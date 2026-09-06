package com.example.itantra.transport

import com.example.itantra.transport.socket.PersistentTcpSocketManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket

class PersistentTcpSocketTest {

    private fun findAvailablePort(): Int {
        ServerSocket(0).use { ss ->
            return ss.localPort
        }
    }

    @Test
    fun testServerClientDuplexCommunicationAndAck() = runBlocking {
        val testPort = findAvailablePort()
        val serverManager = PersistentTcpSocketManager(port = testPort)
        val clientManager = PersistentTcpSocketManager(port = testPort)

        try {
            // 1. Start Server
            serverManager.startServer()
            assertEquals(TcpStatus.LISTENING, serverManager.tcpStatus.value)

            // 2. Client connects to Server on localhost
            clientManager.connectToServer(hostAddress = "127.0.0.1")

            // Wait for both to reach CONNECTED status
            withTimeout(5000) {
                while (serverManager.tcpStatus.value != TcpStatus.CONNECTED ||
                    clientManager.tcpStatus.value != TcpStatus.CONNECTED) {
                    delay(50)
                }
            }

            assertEquals(TcpStatus.CONNECTED, serverManager.tcpStatus.value)
            assertEquals(TcpStatus.CONNECTED, clientManager.tcpStatus.value)

            // 3. Client sends "HELLO FROM PHONE A" to Server
            val sentMsg = TransportMessage.text(
                text = "HELLO FROM PHONE A",
                language = "en",
                priority = 0
            )

            val serverReceivedDeferred = launch {
                val receivedMsg = serverManager.incomingMessages.first()
                assertEquals(sentMsg.messageId, receivedMsg.messageId)
                assertEquals("HELLO FROM PHONE A", receivedMsg.text)
                assertEquals(TransportMessageType.TEXT, receivedMsg.messageType)
            }

            clientManager.send(sentMsg)
            serverReceivedDeferred.join()

            // 4. Server should have automatically dispatched an ACK back to Client
            // Wait for client to receive ACK and compute RTT latency
            withTimeout(3000) {
                while (clientManager.roundTripLatencyMs.value == null) {
                    delay(20)
                }
            }

            val rtt = clientManager.roundTripLatencyMs.value
            assertNotNull("RTT latency should be calculated upon receiving ACK", rtt)
            assertTrue("RTT latency should be >= 0ms, was $rtt", rtt!! >= 0)

            // 5. Verify byte counters
            assertTrue("Client bytesSent should be > 0", clientManager.bytesSent.value > 0)
            assertTrue("Server bytesReceived should be > 0", serverManager.bytesReceived.value > 0)

            // 6. Test bidirectional: Server sends message to Client
            val replyMsg = TransportMessage.text(
                text = "HELLO FROM PHONE B",
                language = "en",
                priority = 0
            )

            val clientReceivedDeferred = launch {
                val receivedReply = clientManager.incomingMessages.first()
                assertEquals("HELLO FROM PHONE B", receivedReply.text)
            }

            serverManager.send(replyMsg)
            clientReceivedDeferred.join()

        } finally {
            clientManager.disconnect()
            serverManager.disconnect()
        }
    }
}
