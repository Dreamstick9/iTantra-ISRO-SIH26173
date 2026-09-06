package com.example.itantra.transport

import java.util.UUID

enum class PacketType(val id: Byte) {
    VOICE_TEXT(0x01),
    EMERGENCY_ALERT(0x02),
    HEARTBEAT(0x03),
    ACK(0x04)
}

enum class PacketPriority {
    STANDARD,
    EMERGENCY
}

data class TransportPacket(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String,
    val payload: ByteArray,
    val packetType: PacketType = PacketType.VOICE_TEXT,
    val priority: PacketPriority = PacketPriority.STANDARD,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TransportPacket
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

data class TransportAck(
    val packetId: String,
    val wireBytes: Int,
    val timestamp: Long = System.currentTimeMillis()
)
