package com.itantra.voice.transport.wifidirect

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.os.Build
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.itantra.voice.transport.DiscoveredPeer
import com.itantra.voice.transport.TransportConnectionState
import com.itantra.voice.transport.TransportEngine
import com.itantra.voice.transport.TransportMessage
import com.itantra.voice.transport.TransportType
import com.itantra.voice.transport.security.WifiDirectPermissionHelper
import com.itantra.voice.transport.socket.PersistentTcpSocketManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

/**
 * Production implementation of [TransportEngine] backed by Android's official
 * Wi-Fi Direct ([WifiP2pManager]) and persistent framed TCP sockets.
 */
class WifiDirectTransportEngine(
    private val context: Context? = null,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val managerProvider: (() -> WifiP2pManager?)? = null,
    private val channelProvider: ((WifiP2pManager) -> WifiP2pManager.Channel?)? = null
) : TransportEngine {

    private val log = Logger.getLogger("WifiDirectTransportEngine")

    override val transportType: TransportType = TransportType.WIFI_DIRECT

    private val _connectionState = MutableStateFlow(TransportConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<TransportConnectionState> = _connectionState.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    override val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    private val _connectedPeer = MutableStateFlow<DiscoveredPeer?>(null)
    override val connectedPeer: StateFlow<DiscoveredPeer?> = _connectedPeer.asStateFlow()

    private val p2pManager: WifiP2pManager? by lazy {
        managerProvider?.invoke() ?: (context?.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager)
    }

    private val channel: WifiP2pManager.Channel? by lazy {
        val mgr = p2pManager ?: return@lazy null
        val ctx = context ?: return@lazy null
        channelProvider?.invoke(mgr) ?: mgr.initialize(ctx, Looper.getMainLooper(), null)
    }


    private val isReceiverRegistered = AtomicBoolean(false)
    private var thisDevice: WifiP2pDevice? = null

    val tcpSocketManager = PersistentTcpSocketManager(
        scope = scope,
        ioDispatcher = ioDispatcher,
        onConnected = {
            log.info("TCP socket established over Wi-Fi Direct link")
            _connectionState.value = TransportConnectionState.CONNECTED
        },
        onDisconnected = { reason ->
            log.info("TCP socket disconnected: $reason")
            // Also reset from CONNECTING: a socket that failed mid-handshake previously
            // left the UI pinned on "Connecting..." with no way back.
            if (_connectionState.value == TransportConnectionState.CONNECTED ||
                _connectionState.value == TransportConnectionState.CONNECTING
            ) {
                _connectionState.value = TransportConnectionState.DISCONNECTED
                _connectedPeer.value = null
            }
        }
    )

    override val incomingMessages: Flow<TransportMessage> = tcpSocketManager.incomingMessages

    private val p2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    val isP2pEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    log.info("Wi-Fi P2P state changed: enabled=$isP2pEnabled")
                    if (!isP2pEnabled) {
                        _connectionState.value = TransportConnectionState.DISCONNECTED
                    }
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    requestPeers()
                }

                @Suppress("DEPRECATION")
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        log.info("Wi-Fi P2P link established, requesting connection info...")
                        requestConnectionInfo()
                    } else {
                        // Ignore while CONNECTING: registering the receiver replays a
                        // sticky CONNECTION_CHANGED with isConnected=false, which used to
                        // cancel the connection attempt that had just been initiated.
                        if (_connectionState.value == TransportConnectionState.CONNECTING) {
                            log.info("Ignoring stale P2P disconnect while connecting")
                        } else {
                            log.info("Wi-Fi P2P link disconnected")
                            handleP2pDisconnect()
                        }
                    }
                }

                @Suppress("DEPRECATION")
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    thisDevice = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                    log.info("This device updated: ${thisDevice?.deviceName} (${thisDevice?.deviceAddress})")
                }

            }
        }
    }

    init {
        if (context != null) {
            registerReceiver()
        }
    }

    private fun registerReceiver() {
        val ctx = context ?: return
        if (!isReceiverRegistered.getAndSet(true)) {
            val filter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            }
            try {
                // Android 14+ (targetSdk 34+) requires an explicit export flag for
                // runtime-registered receivers. These are system P2P broadcasts only, so
                // the receiver must not be exported to other apps.
                ContextCompat.registerReceiver(
                    ctx,
                    p2pReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            } catch (e: Exception) {
                log.warning("Failed to register Wi-Fi Direct receiver: ${e.message}")
            }
        }
    }


    @SuppressLint("MissingPermission")
    override fun startDiscovery() {
        val ctx = context
        if (ctx != null && !WifiDirectPermissionHelper.hasWifiDirectPermissions(ctx)) {
            log.warning("Cannot start peer discovery: missing required Wi-Fi Direct permissions")
            _connectionState.value = TransportConnectionState.ERROR
            return
        }


        val mgr = p2pManager ?: return
        val ch = channel ?: return

        _connectionState.value = TransportConnectionState.DISCOVERING
        mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                log.info("discoverPeers initiated successfully")
            }

            override fun onFailure(reasonCode: Int) {
                log.warning("discoverPeers failed with reason code: $reasonCode")
                _connectionState.value = TransportConnectionState.DISCONNECTED
            }
        })
    }

    @SuppressLint("MissingPermission")
    override fun stopDiscovery() {
        val mgr = p2pManager ?: return
        val ch = channel ?: return

        mgr.stopPeerDiscovery(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                log.info("stopPeerDiscovery succeeded")
                if (_connectionState.value == TransportConnectionState.DISCOVERING) {
                    _connectionState.value = TransportConnectionState.DISCONNECTED
                }
            }

            override fun onFailure(reasonCode: Int) {
                log.warning("stopPeerDiscovery failed with code: $reasonCode")
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun requestPeers() {
        val ctx = context
        if (ctx != null && !WifiDirectPermissionHelper.hasWifiDirectPermissions(ctx)) return

        val mgr = p2pManager ?: return
        val ch = channel ?: return

        mgr.requestPeers(ch) { peerList: WifiP2pDeviceList? ->
            val peers = peerList?.deviceList?.map { device ->
                DiscoveredPeer(
                    id = device.deviceAddress,
                    name = device.deviceName.ifBlank { "iTantra-${device.deviceAddress.takeLast(4)}" },
                    deviceAddress = device.deviceAddress,
                    isGroupOwner = device.isGroupOwner,
                    transportType = TransportType.WIFI_DIRECT
                )
            } ?: emptyList()

            log.info("Discovered ${peers.size} nearby Wi-Fi Direct peer(s)")
            _discoveredPeers.value = peers
        }
    }

    @SuppressLint("MissingPermission")
    override fun connect(peer: DiscoveredPeer) {
        val ctx = context
        if (ctx != null && !WifiDirectPermissionHelper.hasWifiDirectPermissions(ctx)) {
            log.warning("Cannot connect: missing Wi-Fi Direct permissions")
            _connectionState.value = TransportConnectionState.ERROR
            return
        }


        val mgr = p2pManager ?: return
        val ch = channel ?: return

        // Discovery competes with group negotiation and makes connect() flaky.
        mgr.stopPeerDiscovery(ch, null)

        _connectionState.value = TransportConnectionState.CONNECTING
        _connectedPeer.value = peer

        val config = WifiP2pConfig().apply {
            deviceAddress = peer.deviceAddress
            // Let the peer that initiates become the client, so the roles (and therefore
            // which side listens on the TCP port) are decided deterministically.
            groupOwnerIntent = 0
        }

        mgr.connect(ch, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                log.info("Initiated connection to peer: ${peer.name} (${peer.deviceAddress})")
            }

            override fun onFailure(reason: Int) {
                log.warning("Failed to initiate connection to ${peer.name}: code $reason")
                _connectionState.value = TransportConnectionState.DISCONNECTED
                _connectedPeer.value = null
            }
        })
    }

    private fun requestConnectionInfo() {
        val mgr = p2pManager ?: return
        val ch = channel ?: return

        mgr.requestConnectionInfo(ch) { info: WifiP2pInfo? ->
            info ?: return@requestConnectionInfo
            if (info.groupFormed) {
                val groupOwnerAddress = info.groupOwnerAddress?.hostAddress
                log.info("Wi-Fi Direct Group formed. isGroupOwner=${info.isGroupOwner}, GO IP=$groupOwnerAddress")

                if (info.isGroupOwner) {
                    // Group Owner acts as TCP Server
                    tcpSocketManager.startServer()
                } else if (!groupOwnerAddress.isNullOrBlank()) {
                    // Client connects to Group Owner's IP
                    tcpSocketManager.connectClient(groupOwnerAddress)
                }
            }
        }
    }

    override suspend fun send(message: TransportMessage): Result<Unit> {
        return tcpSocketManager.send(message)
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        _connectionState.value = TransportConnectionState.DISCONNECTING

        tcpSocketManager.disconnect()

        val mgr = p2pManager
        val ch = channel
        if (mgr != null && ch != null) {
            mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    log.info("removeGroup succeeded")
                    handleP2pDisconnect()
                }

                override fun onFailure(reason: Int) {
                    log.warning("removeGroup failed with code: $reason")
                    handleP2pDisconnect()
                }
            })
        } else {
            handleP2pDisconnect()
        }
    }

    private fun handleP2pDisconnect() {
        tcpSocketManager.disconnect()
        _connectedPeer.value = null
        _connectionState.value = TransportConnectionState.DISCONNECTED
    }

    override fun release() {
        disconnect()
        if (isReceiverRegistered.getAndSet(false)) {
            try {
                context?.unregisterReceiver(p2pReceiver)
            } catch (ignored: Exception) {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                channel?.close()
            } catch (ignored: Exception) {}
        }
    }
}
