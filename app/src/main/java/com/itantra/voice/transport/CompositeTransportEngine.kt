package com.itantra.voice.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.util.logging.Logger

/**
 * Composite TransportEngine that encapsulates both Wi-Fi Direct and Bluetooth Classic transports.
 * Allows dynamic switching of the active transport medium while merging incoming message streams
 * so the application seamlessly receives packets from whichever medium is connected.
 */
class CompositeTransportEngine(
    val wifiEngine: TransportEngine,
    val bluetoothEngine: TransportEngine,
    private val scope: CoroutineScope
) : TransportEngine {

    private val log = Logger.getLogger("CompositeTransportEngine")

    private val _selectedTransport = MutableStateFlow(TransportType.WIFI_DIRECT)
    val selectedTransport: StateFlow<TransportType> = _selectedTransport.asStateFlow()

    private val activeEngine: TransportEngine
        get() = when (_selectedTransport.value) {
            TransportType.WIFI_DIRECT -> wifiEngine
            TransportType.BLUETOOTH -> bluetoothEngine
        }

    override val transportType: TransportType
        get() = _selectedTransport.value

    private val _connectionState = MutableStateFlow(wifiEngine.connectionState.value)
    override val connectionState: StateFlow<TransportConnectionState> = _connectionState.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    override val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    private val _connectedPeer = MutableStateFlow<DiscoveredPeer?>(null)
    override val connectedPeer: StateFlow<DiscoveredPeer?> = _connectedPeer.asStateFlow()

    private var syncJob: Job? = null

    init {
        updateActiveSync()
    }

    private fun updateActiveSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            launch {
                activeEngine.connectionState.collect { _connectionState.value = it }
            }
            launch {
                activeEngine.discoveredPeers.collect { _discoveredPeers.value = it }
            }
            launch {
                activeEngine.connectedPeer.collect { _connectedPeer.value = it }
            }
        }
    }

    // Merged incoming stream: receives messages from either transport medium!
    override val incomingMessages: Flow<TransportMessage> =
        merge(wifiEngine.incomingMessages, bluetoothEngine.incomingMessages)

    /**
     * Switches the active transport medium (Wi-Fi Direct <-> Bluetooth).
     * Automatically halts discovery on the old engine.
     */
    fun selectTransport(type: TransportType) {
        if (_selectedTransport.value == type) return
        log.info("Switching active transport from ${_selectedTransport.value} to $type")

        activeEngine.stopDiscovery()
        _selectedTransport.value = type

        _connectionState.value = activeEngine.connectionState.value
        _discoveredPeers.value = activeEngine.discoveredPeers.value
        _connectedPeer.value = activeEngine.connectedPeer.value
        updateActiveSync()
    }

    override fun startDiscovery() {
        activeEngine.startDiscovery()
    }

    override fun stopDiscovery() {
        activeEngine.stopDiscovery()
    }

    override fun connect(peer: DiscoveredPeer) {
        when (peer.transportType) {
            TransportType.WIFI_DIRECT -> {
                selectTransport(TransportType.WIFI_DIRECT)
                wifiEngine.connect(peer)
            }
            TransportType.BLUETOOTH -> {
                selectTransport(TransportType.BLUETOOTH)
                bluetoothEngine.connect(peer)
            }
        }
        _connectionState.value = activeEngine.connectionState.value
        _connectedPeer.value = activeEngine.connectedPeer.value
    }

    override fun disconnect() {
        wifiEngine.disconnect()
        bluetoothEngine.disconnect()
        _connectionState.value = TransportConnectionState.DISCONNECTED
        _connectedPeer.value = null
    }

    override suspend fun send(message: TransportMessage): Result<Unit> {
        // Send using the transport that is currently connected
        if (wifiEngine.connectionState.value == TransportConnectionState.CONNECTED) {
            return wifiEngine.send(message)
        }
        if (bluetoothEngine.connectionState.value == TransportConnectionState.CONNECTED) {
            return bluetoothEngine.send(message)
        }

        // Fallback to active engine
        return activeEngine.send(message)
    }

    override fun release() {
        syncJob?.cancel()
        syncJob = null
        wifiEngine.release()
        bluetoothEngine.release()
    }
}
