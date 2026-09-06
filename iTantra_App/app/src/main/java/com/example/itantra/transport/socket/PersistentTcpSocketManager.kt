package com.example.itantra.transport.socket

import com.example.itantra.transport.TcpStatus
import com.example.itantra.transport.TransportMessage
import com.example.itantra.transport.TransportMessageType
import com.example.itantra.transport.framing.MessageFramer
import com.example.itantra.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * PersistentTcpSocketManager manages Wi-Fi Direct phone-to-phone text transport
 * using persistent TCP sockets and structured binary message framing.
 *
 * Handles both Group Owner (GO) server mode and Client mode with automatic
 * reconnection backoff, thread-safe transmission, and reception acknowledgment tracking.
 */
class PersistentTcpSocketManager(
    val port: Int = DEFAULT_PORT,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)
) {

    companion object {
        const val TAG = "TcpSocketManager"
        const val DEFAULT_PORT = 8988
        const val CONNECT_TIMEOUT_MS = 3000
        const val BACKOFF_BASE_MS = 500L
    }

    // ---------------------------------------------------------------------------------------------
    // Public Observable State & Flows
    // ---------------------------------------------------------------------------------------------

    private val _incomingMessages = MutableSharedFlow<TransportMessage>(
        replay = 16,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val incomingMessages: Flow<TransportMessage> = _incomingMessages.asSharedFlow()

    private val _tcpStatus = MutableStateFlow(TcpStatus.DISCONNECTED)
    val tcpStatus: StateFlow<TcpStatus> = _tcpStatus.asStateFlow()

    private val _bytesSent = MutableStateFlow(0L)
    val bytesSent: StateFlow<Long> = _bytesSent.asStateFlow()

    private val _bytesReceived = MutableStateFlow(0L)
    val bytesReceived: StateFlow<Long> = _bytesReceived.asStateFlow()

    private val _roundTripLatencyMs = MutableStateFlow<Long?>(null)
    val roundTripLatencyMs: StateFlow<Long?> = _roundTripLatencyMs.asStateFlow()

    private val _lastMessage = MutableStateFlow<TransportMessage?>(null)
    val lastMessage: StateFlow<TransportMessage?> = _lastMessage.asStateFlow()

    // ---------------------------------------------------------------------------------------------
    // Internal State
    // ---------------------------------------------------------------------------------------------

    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var dataOutputStream: DataOutputStream? = null

    private var serverJob: Job? = null
    private var readerJob: Job? = null
    private var connectJob: Job? = null

    private val sendMutex = Mutex()
    private val sentTimestamps = ConcurrentHashMap<String, Long>()

    // ---------------------------------------------------------------------------------------------
    // Server Lifecycle (Group Owner)
    // ---------------------------------------------------------------------------------------------

    /**
     * Starts listening for client connections as the Wi-Fi Direct Group Owner.
     * Binds [ServerSocket] with SO_REUSEADDR enabled on [targetPort].
     * Continuously accepts inbound client sockets in a background IO coroutine.
     */
    suspend fun startServer(targetPort: Int = port) = withContext(ioDispatcher) {
        disconnect()
        try {
            val server = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(targetPort))
            }
            serverSocket = server
            _tcpStatus.value = TcpStatus.LISTENING
            Logger.i(TAG, "ServerSocket listening on port $targetPort with reuseAddress=true")

            serverJob = scope.launch(ioDispatcher) {
                try {
                    while (isActive && !server.isClosed) {
                        val socket = try {
                            server.accept()
                        } catch (e: SocketException) {
                            if (!server.isClosed) {
                                Logger.i(TAG, "ServerSocket accept SocketException: ${e.message}")
                                _tcpStatus.value = TcpStatus.DISCONNECTED
                            }
                            break
                        }

                        configureSocket(socket)
                        activeSocket = socket
                        dataOutputStream = DataOutputStream(socket.getOutputStream())
                        _tcpStatus.value = TcpStatus.CONNECTED
                        Logger.i(TAG, "Accepted inbound client connection from ${socket.remoteSocketAddress}")

                        val reader = launch(ioDispatcher) {
                            startSocketIo(socket)
                        }
                        readerJob = reader
                        reader.join()

                        if (!server.isClosed && _tcpStatus.value != TcpStatus.DISCONNECTED) {
                            _tcpStatus.value = TcpStatus.LISTENING
                            Logger.i(TAG, "Client session ended, returning to LISTENING on port $targetPort")
                        }
                    }
                } catch (e: CancellationException) {
                    Logger.i(TAG, "Server accept coroutine cancelled.")
                } catch (e: IOException) {
                    Logger.i(TAG, "ServerSocket accept IOException: ${e.message}")
                    if (_tcpStatus.value != TcpStatus.DISCONNECTED) {
                        _tcpStatus.value = TcpStatus.ERROR
                    }
                }
            }
        } catch (e: Exception) {
            Logger.i(TAG, "Failed to start ServerSocket on port $targetPort: ${e.message}")
            _tcpStatus.value = TcpStatus.ERROR
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Client Lifecycle (Group Client)
    // ---------------------------------------------------------------------------------------------

    /**
     * Connects as a client to the Group Owner host address with exponential backoff retries.
     *
     * @param hostAddress Target IP address (defaults to 192.168.49.1, standard Android Wi-Fi Direct GO IP).
     * @param maxRetries Maximum number of connection retry attempts.
     * @param targetPort Target port on the host (defaults to [port]).
     */
    suspend fun connectToServer(
        hostAddress: String = "192.168.49.1",
        maxRetries: Int = 10,
        targetPort: Int = port
    ) = withContext(ioDispatcher) {
        disconnect()
        connectJob = currentCoroutineContext()[Job]
        _tcpStatus.value = TcpStatus.CONNECTING

        var connected = false
        for (attempt in 1..maxRetries) {
            if (!isActive || _tcpStatus.value == TcpStatus.DISCONNECTED) {
                Logger.i(TAG, "Connection loop aborted (isActive=$isActive, status=${_tcpStatus.value})")
                return@withContext
            }

            if (attempt > 1) {
                _tcpStatus.value = TcpStatus.RECONNECTING
            }

            Logger.i(TAG, "Connecting to $hostAddress:$targetPort (attempt $attempt/$maxRetries)...")

            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(hostAddress, targetPort), CONNECT_TIMEOUT_MS)
                configureSocket(socket)
                activeSocket = socket
                dataOutputStream = DataOutputStream(socket.getOutputStream())
                _tcpStatus.value = TcpStatus.CONNECTED
                Logger.i(TAG, "Successfully connected to $hostAddress:$targetPort on attempt $attempt")

                readerJob = scope.launch(ioDispatcher) {
                    startSocketIo(socket)
                }
                connected = true
                break
            } catch (e: SocketException) {
                Logger.i(TAG, "SocketException connecting to $hostAddress:$targetPort (attempt $attempt): ${e.message}")
            } catch (e: IOException) {
                Logger.i(TAG, "IOException connecting to $hostAddress:$targetPort (attempt $attempt): ${e.message}")
            }

            if (attempt < maxRetries) {
                val backoffMs = attempt * BACKOFF_BASE_MS
                Logger.i(TAG, "Retrying in ${backoffMs}ms...")
                try {
                    delay(backoffMs)
                } catch (e: CancellationException) {
                    Logger.i(TAG, "Connection retry cancelled during backoff delay.")
                    _tcpStatus.value = TcpStatus.DISCONNECTED
                    throw e
                }
            }
        }

        if (!connected && _tcpStatus.value != TcpStatus.DISCONNECTED) {
            Logger.i(TAG, "Failed to connect to $hostAddress:$targetPort after $maxRetries retries.")
            _tcpStatus.value = TcpStatus.ERROR
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Socket I/O Reader Loop
    // ---------------------------------------------------------------------------------------------

    /**
     * Reader loop handling incoming framed messages over [socket].
     * Updates bytes received, auto-acknowledges TEXT and ALERT messages, and calculates latency on ACK.
     */
    private suspend fun startSocketIo(socket: Socket) {
        activeSocket = socket
        try {
            val inputStream = socket.getInputStream()
            val countingIn = CountingInputStream(inputStream)
            val dis = DataInputStream(countingIn)
            val outputStream = socket.getOutputStream()
            dataOutputStream = DataOutputStream(outputStream)

            Logger.i(TAG, "Socket I/O reader started for ${socket.remoteSocketAddress}")

            while (currentCoroutineContext().isActive && !socket.isClosed && socket.isConnected) {
                val beforeCount = countingIn.bytesRead
                val msg = try {
                    MessageFramer.decodeFrame(dis)
                } catch (e: EOFException) {
                    Logger.i(TAG, "Remote peer closed stream (EOFException)")
                    break
                } catch (e: SocketException) {
                    Logger.i(TAG, "Socket closed or reset (SocketException): ${e.message}")
                    break
                } catch (e: IOException) {
                    Logger.i(TAG, "I/O error during frame decoding (IOException): ${e.message}")
                    break
                }

                if (msg == null) {
                    Logger.i(TAG, "decodeFrame returned null (end of stream)")
                    break
                }

                val frameBytes = countingIn.bytesRead - beforeCount
                _bytesReceived.update { it + frameBytes }
                _lastMessage.value = msg

                when (msg.messageType) {
                    TransportMessageType.TEXT, TransportMessageType.ALERT -> {
                        _incomingMessages.emit(msg)
                        val ackMessage = TransportMessage(
                            messageId = msg.messageId,
                            messageType = TransportMessageType.ACK,
                            language = msg.language,
                            priority = msg.priority
                        )
                        try {
                            send(ackMessage)
                        } catch (e: Exception) {
                            Logger.i(TAG, "Failed to send immediate ACK for message ${msg.messageId}: ${e.message}")
                        }
                    }
                    TransportMessageType.ACK -> {
                        val sentTime = sentTimestamps.remove(msg.messageId)
                        if (sentTime != null) {
                            val rtt = (System.currentTimeMillis() - sentTime).coerceAtLeast(0L)
                            _roundTripLatencyMs.value = rtt
                            Logger.i(TAG, "Received ACK for message ${msg.messageId}, RTT: ${rtt}ms")
                        } else {
                            Logger.i(TAG, "Received ACK for message ${msg.messageId} with no recorded timestamp")
                        }
                    }
                    else -> {
                        Logger.i(TAG, "Received message type: ${msg.messageType} (${msg.messageId})")
                    }
                }
            }
        } catch (e: EOFException) {
            Logger.i(TAG, "Socket reader EOF: ${e.message}")
        } catch (e: SocketException) {
            Logger.i(TAG, "Socket reader SocketException: ${e.message}")
        } catch (e: IOException) {
            Logger.i(TAG, "Socket reader IOException: ${e.message}")
        } catch (e: CancellationException) {
            Logger.i(TAG, "Socket reader coroutine cancelled.")
            throw e
        } catch (e: Exception) {
            Logger.i(TAG, "Unexpected error in socket reader: ${e.message}")
        } finally {
            cleanupActiveSocket()
            if (_tcpStatus.value == TcpStatus.CONNECTED) {
                _tcpStatus.value = TcpStatus.DISCONNECTED
            }
            Logger.i(TAG, "Socket reader loop exited.")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Sending Messages
    // ---------------------------------------------------------------------------------------------

    /**
     * Thread-safely encodes and sends a [TransportMessage] across the active TCP connection.
     * Updates bytes sent and tracks timestamps for latency calculation.
     */
    suspend fun send(message: TransportMessage) = sendMutex.withLock {
        withContext(ioDispatcher) {
            val dos = dataOutputStream
            if (dos == null) {
                val err = "Cannot send message ${message.messageId}: No active socket connection"
                Logger.i(TAG, err)
                throw IOException(err)
            }

            try {
                val frameBytes = MessageFramer.encodeFrame(message, dos)
                _bytesSent.update { it + frameBytes }
                if (message.messageType == TransportMessageType.TEXT || message.messageType == TransportMessageType.ALERT) {
                    sentTimestamps[message.messageId] = System.currentTimeMillis()
                }
                _lastMessage.value = message
                Logger.i(TAG, "Sent message ${message.messageId} (${message.messageType}, $frameBytes bytes)")
            } catch (e: SocketException) {
                Logger.i(TAG, "SocketException sending message ${message.messageId}: ${e.message}")
                _tcpStatus.value = TcpStatus.DISCONNECTED
                throw e
            } catch (e: IOException) {
                Logger.i(TAG, "IOException sending message ${message.messageId}: ${e.message}")
                _tcpStatus.value = TcpStatus.ERROR
                throw e
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Disconnect & Cleanup
    // ---------------------------------------------------------------------------------------------

    /**
     * Closes active client Socket and ServerSocket, cancels reader and accept jobs,
     * and sets status to [TcpStatus.DISCONNECTED].
     */
    suspend fun disconnect() = withContext(ioDispatcher) {
        Logger.i(TAG, "Disconnecting TCP socket manager...")
        _tcpStatus.value = TcpStatus.DISCONNECTED

        readerJob?.cancel()
        readerJob = null

        serverJob?.cancel()
        serverJob = null

        connectJob?.cancel()
        connectJob = null

        cleanupActiveSocket()

        try {
            serverSocket?.let { sSock ->
                if (!sSock.isClosed) {
                    sSock.close()
                }
            }
        } catch (e: Exception) {
            Logger.i(TAG, "Error closing ServerSocket: ${e.message}")
        } finally {
            serverSocket = null
        }

        sentTimestamps.clear()
        Logger.i(TAG, "PersistentTcpSocketManager disconnected cleanly.")
    }

    private fun cleanupActiveSocket() {
        dataOutputStream = null
        try {
            activeSocket?.let { sock ->
                if (!sock.isClosed) {
                    runCatching { if (!sock.isInputShutdown) sock.shutdownInput() }
                    runCatching { if (!sock.isOutputShutdown) sock.shutdownOutput() }
                    sock.close()
                }
            }
        } catch (e: Exception) {
            Logger.i(TAG, "Error closing active socket: ${e.message}")
        } finally {
            activeSocket = null
        }
    }

    private fun configureSocket(socket: Socket) {
        socket.tcpNoDelay = true
        socket.keepAlive = true
    }

    fun resetStats() {
        _bytesSent.value = 0L
        _bytesReceived.value = 0L
        _roundTripLatencyMs.value = null
        _lastMessage.value = null
    }

    // ---------------------------------------------------------------------------------------------
    // CountingInputStream Helper
    // ---------------------------------------------------------------------------------------------

    private class CountingInputStream(wrapped: InputStream) : FilterInputStream(wrapped) {
        private val _bytesRead = AtomicLong(0L)
        val bytesRead: Long get() = _bytesRead.get()

        override fun read(): Int {
            val b = super.read()
            if (b != -1) _bytesRead.incrementAndGet()
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val count = super.read(b, off, len)
            if (count > 0) _bytesRead.addAndGet(count.toLong())
            return count
        }

        override fun skip(n: Long): Long {
            val skipped = super.skip(n)
            if (skipped > 0) _bytesRead.addAndGet(skipped)
            return skipped
        }
    }
}
