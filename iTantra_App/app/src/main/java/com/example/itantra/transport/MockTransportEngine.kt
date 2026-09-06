package com.example.itantra.transport

import com.example.itantra.data.ConnectionStatus
import com.example.itantra.data.ReceivedMessage
import com.example.itantra.data.TransmissionMode
import com.example.itantra.util.Logger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class MockTransportEngine : TransportEngine {
    private val TAG = "MockTransport"

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<TransportPacket>(replay = 1, extraBufferCapacity = 32)
    override val incomingPackets: SharedFlow<TransportPacket> = _incomingPackets.asSharedFlow()

    private val _incomingMessages = MutableSharedFlow<ReceivedMessage>(replay = 1, extraBufferCapacity = 32)
    override val incomingMessages: SharedFlow<ReceivedMessage> = _incomingMessages.asSharedFlow()

    val sentPackets = mutableListOf<TransportPacket>()
    val sentMessages = mutableListOf<ReceivedMessage>()

    override fun initialize(): Result<Unit> {
        Logger.i(TAG, "MockTransportEngine initialized.")
        return Result.success(Unit)
    }

    override suspend fun connect(target: String): Result<Unit> {
        _connectionStatus.value = ConnectionStatus.SEARCHING
        Logger.i(TAG, "Searching for P2P nodes: $target")
        _connectionStatus.value = ConnectionStatus.CONNECTED
        Logger.i(TAG, "MockTransport connected.")
        return Result.success(Unit)
    }

    override suspend fun disconnect() {
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        Logger.i(TAG, "MockTransport disconnected.")
    }

    override suspend fun sendPacket(packet: TransportPacket): Result<TransportAck> {
        sentPackets.add(packet)
        Logger.i(TAG, "Mock packet sent: id=${packet.id}, size=${packet.payload.size} bytes.")
        return Result.success(TransportAck(packetId = packet.id, wireBytes = packet.payload.size))
    }

    override suspend fun sendMessage(message: ReceivedMessage): Result<TransportAck> {
        sentMessages.add(message)
        val wireBytes = if (message.compressedByteSize > 0) message.compressedByteSize else message.rawUtf8ByteSize
        Logger.i(TAG, "Mock message sent: id=${message.id}, wireBytes=$wireBytes.")
        return Result.success(TransportAck(packetId = message.id, wireBytes = wireBytes))
    }

    override fun setTransmissionMode(mode: TransmissionMode) {
        Logger.i(TAG, "Transmission mode updated to: ${mode.displayName}")
    }

    override fun release() {
        sentPackets.clear()
        sentMessages.clear()
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        Logger.i(TAG, "MockTransportEngine released.")
    }

    suspend fun simulateIncomingPacket(packet: TransportPacket) {
        Logger.i(TAG, "Simulated incoming packet received: id=${packet.id}")
        _incomingPackets.emit(packet)
    }

    suspend fun simulateIncomingMessage(message: ReceivedMessage) {
        Logger.i(TAG, "Simulated incoming message received: id=${message.id}, text='${message.text}'")
        _incomingMessages.emit(message)
    }
}
