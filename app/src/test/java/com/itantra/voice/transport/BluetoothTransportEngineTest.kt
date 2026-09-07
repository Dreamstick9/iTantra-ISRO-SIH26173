package com.itantra.voice.transport

import com.itantra.voice.transport.bluetooth.BluetoothTransportEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BluetoothTransportEngineTest {

    @Test
    fun testInitialStateIsDisconnected() = runTest {
        val engine = BluetoothTransportEngine(
            context = null,
            bluetoothAdapter = null,
            scope = this
        )

        assertEquals(TransportType.BLUETOOTH, engine.transportType)
        assertEquals(TransportConnectionState.DISCONNECTED, engine.connectionState.value)
        assertEquals(emptyList<DiscoveredPeer>(), engine.discoveredPeers.value)
        assertNull(engine.connectedPeer.value)
    }

    @Test
    fun testDisconnectResetsState() = runTest {
        val engine = BluetoothTransportEngine(
            context = null,
            bluetoothAdapter = null,
            scope = this
        )

        engine.disconnect()
        assertEquals(TransportConnectionState.DISCONNECTED, engine.connectionState.value)
        assertNull(engine.connectedPeer.value)
    }

    @Test
    fun testDiscoveredPeerWithBluetoothType() {
        val peer = DiscoveredPeer(
            id = "00:11:22:33:44:55",
            name = "iTantra-BT-Phone",
            deviceAddress = "00:11:22:33:44:55",
            isGroupOwner = false,
            transportType = TransportType.BLUETOOTH
        )

        assertEquals(TransportType.BLUETOOTH, peer.transportType)
        assertEquals("iTantra-BT-Phone", peer.name)
        assertEquals("00:11:22:33:44:55", peer.deviceAddress)
    }
}
