package com.itantra.voice.transport.socket

import com.itantra.voice.transport.TransportMessage
import com.itantra.voice.transport.framing.MessageFramer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
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

    // replay must be 0: a replay buffer causes any new or re-subscribing collector to
    // immediately receive the previous message, which made the receiving phone speak
    // the last transmission again on every reconnect and on every ViewModel rebind.
    private val _incomingMessages = MutableSharedFlow<TransportMessage>(
        replay = 0,
        extraBufferCapacity = 64
    )
    /**
     * Exposed as [SharedFlow] rather than [Flow] so callers can observe subscription
     * (there is no replay buffer, so a message emitted before a collector attaches is
     * gone). [com.itantra.voice.transport.TransportEngine] still narrows it to [Flow].
     */
    val incomingMessages: SharedFlow<TransportMessage> = _incomingMessages.asSharedFlow()


    private val writeMutex = Mutex()
    private val isConnectedFlag = AtomicBoolean(false)
    val isConnected: Boolean get() = isConnectedFlag.get()

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var activeSocket: Socket? = null
    @Volatile
    private var readerJob: Job? = null

    @Volatile
    private var acceptJob: Job? = null

    @Volatile
    private var connectJob: Job? = null

    /**
     * Starts a ServerSocket listening on the specified port (typically for Group Owner role)
     * and awaits an incoming connection from the client peer.
     */
    fun startServer(port: Int = DEFAULT_PORT) {
        isConnectedFlag.set(false)
        closeAll()

        acceptJob = scope.launch(ioDispatcher) {
            try {
                // Bind explicitly so SO_REUSEADDR is applied *before* binding; setting it
                // on an already-bound ServerSocket(port) is a no-op and left the port
                // unusable during TIME_WAIT, breaking every reconnect after the first.
                val server = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port), 1)
                }
                serverSocket = server
                log.info("Server listening on port $port, awaiting peer connection...")

                // Loop: the previous single accept() left the group owner deaf after the
                // first peer disconnected, so reconnecting required restarting the app.
                while (isActive && !server.isClosed) {
                    val client = server.accept()
                    log.info("Client connected from ${client.inetAddress.hostAddress}")
                    setupActiveSocket(client)
                    // Serve one peer at a time; wait until it drops before accepting again.
                    while (isActive && isConnectedFlag.get()) {
                        delay(ACCEPT_POLL_INTERVAL_MS)
                    }
                }
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
        isConnectedFlag.set(false)
        closeAll()

        connectJob = scope.launch(ioDispatcher) {
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
        isConnectedFlag.set(false)
        closeAll()
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
     * Fully tears down the link: active peer, listening socket and all coroutines.
     */
    fun disconnect() {
        val wasConnected = isConnectedFlag.getAndSet(false)
        closeAll()
        if (wasConnected) onDisconnected("Disconnected")
    }

    /**
     * Handles an unexpected drop of the *peer* while leaving a listening server socket
     * intact, so the group owner can accept the peer again when it comes back.
     */
    private fun handleDisconnect(reason: String) {
        if (isConnectedFlag.getAndSet(false)) {
            closePeerSocket()
            onDisconnected(reason)
        }
    }

    /**
     * Closes only the peer connection. Deliberately does not touch [acceptJob] or
     * [serverSocket]: cancelling the accept job from here would cancel the very
     * coroutine that is waiting to re-accept the peer.
     */
    private fun closePeerSocket() {
        readerJob?.cancel()
        readerJob = null

        try {
            activeSocket?.close()
        } catch (ignored: Exception) {}
        activeSocket = null
    }

    /** Closes the peer connection, the listening socket, and every coroutine. */
    private fun closeAll() {
        closePeerSocket()

        acceptJob?.cancel()
        acceptJob = null
        connectJob?.cancel()
        connectJob = null

        try {
            serverSocket?.close()
        } catch (ignored: Exception) {}
        serverSocket = null
    }

    companion object {
        const val DEFAULT_PORT = 8888

        /** How often the accept loop checks whether the served peer has dropped. */
        const val ACCEPT_POLL_INTERVAL_MS = 200L
    }
}
