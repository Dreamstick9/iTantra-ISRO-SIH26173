package com.itantra.voice.transport

import com.itantra.voice.transport.socket.PersistentTcpSocketManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket

@OptIn(ExperimentalCoroutinesApi::class)
class PersistentTcpSocketTest {

    @Test
    fun testBidirectionalMessageExchangeOverLoopback() = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        val tempServer = ServerSocket(0)
        val port = tempServer.localPort
        tempServer.close()

        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)

        var serverConnected = false
        var clientConnected = false

        val serverManager = PersistentTcpSocketManager(
            scope = testScope,
            onConnected = { serverConnected = true }
        )
        val clientManager = PersistentTcpSocketManager(
            scope = testScope,
            onConnected = { clientConnected = true }
        )

        // Start server listening
        serverManager.startServer(port)
        Thread.sleep(50)

        // Connect client
        clientManager.connectClient("127.0.0.1", port)

        // Wait briefly for real connection handshake
        var attempts = 0
        while ((!serverManager.isConnected || !clientManager.isConnected) && attempts < 100) {
            Thread.sleep(50)
            attempts++
        }

        assertTrue("Server should be connected", serverManager.isConnected)
        assertTrue("Client should be connected", clientManager.isConnected)


        // 1. Client sends to Server: "HELLO FROM ITANTRA"
        val testMessage = TransportMessage.createTestMessage(sourceLang = "hi-IN", targetLang = "en-IN")

        // incomingMessages has no replay buffer (a replay buffer re-delivers the last
        // message to every new collector, which made the receiving phone speak twice).
        // The collector must therefore be attached before the sender writes, so wait for
        // the coroutine to actually reach `first()` rather than assuming it has.
        val serverSubscribed = kotlinx.coroutines.CompletableDeferred<Unit>()
        val serverReceivedDeferred = async {
            serverManager.incomingMessages
                .onSubscription { serverSubscribed.complete(Unit) }
                .first()
        }
        serverSubscribed.await()

        val sendResult = clientManager.send(testMessage)
        assertTrue("Client send should succeed", sendResult.isSuccess)

        val serverReceived = withTimeout(3000) { serverReceivedDeferred.await() }
        assertEquals("HELLO FROM ITANTRA", serverReceived.text)
        assertEquals("hi-IN", serverReceived.sourceLanguage)
        assertEquals("en-IN", serverReceived.targetLanguage)

        // 2. Server sends response to Client
        val replyMessage = TransportMessage(
            sourceLanguage = "en-IN",
            targetLanguage = "hi-IN",
            text = "RESPONSE FROM SERVER",
            type = TransportMessageType.TRANSLATION
        )

        val clientSubscribed = kotlinx.coroutines.CompletableDeferred<Unit>()
        val clientReceivedDeferred = async {
            clientManager.incomingMessages
                .onSubscription { clientSubscribed.complete(Unit) }
                .first()
        }
        clientSubscribed.await()

        val replyResult = serverManager.send(replyMessage)
        assertTrue("Server reply should succeed", replyResult.isSuccess)

        val clientReceived = withTimeout(3000) { clientReceivedDeferred.await() }
        assertEquals("RESPONSE FROM SERVER", clientReceived.text)

        // Clean teardown
        clientManager.disconnect()
        serverManager.disconnect()

        assertFalse(clientManager.isConnected)
        assertFalse(serverManager.isConnected)
    }
}
