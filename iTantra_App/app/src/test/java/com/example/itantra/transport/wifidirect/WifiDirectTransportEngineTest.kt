package com.example.itantra.transport.wifidirect

import android.content.Context
import android.content.ContextWrapper
import com.example.itantra.data.ConnectionStatus
import com.example.itantra.transport.*
import com.example.itantra.transport.socket.PersistentTcpSocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket

@OptIn(ExperimentalCoroutinesApi::class)
class WifiDirectTransportEngineTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testContext: Context = ContextWrapper(null)

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testNullP2pManagerGracefulErrorHandling() = runTest(testDispatcher) {
        val engine = WifiDirectTransportEngine(
            context = testContext,
            p2pManager = null,
            tcpSocketManager = PersistentTcpSocketManager(),
            ioDispatcher = testDispatcher
        )

        testScheduler.advanceUntilIdle()

        val diag = engine.diagnostics.value
        assertEquals(P2pStatus.ERROR, diag.p2pStatus)
        assertEquals("Wi-Fi Direct is not supported on this device", diag.lastError)
        assertEquals(ConnectionStatus.DISCONNECTED, engine.connectionStatus.value)
        assertTrue(engine.peers.value.isEmpty())
    }

    @Test
    fun testTcpSocketManagerDelegationAndDuplexExchange() = runBlocking {
        // Find an open port
        val testPort = ServerSocket(0).use { it.localPort }

        val serverTcp = PersistentTcpSocketManager(port = testPort, ioDispatcher = Dispatchers.IO)
        val clientTcp = PersistentTcpSocketManager(port = testPort, ioDispatcher = Dispatchers.IO)

        val serverEngine = WifiDirectTransportEngine(
            context = testContext,
            p2pManager = null,
            tcpSocketManager = serverTcp,
            ioDispatcher = Dispatchers.IO
        )

        val clientEngine = WifiDirectTransportEngine(
            context = testContext,
            p2pManager = null,
            tcpSocketManager = clientTcp,
            ioDispatcher = Dispatchers.IO
        )

        try {
            serverTcp.startServer(testPort)
            clientTcp.connectToServer(hostAddress = "127.0.0.1", maxRetries = 3, targetPort = testPort)

            // Wait for both to connect
            clientTcp.tcpStatus.first { it == TcpStatus.CONNECTED }
            serverTcp.tcpStatus.first { it == TcpStatus.CONNECTED }

            val message = TransportMessage.text("HELLO FROM PHONE A", language = "en", priority = 0)

            // Send from client engine
            clientEngine.send(message)

            // Receive via server engine's incomingMessages flow
            val received = serverEngine.incomingMessages().first { it.messageId == message.messageId }

            assertEquals(message.messageId, received.messageId)
            assertEquals("HELLO FROM PHONE A", received.text)
            assertEquals(TransportMessageType.TEXT, received.messageType)

            // Verify client received ACK and computed RTT latency
            val rtt = clientTcp.roundTripLatencyMs.first { it != null && it >= 0 }
            assertNotNull(rtt)
            assertTrue(rtt!! >= 0)

            // Verify telemetry
            assertTrue(clientTcp.bytesSent.value > 0)
            assertTrue(serverTcp.bytesReceived.value > 0)

        } finally {
            clientEngine.disconnect()
            serverEngine.disconnect()
        }
    }
}
