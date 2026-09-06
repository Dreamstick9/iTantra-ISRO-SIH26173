package com.example.itantra.transport

import com.example.itantra.data.ConnectionStatus
import com.example.itantra.data.ReceivedMessage
import com.example.itantra.data.TransmissionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface TransportEngine {
    val connectionStatus: StateFlow<ConnectionStatus>
    val incomingPackets: Flow<TransportPacket>
    val incomingMessages: Flow<ReceivedMessage>

    fun initialize(): Result<Unit>
    suspend fun connect(target: String = "PEER_AUTO"): Result<Unit>
    suspend fun disconnect()
    suspend fun sendPacket(packet: TransportPacket): Result<TransportAck>
    suspend fun sendMessage(message: ReceivedMessage): Result<TransportAck>
    fun setTransmissionMode(mode: TransmissionMode)
    fun release()
}
