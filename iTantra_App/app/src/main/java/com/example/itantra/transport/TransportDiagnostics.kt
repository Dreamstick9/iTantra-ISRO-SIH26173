package com.example.itantra.transport

enum class P2pStatus {
    OFFLINE,
    INITIALIZING,
    DISCOVERING,
    CONNECTING,
    CONNECTED_AS_GO,
    CONNECTED_AS_CLIENT,
    DISCONNECTED,
    ERROR
}

enum class TcpStatus {
    DISCONNECTED,
    LISTENING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

data class TransportDiagnostics(
    val p2pStatus: P2pStatus = P2pStatus.OFFLINE,
    val tcpStatus: TcpStatus = TcpStatus.DISCONNECTED,
    val connectedPeerName: String? = null,
    val connectedPeerAddress: String? = null,
    val isGroupOwner: Boolean = false,
    val localIpAddress: String? = null,
    val remoteIpAddress: String? = null,
    val bytesSent: Long = 0L,
    val bytesReceived: Long = 0L,
    val lastMessage: TransportMessage? = null,
    val roundTripLatencyMs: Long? = null,
    val lastError: String? = null
)
