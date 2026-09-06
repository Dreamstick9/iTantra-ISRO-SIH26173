package com.example.itantra.transport.socket

import com.example.itantra.transport.TcpStatus
import com.example.itantra.transport.TransportMessage
import com.example.itantra.transport.TransportMessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.ServerSocket

@OptIn(ExperimentalCoroutinesApi::class)
class PersistentTcpSocketManagerTest {

    private var testPort: Int = 0
    private var serverManager: PersistentTcpSocketManager? = null
    private var clientManager: PersistentTcpSocketManager? = null

    @Before
    fun setUp() {
        val ephemeral = ServerSocket(0)
        testPort = ephemeral.localPort
        ephemeral.close()

        serverManager = PersistentTcpSocketManager(port = testPort, ioDispatcher = Dispatchers.IO)
        clientManager = PersistentTcpSocketManager(port = testPort, ioDispatcher = Dispatchers.IO)
    }

    @After
    fun tearDown() {
        runBlocking {
            clientManager?.disconnect()
            serverManager?.disconnect()
        }
    }

    @Test
    fun testServerStartsAndTransitionsToListening() {
        runBlocking {
            val server = serverManager!!
            assertEquals(TcpStatus.DISCONNECTED, server.tcpStatus.value)

            server.startServer(testPort)
            assertEquals(TcpStatus.LISTENING, server.tcpStatus.value)

            server.disconnect()
            assertEquals(TcpStatus.DISCONNECTED, server.tcpStatus.value)
        }
    }

    @Test
    fun testClientConnectsToServerSuccessfully() {
        runBlocking {
            val server = serverManager!!
            val client = clientManager!!

            server.startServer(testPort)
            assertEquals(TcpStatus.LISTENING, server.tcpStatus.value)

            client.connectToServer(hostAddress = "127.0.0.1", maxRetries = 3, targetPort = testPort)

            withTimeout(5000) {
                client.tcpStatus.first { it == TcpStatus.CONNECTED }
                server.tcpStatus.first { it == TcpStatus.CONNECTED }
            }

            assertEquals(TcpStatus.CONNECTED, client.tcpStatus.value)
            assertEquals(TcpStatus.CONNECTED, server.tcpStatus.value)
        }
    }

    @Test
    fun testTextMessageExchangeWithAutoAckAndRttLatency() {
        runBlocking {
            val server = serverManager!!
            val client = clientManager!!

            server.startServer(testPort)
            client.connectToServer(hostAddress = "127.0.0.1", maxRetries = 3, targetPort = testPort)

            withTimeout(5000) {
                client.tcpStatus.first { it == TcpStatus.CONNECTED }
                server.tcpStatus.first { it == TcpStatus.CONNECTED }
            }

            val testMessage = TransportMessage.text(
                text = "Emergency supplies dispatched to Sector 7",
                language = "en",
                priority = 1
            )

            // Send from client to server
            client.send(testMessage)

            // Verify server received the incoming text message
            val receivedMessage = withTimeout(5000) {
                server.incomingMessages.first { it.messageId == testMessage.messageId }
            }

            assertEquals(testMessage.messageId, receivedMessage.messageId)
            assertEquals(testMessage.text, receivedMessage.text)
            assertEquals(testMessage.language, receivedMessage.language)
            assertEquals(TransportMessageType.TEXT, receivedMessage.messageType)

            // Verify client received automatic ACK and recorded RTT latency
            val clientRtt = withTimeout(5000) {
                client.roundTripLatencyMs.filter { it != null && it >= 0 }.first()
            }

            assertNotNull(clientRtt)
            assertTrue(clientRtt!! >= 0)

            // Verify stats
            assertTrue(client.bytesSent.value > 0)
            assertTrue(server.bytesReceived.value > 0)
            assertTrue(server.bytesSent.value > 0) // for the auto ACK
            assertTrue(client.bytesReceived.value > 0) // for receiving the ACK
        }
    }

