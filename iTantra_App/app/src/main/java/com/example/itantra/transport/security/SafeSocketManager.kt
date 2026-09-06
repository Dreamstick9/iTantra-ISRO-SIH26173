package com.example.itantra.transport.security

import com.example.itantra.transport.TransportPacket
import com.example.itantra.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Socket lifecycle and network security manager for Wi-Fi Direct TCP communications.
 *
 * Enforces:
 * 1. SO_REUSEADDR on ServerSockets to prevent EADDRINUSE errors upon service restarts.
 * 2. SO_TIMEOUT and TCP_NODELAY on Sockets to eliminate hang conditions and optimize PTT latency.
 * 3. Graceful shutdown sequences (shutdownInput/Output -> close) to avoid TCP RST and FD leaks.
 * 4. Bounded frame consumption via [SafeFrameProtocol] to protect against OOM / DoS crashes.
 */
class SafeSocketManager(
    private val port: Int = DEFAULT_PORT,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        const val TAG = "SafeSocketManager"
        const val DEFAULT_PORT = 8988 // Standard port for Wi-Fi Direct P2P sockets
        const val CONNECT_TIMEOUT_MS = 8000
        const val SOCKET_READ_TIMEOUT_MS = 15000 // 15 seconds read timeout
    }

    private var serverSocket: ServerSocket? = null
    private var activeClientSocket: Socket? = null
    private val isRunning = AtomicBoolean(false)
    private var serverJob: Job? = null

    /**
     * Safely closes any [Closeable] or Socket suppressing non-critical I/O errors.
     */
    fun Closeable?.closeQuietly() {
        if (this == null) return
        try {
            if (this is Socket) {
                if (!this.isClosed) {
                    runCatching { if (!this.isInputShutdown) this.shutdownInput() }
                    runCatching { if (!this.isOutputShutdown) this.shutdownOutput() }
                }
            }
            this.close()
        } catch (e: Exception) {
            Logger.w(TAG, "Exception during quiet close: ${e.message}")
        }
    }

    /**
     * Starts a secure ServerSocket listener on the specified local port.
     * Guaranteed to release OS file descriptors upon cancellation or error.
     */
    fun startServer(
        scope: CoroutineScope,
        onPacketReceived: suspend (TransportPacket) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (isRunning.getAndSet(true)) {
            Logger.w(TAG, "Server already running on port $port")
            return
        }

        serverJob = scope.launch(ioDispatcher) {
            try {
                // Critical: instantiate unbound ServerSocket first, enable SO_REUSEADDR, then bind!
                val server = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
                serverSocket = server
                Logger.i(TAG, "Secure ServerSocket bound on port $port (reuseAddress=true). Awaiting connection...")

                while (isActive && isRunning.get()) {
                    val socket = try {
                        server.accept()
                    } catch (e: SocketException) {
                        // Thrown when serverSocket.close() is invoked during shutdown
                        if (!isRunning.get()) break
                        throw e
                    }

                    configureSocket(socket)
                    activeClientSocket = socket
                    Logger.i(TAG, "Accepted inbound connection from ${socket.remoteSocketAddress}")

                    try {
                        socket.getInputStream().use { inputStream ->
                            while (isActive && !socket.isClosed && socket.isConnected) {
                                try {
                                    val packet = SafeFrameProtocol.readFrame(inputStream)
                                    onPacketReceived(packet)
                                } catch (e: SocketTimeoutException) {
                                    // Periodic read timeout prevents hung reader thread; continue if still active
                                    continue
                                } catch (e: EOFException) {
                                    Logger.i(TAG, "Remote peer closed TCP connection normally (EOF).")
                                    break
                                } catch (e: SafeFrameProtocol.SecurityBoundsException) {
                                    Logger.e(TAG, "SECURITY ALERT: Closing socket due to bounds violation: ${e.message}")
                                    break
                                } catch (e: SafeFrameProtocol.ProtocolMismatchException) {
                                    Logger.e(TAG, "Protocol mismatch error: ${e.message}")
                                    break
                                } catch (e: SafeFrameProtocol.ChecksumMismatchException) {
                                    Logger.w(TAG, "Checksum failed for frame: ${e.message}")
                                    continue
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (isActive && isRunning.get()) {
                            Logger.e(TAG, "Error in socket stream: ${e.message}", e)
                        }
                    } finally {
                        socket.closeQuietly()
                        activeClientSocket = null
                        Logger.i(TAG, "Client socket cleaned up.")
                    }
                }
            } catch (e: CancellationException) {
                Logger.i(TAG, "Server coroutine cancelled.")
            } catch (e: Exception) {
                Logger.e(TAG, "Fatal ServerSocket error: ${e.message}", e)
                onError(e)
            } finally {
                stopServer()
            }
        }
    }

    /**
     * Connects as client to a remote Wi-Fi Direct host (e.g. 192.168.49.1) with timeout bounds.
     */
    suspend fun connectClient(host: String, port: Int = DEFAULT_PORT): Result<Socket> = withContext(ioDispatcher) {
        return@withContext try {
            val socket = Socket()
            configureSocket(socket)
            Logger.i(TAG, "Connecting to Wi-Fi Direct peer at $host:$port (timeout=${CONNECT_TIMEOUT_MS}ms)...")
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            activeClientSocket = socket
            Logger.i(TAG, "Successfully connected to $host:$port")
            Result.success(socket)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to connect to $host:$port - ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Sends a packet through the active socket using [SafeFrameProtocol].
     */
    suspend fun sendPacket(packet: TransportPacket): Result<Int> = withContext(ioDispatcher) {
        val socket = activeClientSocket ?: return@withContext Result.failure(IOException("No active socket connection."))
        return@withContext try {
            val out = socket.getOutputStream()
            SafeFrameProtocol.writeFrame(out, packet)
            Result.success(packet.payload.size)
        } catch (e: Exception) {
            Logger.e(TAG, "Send packet error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Applies standard low-latency and timeout configuration to any active socket.
     */
    private fun configureSocket(socket: Socket) {
        socket.tcpNoDelay = true              // Low latency for tactical voice audio / PTT
        socket.soTimeout = SOCKET_READ_TIMEOUT_MS // Prevents hung threads
        socket.keepAlive = true               // Detects dead connection
    }

    /**
     * Closes all active sockets and stops background server thread without leaking file descriptors.
     */
    fun stopServer() {
        isRunning.set(false)
        activeClientSocket.closeQuietly()
        activeClientSocket = null
        serverSocket.closeQuietly()
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        Logger.i(TAG, "SafeSocketManager stopped and all sockets closed.")
    }
}
