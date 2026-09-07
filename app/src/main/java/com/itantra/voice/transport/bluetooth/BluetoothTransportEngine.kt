package com.itantra.voice.transport.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.itantra.voice.transport.DiscoveredPeer
import com.itantra.voice.transport.TransportConnectionState
import com.itantra.voice.transport.TransportEngine
import com.itantra.voice.transport.TransportMessage
import com.itantra.voice.transport.TransportType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.logging.Logger

/**
 * Bluetooth Classic RFCOMM implementation of TransportEngine.
 * Handles paired device resolution, active Bluetooth inquiry discovery,
 * bidirectional SPP streaming, and background connection acceptance.
 */
class BluetoothTransportEngine(
    private val context: Context? = null,
    private val bluetoothAdapter: BluetoothAdapter? = try {
        BluetoothAdapter.getDefaultAdapter()
    } catch (e: Exception) {
        null
    },
    private val scope: CoroutineScope,
    socketManager: RfcommSocketManager? = null
) : TransportEngine {

    private val log = Logger.getLogger("BluetoothTransportEngine")

    override val transportType: TransportType = TransportType.BLUETOOTH

    private val _connectionState = MutableStateFlow(TransportConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<TransportConnectionState> = _connectionState.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    override val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    private val _connectedPeer = MutableStateFlow<DiscoveredPeer?>(null)
    override val connectedPeer: StateFlow<DiscoveredPeer?> = _connectedPeer.asStateFlow()

    val socketManager: RfcommSocketManager = socketManager ?: RfcommSocketManager(
        scope = scope,
        onConnected = {
            _connectionState.value = TransportConnectionState.CONNECTED
        },
        onDisconnected = { reason ->
            log.info("Bluetooth disconnected: $reason")
            _connectedPeer.value = null
            _connectionState.value = TransportConnectionState.DISCONNECTED
        }
    )

    override val incomingMessages: Flow<TransportMessage> = this.socketManager.incomingMessages

    private var isReceiverRegistered = false
    private val peerMap = mutableMapOf<String, DiscoveredPeer>()

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    device?.let { handleDiscoveredDevice(it) }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    log.info("Bluetooth discovery inquiry finished.")
                    if (_connectionState.value == TransportConnectionState.DISCOVERING) {
                        // Stay in DISCOVERING if user hasn't explicitly stopped, or transition
                    }
                }
            }
        }
    }

    init {
        // Start background RFCOMM server so this device is ready to accept connections
        try {
            this.socketManager.startServer(bluetoothAdapter)
        } catch (e: Exception) {
            log.warning("Could not auto-start RFCOMM server in init: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    override fun startDiscovery() {
        if (_connectionState.value == TransportConnectionState.CONNECTED) {
            log.info("Already connected, skipping discovery.")
            return
        }

        _connectionState.value = TransportConnectionState.DISCOVERING
        peerMap.clear()

        // 1. Immediately add bonded (paired) devices
        try {
            bluetoothAdapter?.bondedDevices?.forEach { device ->
                handleDiscoveredDevice(device)
            }
        } catch (e: Exception) {
            log.warning("Error fetching bonded devices: ${e.message}")
        }

        // 2. Start active Bluetooth discovery for unbonded devices
        if (context != null && !isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            try {
                context.registerReceiver(discoveryReceiver, filter)
                isReceiverRegistered = true
            } catch (e: Exception) {
                log.warning("Failed to register Bluetooth receiver: ${e.message}")
            }
        }

        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
            bluetoothAdapter?.startDiscovery()
        } catch (e: Exception) {
            log.warning("Failed to start Bluetooth discovery: ${e.message}")
        }

        // Ensure server is listening
        socketManager.startServer(bluetoothAdapter)
    }

    @SuppressLint("MissingPermission")
    override fun stopDiscovery() {
        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
        } catch (e: Exception) {
            log.warning("Error cancelling discovery: ${e.message}")
        }

        unregisterReceiverSafely()

        if (_connectionState.value == TransportConnectionState.DISCOVERING) {
            _connectionState.value = TransportConnectionState.DISCONNECTED
        }
    }

    @SuppressLint("MissingPermission")
    override fun connect(peer: DiscoveredPeer) {
        log.info("Connecting to Bluetooth peer: ${peer.name} (${peer.deviceAddress})")
        _connectionState.value = TransportConnectionState.CONNECTING
        _connectedPeer.value = peer

        // Cancel discovery to free radio resources for reliable RFCOMM connection
        stopDiscovery()

        if (bluetoothAdapter == null) {
            _connectionState.value = TransportConnectionState.ERROR
            return
        }

        try {
            val device = bluetoothAdapter.getRemoteDevice(peer.deviceAddress)
            socketManager.connectClient(device)
        } catch (e: Exception) {
            log.warning("Failed to resolve remote device ${peer.deviceAddress}: ${e.message}")
            _connectionState.value = TransportConnectionState.ERROR
        }
    }

    override fun disconnect() {
        log.info("Disconnecting Bluetooth transport...")
        _connectionState.value = TransportConnectionState.DISCONNECTING
        socketManager.disconnect()
        _connectedPeer.value = null
        _connectionState.value = TransportConnectionState.DISCONNECTED
    }

    override suspend fun send(message: TransportMessage): Result<Unit> {
        return socketManager.send(message)
    }

    override fun release() {
        stopDiscovery()
        disconnect()
    }

    @SuppressLint("MissingPermission")
    private fun handleDiscoveredDevice(device: BluetoothDevice) {
        val name = try {
            device.name?.takeIf { it.isNotBlank() } ?: "Device ${device.address.takeLast(5)}"
        } catch (e: Exception) {
            "Device ${device.address.takeLast(5)}"
        }

        val peer = DiscoveredPeer(
            id = device.address,
            name = name,
            deviceAddress = device.address,
            isGroupOwner = false,
            transportType = TransportType.BLUETOOTH
        )

        peerMap[device.address] = peer
        _discoveredPeers.value = peerMap.values.toList()
    }

    private fun unregisterReceiverSafely() {
        if (isReceiverRegistered && context != null) {
            try {
                context.unregisterReceiver(discoveryReceiver)
            } catch (ignored: Exception) {}
            isReceiverRegistered = false
        }
    }
}
