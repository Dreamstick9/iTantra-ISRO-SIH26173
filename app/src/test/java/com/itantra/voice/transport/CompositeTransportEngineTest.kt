package com.itantra.voice.transport

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompositeTransportEngineTest {

    private class FakeTransportEngine(
        override val transportType: TransportType
    ) : TransportEngine {
        val _connectionState = MutableStateFlow(TransportConnectionState.DISCONNECTED)
        override val connectionState: StateFlow<TransportConnectionState> = _connectionState

        val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
        override val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers

        val _connectedPeer = MutableStateFlow<DiscoveredPeer?>(null)
        override val connectedPeer: StateFlow<DiscoveredPeer?> = _connectedPeer

        val _incomingMessages = MutableSharedFlow<TransportMessage>(replay = 1, extraBufferCapacity = 64)
        override val incomingMessages: Flow<TransportMessage> = _incomingMessages

        var startDiscoveryCalled = false
        var stopDiscoveryCalled = false
        var lastConnectedPeer: DiscoveredPeer? = null
        var disconnectCalled = false
        var sentMessages = mutableListOf<TransportMessage>()

        override fun startDiscovery() { startDiscoveryCalled = true }
        override fun stopDiscovery() { stopDiscoveryCalled = true }
        override fun connect(peer: DiscoveredPeer) {
            lastConnectedPeer = peer
            _connectedPeer.value = peer
            _connectionState.value = TransportConnectionState.CONNECTED
        }
        override fun disconnect() {
            disconnectCalled = true
            _connectedPeer.value = null
            _connectionState.value = TransportConnectionState.DISCONNECTED
        }
        override suspend fun send(message: TransportMessage): Result<Unit> {
            sentMessages.add(message)
            return Result.success(Unit)
        }
        override fun release() { disconnect() }
    }

    @Test
    fun testDefaultTransportIsWifiDirectAndSwitchesToBluetooth() = runTest {
        val fakeWifi = FakeTransportEngine(TransportType.WIFI_DIRECT)
        val fakeBt = FakeTransportEngine(TransportType.BLUETOOTH)

        val composite = CompositeTransportEngine(
            wifiEngine = fakeWifi,
            bluetoothEngine = fakeBt,
            scope = this
        )

        assertEquals(TransportType.WIFI_DIRECT, composite.transportType)
        assertEquals(TransportType.WIFI_DIRECT, composite.selectedTransport.value)

        // Switch to Bluetooth
        composite.selectTransport(TransportType.BLUETOOTH)
        assertEquals(TransportType.BLUETOOTH, composite.transportType)
        assertEquals(TransportType.BLUETOOTH, composite.selectedTransport.value)

        composite.release()
    }

    @Test
    fun testConnectRoutesToCorrectUnderlyingEngine() = runTest {
        val fakeWifi = FakeTransportEngine(TransportType.WIFI_DIRECT)
        val fakeBt = FakeTransportEngine(TransportType.BLUETOOTH)

        val composite = CompositeTransportEngine(
            wifiEngine = fakeWifi,
            bluetoothEngine = fakeBt,
            scope = this
        )

        val btPeer = DiscoveredPeer(
            id = "bt-123",
            name = "BT Device",
            deviceAddress = "00:11:22:33:44:55",
            transportType = TransportType.BLUETOOTH
        )

        composite.connect(btPeer)
        testScheduler.runCurrent()

        assertEquals(TransportType.BLUETOOTH, composite.selectedTransport.value)
        assertEquals("bt-123", fakeBt.lastConnectedPeer?.id)
        assertEquals(TransportConnectionState.CONNECTED, composite.connectionState.value)

        composite.release()
    }

    @Test
    fun testIncomingMessagesMergedFromBothEngines() = runTest {
        val fakeWifi = FakeTransportEngine(TransportType.WIFI_DIRECT)
        val fakeBt = FakeTransportEngine(TransportType.BLUETOOTH)

        val composite = CompositeTransportEngine(
            wifiEngine = fakeWifi,
            bluetoothEngine = fakeBt,
            scope = this
        )

        val msgFromWifi = TransportMessage(
            sourceLanguage = "en-IN",
            targetLanguage = "hi-IN",
            text = "From WiFi"
        )
        val msgFromBt = TransportMessage(
            sourceLanguage = "hi-IN",
            targetLanguage = "en-IN",
            text = "From Bluetooth"
        )

        val received = mutableListOf<TransportMessage>()
        val job = launch {
            composite.incomingMessages.collect {
                received.add(it)
            }
        }

        testScheduler.runCurrent()

        fakeWifi._incomingMessages.emit(msgFromWifi)
        fakeBt._incomingMessages.emit(msgFromBt)

        testScheduler.runCurrent()

        assertEquals(2, received.size)
        assertEquals("From WiFi", received[0].text)
        assertEquals("From Bluetooth", received[1].text)

        job.cancel()
        composite.release()
    }
}
