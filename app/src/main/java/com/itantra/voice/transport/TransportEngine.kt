package com.itantra.voice.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Connection states for peer-to-peer transport sessions.
 */
enum class TransportConnectionState {
    DISCONNECTED,
    DISCOVERING,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

/**
 * Underlying physical transport medium.
 */
enum class TransportType {
    WIFI_DIRECT,
    BLUETOOTH
}

/**
 * Representation of a discovered nearby peer device.
 */
data class DiscoveredPeer(
    val id: String,
    val name: String,
    val deviceAddress: String,
    val isGroupOwner: Boolean = false,
    val transportType: TransportType = TransportType.WIFI_DIRECT
)

/**
 * Top-level abstraction decoupling application logic (UI, ViewModel, audio pipeline)
 * from transport-specific implementations (Wi-Fi Direct TCP, Bluetooth RFCOMM).
 */
interface TransportEngine {
    val transportType: TransportType
    val connectionState: StateFlow<TransportConnectionState>
    val discoveredPeers: StateFlow<List<DiscoveredPeer>>
    val incomingMessages: Flow<TransportMessage>
    val connectedPeer: StateFlow<DiscoveredPeer?>

    fun startDiscovery()
    fun stopDiscovery()
    fun connect(peer: DiscoveredPeer)
    fun disconnect()
    suspend fun send(message: TransportMessage): Result<Unit>
    fun release()
}
