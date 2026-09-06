package com.example.itantra.transport

enum class PeerStatus(val code: Int) {
    CONNECTED(0),
    INVITED(1),
    FAILED(2),
    AVAILABLE(3),
    UNAVAILABLE(4);

    companion object {
        fun fromCode(code: Int): PeerStatus =
            entries.firstOrNull { it.code == code } ?: UNAVAILABLE
    }
}

data class Peer(
    val deviceAddress: String,
    val deviceName: String,
    val status: PeerStatus = PeerStatus.AVAILABLE,
    val isGroupOwner: Boolean = false
)
