package com.example.itantra.transport

import com.example.itantra.data.ConnectionStatus
import com.example.itantra.data.ReceivedMessage
import com.example.itantra.data.TransmissionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface TransportEngine {
    suspend fun startDiscovery()
    suspend fun connect(peer: Peer)
    suspend fun send(message: TransportMessage)
    fun incomingMessages(): Flow<TransportMessage>
    suspend fun disconnect()

    val peers: StateFlow<List<Peer>>
    val diagnostics: StateFlow<TransportDiagnostics>

    // Backward-compatibility bridge methods for existing UI / ViewModels / Tests
    fun initialize(): Result<Unit> = Result.success(Unit)
    fun release() {}
    fun setTransmissionMode(mode: TransmissionMode) {}
    suspend fun connect(target: String = "PEER_AUTO"): Result<Unit> {
        startDiscovery()
        return Result.success(Unit)
    }
    suspend fun sendMessage(message: ReceivedMessage): Result<TransportAck> {
        val tMsg = TransportMessage.fromReceivedMessage(message)
        send(tMsg)
        return Result.success(TransportAck(packetId = message.id, wireBytes = tMsg.compressedPayload.size))
    }
    val connectionStatus: StateFlow<ConnectionStatus>
        get() = MutableStateFlow(ConnectionStatus.DISCONNECTED)
}
