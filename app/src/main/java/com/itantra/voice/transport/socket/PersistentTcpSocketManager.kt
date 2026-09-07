package com.itantra.voice.transport.socket

import com.itantra.voice.transport.TransportMessage
import com.itantra.voice.transport.framing.MessageFramer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

/**
 * Manages persistent TCP socket connections over Wi-Fi Direct P2P links.
 * Provides bidirectional, framed message streaming with thread-safe writes
 * and graceful disconnection handling.
 */
class PersistentTcpSocketManager(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onConnected: () -> Unit = {},
    private val onDisconnected: (reason: String?) -> Unit = {}
) {

    private val log = Logger.getLogger("PersistentTcpSocketManager")

    private val _incomingMessages = MutableSharedFlow<TransportMessage>(
        replay = 1,
        extraBufferCapacity = 64
    )
    val incomingMessages: Flow<TransportMessage> = _incomingMessages.asSharedFlow()


    private val writeMutex = Mutex()
    private val isConnectedFlag = AtomicBoolean(false)
    val isConnected: Boolean get() = isConnectedFlag.get()

    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var readerJob: Job? = null
    private var acceptJob: Job? = null

    /**
     * Starts a ServerSocket listening on the specified port (typically for Group Owner role)
     * and awaits an incoming connection from the client peer.
     */
    fun startServer(port: Int = DEFAULT_PORT) {
        disconnect()

        acceptJob = scope.launch(ioDispatcher) {
            try {
                val server = ServerSocket(port).apply {
                    reuseAddress = true
                }
                serverSocket = server
                log.info("Server listening on port $port, awaiting peer connection...")

                val client = server.accept()
                log.info("Client connected from ${client.inetAddress.hostAddress}")
                setupActiveSocket(client)
            } catch (e: Exception) {
                if (isActive) {
                    log.warning("ServerSocket accept error: ${e.message}")
                    onDisconnected("Server accept failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Connects as a TCP client to the specified host IP and port (typically Group Owner IP).
     */
    fun connectClient(host: String, port: Int = DEFAULT_PORT, timeoutMs: Int = 10000) {
        disconnect()

        scope.launch(ioDispatcher) {
            try {
                log.info("Connecting to $host:$port (timeout: ${timeoutMs}ms)...")
                val socket = Socket().apply {
                    connect(InetSocketAddress(host, port), timeoutMs)
                }
                log.info("Successfully connected to $host:$port")
                setupActiveSocket(socket)
            } catch (e: Exception) {
                log.warning("Failed to connect to $host:$port: ${e.message}")
                onDisconnected("Connection failed: ${e.message}")
            }
        }
    }

    /**
     * Adopts an already-connected socket (useful for tests or existing links).
     */
    fun adoptConnectedSocket(socket: Socket) {
        disconnect()
        setupActiveSocket(socket)
    }

    private fun setupActiveSocket(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.soTimeout = 0 // Infinite read timeout for persistent listener
        } catch (ignored: Exception) {}

        activeSocket = socket
        isConnectedFlag.set(true)
        onConnected()

        readerJob = scope.launch(ioDispatcher) {
            val inStream = try {
                socket.getInputStream()
            } catch (e: Exception) {
                log.warning("Error getting socket input stream: ${e.message}")
                handleDisconnect("Failed to get input stream")
                return@launch
            }

            try {
                while (isActive && isConnectedFlag.get()) {
                    val message = MessageFramer.readFrame(inStream)
                    _incomingMessages.emit(message)
                }
            } catch (e: EOFException) {
                log.info("Peer closed the connection (EOF).")
                handleDisconnect("Peer disconnected")
            } catch (e: SocketException) {
                if (isActive && isConnectedFlag.get()) {
                    log.info("Socket closed or connection reset: ${e.message}")
                    handleDisconnect("Connection reset")
                }
            } catch (e: Exception) {
                if (isActive && isConnectedFlag.get()) {
                    log.warning("Socket read error: ${e.message}")
                    handleDisconnect("Read error: ${e.message}")
                }
            }
        }
    }

    /**
     * Serializes and transmits a TransportMessage across the active TCP link.
     */
    suspend fun send(message: TransportMessage): Result<Unit> = withContext(ioDispatcher) {
        val socket = activeSocket
        if (socket == null || !isConnectedFlag.get() || socket.isClosed) {
            return@withContext Result.failure(IOException("TCP connection is not active"))
        }

        try {
            writeMutex.withLock {
                val outStream = socket.getOutputStream()
                MessageFramer.writeFrame(message, outStream)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            log.warning("Failed to send message: ${e.message}")
            handleDisconnect("Send failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Closes the active socket, server socket, and cancels coroutines.
     */
    fun disconnect() {
        if (!isConnectedFlag.getAndSet(false)) {
            // Already disconnected, but still clean up lingering sockets/jobs
            closeSockets()
            return
        }

        closeSockets()
        onDisconnected("Disconnected")
    }

    private fun handleDisconnect(reason: String) {
        if (isConnectedFlag.getAndSet(false)) {
            closeSockets()
            onDisconnected(reason)
        }
    }

    private fun closeSockets() {
        readerJob?.cancel()
        readerJob = null
        acceptJob?.cancel()
        acceptJob = null

        try {
            activeSocket?.close()
        } catch (ignored: Exception) {}
        activeSocket = null

        try {
            serverSocket?.close()
        } catch (ignored: Exception) {}
        serverSocket = null
    }

    companion object {
        const val DEFAULT_PORT = 8888
    }
}
