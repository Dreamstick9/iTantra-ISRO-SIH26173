package com.itantra.voice.transport

import android.content.Context
import com.itantra.voice.transport.wifidirect.WifiDirectTransportEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class WifiDirectTransportEngineTest {

    @Test
    fun testInitialStateIsDisconnectedWithEmptyPeers() = runTest {
        val engine = WifiDirectTransportEngine(
            context = null,
            scope = this,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            managerProvider = { null },
            channelProvider = { null }
        )

        assertEquals(TransportConnectionState.DISCONNECTED, engine.connectionState.value)
        assertEquals(emptyList<DiscoveredPeer>(), engine.discoveredPeers.value)
        assertNull(engine.connectedPeer.value)
        assertEquals(TransportType.WIFI_DIRECT, engine.transportType)
    }

    @Test
    fun testDisconnectResetsConnectedPeerAndState() = runTest {
        val engine = WifiDirectTransportEngine(
            context = null,
            scope = this,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            managerProvider = { null },
            channelProvider = { null }
        )


        engine.disconnect()
        assertEquals(TransportConnectionState.DISCONNECTED, engine.connectionState.value)
        assertNull(engine.connectedPeer.value)
    }

    @Test
    fun testDiscoveredPeerModelFields() {
        val peer = DiscoveredPeer(
            id = "aa:bb:cc:dd:ee:ff",
            name = "Phone-B-Pixel",
            deviceAddress = "aa:bb:cc:dd:ee:ff",
            isGroupOwner = true,
            transportType = TransportType.WIFI_DIRECT
        )

        assertEquals("Phone-B-Pixel", peer.name)
        assertEquals("aa:bb:cc:dd:ee:ff", peer.deviceAddress)
        assertEquals(true, peer.isGroupOwner)
        assertEquals(TransportType.WIFI_DIRECT, peer.transportType)
    }
}
