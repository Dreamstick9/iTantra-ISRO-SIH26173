package com.itantra.voice.transport.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
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
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

/**
 * Manages persistent Bluetooth Classic RFCOMM connections using the Serial Port Profile (SPP).
 * Provides bidirectional framed message streaming with thread-safe writes, background server
 * listening, and clean lifecycle management.
 */
class RfcommSocketManager(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onConnected: () -> Unit = {},
    private val onDisconnected: (reason: String?) -> Unit = {}
) {

    private val log = Logger.getLogger("RfcommSocketManager")

    private val _incomingMessages = MutableSharedFlow<TransportMessage>(
        replay = 1,
        extraBufferCapacity = 64
    )
    val incomingMessages: Flow<TransportMessage> = _incomingMessages.asSharedFlow()

    private val writeMutex = Mutex()
    private val isConnectedFlag = AtomicBoolean(false)
    val isConnected: Boolean get() = isConnectedFlag.get()

    private var serverSocket: BluetoothServerSocket? = null
    private var activeSocket: BluetoothSocket? = null
    private var readerJob: Job? = null
    private var acceptJob: Job? = null

    /**
     * Starts listening for incoming RFCOMM connections from peer devices.
     */
    @SuppressLint("MissingPermission")
    fun startServer(bluetoothAdapter: BluetoothAdapter?) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            log.warning("Cannot start RFCOMM server: Bluetooth is null or disabled")
            return
        }

        acceptJob?.cancel()
        acceptJob = scope.launch(ioDispatcher) {
            try {
                log.info("Starting RFCOMM server listener (UUID: $SPP_UUID)...")
                val server = try {
                    bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, SPP_UUID)
                } catch (e: Exception) {
                    bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SPP_UUID)
                }
                serverSocket = server

                log.info("RFCOMM server listening, awaiting peer connection...")
                val client = server.accept()
                log.info("RFCOMM peer connected: ${client.remoteDevice?.name} (${client.remoteDevice?.address})")
                setupActiveSocket(client)
            } catch (e: Exception) {
                if (isActive) {
                    log.warning("RFCOMM accept error or cancelled: ${e.message}")
                }
            }
        }
    }

    /**
     * Connects to a remote Bluetooth device via RFCOMM SPP socket.
     */
    @SuppressLint("MissingPermission")
    fun connectClient(device: BluetoothDevice) {
        disconnect()

        scope.launch(ioDispatcher) {
            try {
                log.info("Connecting RFCOMM to ${device.name} (${device.address})...")
                val socket = try {
                    device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                } catch (e: Exception) {
                    device.createRfcommSocketToServiceRecord(SPP_UUID)
                }

                // Blocking connect
                socket.connect()
                log.info("RFCOMM successfully connected to ${device.name}")
                setupActiveSocket(socket)
            } catch (e: Exception) {
                log.warning("Failed to connect RFCOMM to ${device.address}: ${e.message}")
                onDisconnected("Bluetooth connect failed: ${e.message}")
            }
        }
    }

    /**
     * Adopts an already-connected socket (useful for testing or external socket providers).
     */
    fun adoptConnectedSocket(socket: BluetoothSocket) {
        disconnect()
        setupActiveSocket(socket)
    }

    private fun setupActiveSocket(socket: BluetoothSocket) {
        activeSocket = socket
        isConnectedFlag.set(true)
        onConnected()

        readerJob = scope.launch(ioDispatcher) {
            val inStream = try {
                socket.inputStream
            } catch (e: Exception) {
                log.warning("Error getting RFCOMM input stream: ${e.message}")
                handleDisconnect("Failed to get input stream")
                return@launch
            }

            try {
                while (isActive && isConnectedFlag.get()) {
                    val message = MessageFramer.readFrame(inStream)
                    _incomingMessages.emit(message)
                }
            } catch (e: EOFException) {
                log.info("RFCOMM peer closed connection (EOF).")
                handleDisconnect("Peer disconnected")
            } catch (e: IOException) {
                if (isActive && isConnectedFlag.get()) {
                    log.info("RFCOMM socket closed: ${e.message}")
                    handleDisconnect("Connection closed")
                }
            } catch (e: Exception) {
                if (isActive && isConnectedFlag.get()) {
                    log.warning("RFCOMM read error: ${e.message}")
                    handleDisconnect("Read error: ${e.message}")
                }
            }
        }
    }

    /**
     * Transmits a framed TransportMessage across the active RFCOMM link.
     */
    suspend fun send(message: TransportMessage): Result<Unit> = withContext(ioDispatcher) {
        val socket = activeSocket
        if (socket == null || !isConnectedFlag.get()) {
            return@withContext Result.failure(IOException("Bluetooth RFCOMM connection is not active"))
        }

        try {
            writeMutex.withLock {
                val outStream = socket.outputStream
                MessageFramer.writeFrame(message, outStream)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            log.warning("Failed to send message over RFCOMM: ${e.message}")
            handleDisconnect("Send failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Closes the active socket, server listener, and stops coroutines.
     */
    fun disconnect() {
        if (!isConnectedFlag.getAndSet(false)) {
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
        const val SERVICE_NAME = "iTantraVoiceP2P"
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
