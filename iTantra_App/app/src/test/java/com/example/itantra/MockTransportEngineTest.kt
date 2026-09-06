package com.example.itantra

import com.example.itantra.data.ConnectionStatus
import com.example.itantra.data.Language
import com.example.itantra.data.ReceivedMessage
import com.example.itantra.transport.MockTransportEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MockTransportEngineTest {

    private lateinit var engine: MockTransportEngine

    @Before
    fun setUp() {
        engine = MockTransportEngine()
        engine.initialize()
    }

    @Test
    fun testConnectAndDisconnect() = runBlocking {
        assertEquals(ConnectionStatus.DISCONNECTED, engine.connectionStatus.value)

        engine.connect()
        assertEquals(ConnectionStatus.CONNECTED, engine.connectionStatus.value)

        engine.disconnect()
        assertEquals(ConnectionStatus.DISCONNECTED, engine.connectionStatus.value)
    }

    @Test
    fun testSendMessage() = runBlocking {
        engine.connect()
        val msg = ReceivedMessage(
            senderId = "1",
            senderName = "Local",
            text = "Alert",
            originalLanguage = Language.ENGLISH
        )
        val ack = engine.sendMessage(msg).getOrThrow()
        assertEquals(msg.id, ack.packetId)
        assertEquals(1, engine.sentMessages.size)
    }

    @Test
    fun testIncomingMessageFlow() = runBlocking {
        val incoming = ReceivedMessage(
            senderId = "2",
            senderName = "Peer",
            text = "Incoming message",
            originalLanguage = Language.HINDI
        )

        engine.simulateIncomingMessage(incoming)
        val received = engine.incomingMessages.first()

        assertEquals(incoming.id, received.id)
        assertEquals(incoming.text, received.text)
    }
}
