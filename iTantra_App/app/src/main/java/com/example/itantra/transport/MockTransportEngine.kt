package com.example.itantra.transport

import com.example.itantra.data.ConnectionStatus
import com.example.itantra.data.ReceivedMessage
import com.example.itantra.data.TransmissionMode
import com.example.itantra.util.Logger
import kotlinx.coroutines.flow.Flow
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

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    override val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

    private val _diagnostics = MutableStateFlow(TransportDiagnostics())
    override val diagnostics: StateFlow<TransportDiagnostics> = _diagnostics.asStateFlow()

    private val _incomingMessagesFlow = MutableSharedFlow<TransportMessage>(replay = 1, extraBufferCapacity = 32)
    override fun incomingMessages(): Flow<TransportMessage> = _incomingMessagesFlow.asSharedFlow()

    private val _incomingPackets = MutableSharedFlow<TransportPacket>(replay = 1, extraBufferCapacity = 32)
    val incomingPackets: SharedFlow<TransportPacket> = _incomingPackets.asSharedFlow()

    val sentPackets = mutableListOf<TransportPacket>()
    val sentMessages = mutableListOf<ReceivedMessage>()
    val sentTransportMessages = mutableListOf<TransportMessage>()

    override fun initialize(): Result<Unit> {
        Logger.i(TAG, "MockTransportEngine initialized.")
        return Result.success(Unit)
    }

    override suspend fun startDiscovery() {
        _diagnostics.value = _diagnostics.value.copy(p2pStatus = P2pStatus.DISCOVERING)
        _connectionStatus.value = ConnectionStatus.SEARCHING
        Logger.i(TAG, "MockTransport startDiscovery called.")
    }

    override suspend fun connect(peer: Peer) {
        _diagnostics.value = _diagnostics.value.copy(
            p2pStatus = P2pStatus.CONNECTING,
            connectedPeerName = peer.deviceName,
            connectedPeerAddress = peer.deviceAddress
        )
        _connectionStatus.value = ConnectionStatus.SEARCHING
        Logger.i(TAG, "MockTransport connecting to peer: ${peer.deviceName}")

        _diagnostics.value = _diagnostics.value.copy(
            p2pStatus = if (peer.isGroupOwner) P2pStatus.CONNECTED_AS_CLIENT else P2pStatus.CONNECTED_AS_GO,
            tcpStatus = TcpStatus.CONNECTED,
            connectedPeerName = peer.deviceName,
            connectedPeerAddress = peer.deviceAddress
        )
        _connectionStatus.value = ConnectionStatus.CONNECTED
        Logger.i(TAG, "MockTransport connected to peer: ${peer.deviceName}")
    }

    override suspend fun connect(target: String): Result<Unit> {
        _connectionStatus.value = ConnectionStatus.SEARCHING
        _diagnostics.value = _diagnostics.value.copy(p2pStatus = P2pStatus.CONNECTING)
        Logger.i(TAG, "Searching for P2P nodes: $target")

        _connectionStatus.value = ConnectionStatus.CONNECTED
        _diagnostics.value = _diagnostics.value.copy(
            p2pStatus = P2pStatus.CONNECTED_AS_GO,
            tcpStatus = TcpStatus.CONNECTED,
            connectedPeerName = target
        )
        Logger.i(TAG, "MockTransport connected.")
        return Result.success(Unit)
    }

    override suspend fun send(message: TransportMessage) {
        sentTransportMessages.add(message)
        _diagnostics.value = _diagnostics.value.copy(
            bytesSent = _diagnostics.value.bytesSent + message.compressedPayload.size,
            lastMessage = message
        )
        Logger.i(TAG, "Mock message sent: id=${message.messageId}, type=${message.messageType}, size=${message.compressedPayload.size} bytes.")
    }

    override suspend fun sendMessage(message: ReceivedMessage): Result<TransportAck> {
        sentMessages.add(message)
        val tMsg = TransportMessage.fromReceivedMessage(message)
        send(tMsg)
        val wireBytes = if (message.compressedByteSize > 0) message.compressedByteSize else message.rawUtf8ByteSize
        return Result.success(TransportAck(packetId = message.id, wireBytes = wireBytes))
    }

    suspend fun sendPacket(packet: TransportPacket): Result<TransportAck> {
        sentPackets.add(packet)
        Logger.i(TAG, "Mock packet sent: id=${packet.id}, size=${packet.payload.size} bytes.")
        return Result.success(TransportAck(packetId = packet.id, wireBytes = packet.payload.size))
    }

    override suspend fun disconnect() {
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _diagnostics.value = _diagnostics.value.copy(
            p2pStatus = P2pStatus.DISCONNECTED,
            tcpStatus = TcpStatus.DISCONNECTED,
            connectedPeerName = null,
            connectedPeerAddress = null
        )
        Logger.i(TAG, "MockTransport disconnected.")
    }

    override fun setTransmissionMode(mode: TransmissionMode) {
        Logger.i(TAG, "Transmission mode updated to: ${mode.displayName}")
    }

    override fun release() {
        sentPackets.clear()
        sentMessages.clear()
        sentTransportMessages.clear()
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _diagnostics.value = TransportDiagnostics()
        Logger.i(TAG, "MockTransportEngine released.")
    }

    fun updatePeers(peersList: List<Peer>) {
        _peers.value = peersList
    }

    fun updateDiagnostics(diag: TransportDiagnostics) {
        _diagnostics.value = diag
    }

    suspend fun simulateIncomingPacket(packet: TransportPacket) {
        Logger.i(TAG, "Simulated incoming packet received: id=${packet.id}")
        _incomingPackets.emit(packet)
    }

    suspend fun simulateIncomingMessage(message: TransportMessage) {
        Logger.i(TAG, "Simulated incoming TransportMessage received: id=${message.messageId}, text='${message.text}'")
        _diagnostics.value = _diagnostics.value.copy(
            bytesReceived = _diagnostics.value.bytesReceived + message.compressedPayload.size,
            lastMessage = message
        )
        _incomingMessagesFlow.emit(message)
    }

    suspend fun simulateIncomingMessage(message: ReceivedMessage) {
        Logger.i(TAG, "Simulated incoming ReceivedMessage received: id=${message.id}, text='${message.text}'")
        val tMsg = TransportMessage.fromReceivedMessage(message)
        simulateIncomingMessage(tMsg)
    }
}