    @Test
    fun testAlertMessageExchangeWithAutoAck() {
        runBlocking {
            val server = serverManager!!
            val client = clientManager!!

            server.startServer(testPort)
            client.connectToServer(hostAddress = "127.0.0.1", maxRetries = 3, targetPort = testPort)

            withTimeout(5000) {
                client.tcpStatus.first { it == TcpStatus.CONNECTED }
                server.tcpStatus.first { it == TcpStatus.CONNECTED }
            }

            val alertMessage = TransportMessage.alert(
                text = "सुनामी चेतावनी: सुरक्षित स्थान पर जाएं",
                language = "hi",
                priority = 2
            )

            // Send alert from server to client
            server.send(alertMessage)

            // Verify client received the incoming alert message
            val receivedMessage = withTimeout(5000) {
                client.incomingMessages.first { it.messageId == alertMessage.messageId }
            }

            assertEquals(alertMessage.messageId, receivedMessage.messageId)
            assertEquals(alertMessage.text, receivedMessage.text)
            assertEquals(alertMessage.language, receivedMessage.language)
            assertEquals(TransportMessageType.ALERT, receivedMessage.messageType)

            // Verify server received automatic ACK and recorded RTT latency
            val serverRtt = withTimeout(5000) {
                server.roundTripLatencyMs.filter { it != null && it >= 0 }.first()
            }

            assertNotNull(serverRtt)
            assertTrue(serverRtt!! >= 0)
        }
    }

    @Test
    fun testSequentialMultilingualMessages() {
        runBlocking {
            val server = serverManager!!
            val client = clientManager!!

            server.startServer(testPort)
            client.connectToServer(hostAddress = "127.0.0.1", maxRetries = 3, targetPort = testPort)

            withTimeout(5000) {
                client.tcpStatus.first { it == TcpStatus.CONNECTED }
                server.tcpStatus.first { it == TcpStatus.CONNECTED }
            }

            val messages = listOf(
                TransportMessage.text("Cyclone alert", language = "en"),
                TransportMessage.text("புயல் எச்சரிக்கை", language = "ta"),
                TransportMessage.text("తుఫాను హెచ్చరిక", language = "te")
            )

            for (msg in messages) {
                client.send(msg)
                val received = withTimeout(5000) {
                    server.incomingMessages.first { it.messageId == msg.messageId }
                }
                assertEquals(msg.text, received.text)
                assertEquals(msg.language, received.language)
            }

            assertEquals(messages.last().messageId, (server.lastMessage.value as? TransportMessage)?.messageId)
        }
    }

    @Test
    fun testConnectToServerFailsOnUnreachableHostAndSetsErrorStatus() {
        runBlocking {
            // Use an unused valid port with maxRetries = 2
            val unreachablePort = if (testPort + 500 <= 65530) testPort + 500 else testPort - 500
            val client = PersistentTcpSocketManager(port = unreachablePort, ioDispatcher = Dispatchers.IO)

            client.connectToServer(hostAddress = "127.0.0.1", maxRetries = 2, targetPort = unreachablePort)

            assertEquals(TcpStatus.ERROR, client.tcpStatus.value)
            client.disconnect()
        }
    }

    @Test
    fun testDisconnectClosesSocketAndThrowsOnSubsequentSend() {
        runBlocking {
            val server = serverManager!!
            val client = clientManager!!

            server.startServer(testPort)
            client.connectToServer(hostAddress = "127.0.0.1", maxRetries = 3, targetPort = testPort)

            withTimeout(5000) {
                client.tcpStatus.first { it == TcpStatus.CONNECTED }
            }

            client.disconnect()
            assertEquals(TcpStatus.DISCONNECTED, client.tcpStatus.value)

            assertThrows(IOException::class.java) {
                runBlocking {
                    client.send(TransportMessage.text("Should fail"))
                }
            }
        }
    }

    @Test
    fun testResetStats() {
        runBlocking {
            val server = serverManager!!
            val client = clientManager!!

            server.startServer(testPort)
            client.connectToServer(hostAddress = "127.0.0.1", maxRetries = 3, targetPort = testPort)

            withTimeout(5000) {
                client.tcpStatus.first { it == TcpStatus.CONNECTED }
            }

            client.send(TransportMessage.text("Test message"))
            withTimeout(5000) {
                client.roundTripLatencyMs.filter { it != null }.first()
            }

            assertTrue(client.bytesSent.value > 0)
            assertNotNull(client.roundTripLatencyMs.value)

            client.resetStats()

            assertEquals(0L, client.bytesSent.value)
            assertEquals(0L, client.bytesReceived.value)
            assertNull(client.roundTripLatencyMs.value)
            assertNull(client.lastMessage.value)
        }
    }
}
