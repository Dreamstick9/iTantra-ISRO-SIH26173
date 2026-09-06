package com.example.itantra.transport.wifidirect

import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.WpsInfo
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.itantra.data.ConnectionStatus
import com.example.itantra.transport.*
import com.example.itantra.transport.security.WifiDirectPermissionHelper
import com.example.itantra.transport.socket.PersistentTcpSocketManager
import com.example.itantra.util.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Production implementation of [TransportEngine] using Android Wi-Fi Direct (WifiP2pManager)
 * and [PersistentTcpSocketManager] for phone-to-phone text transport.
 *
 * Implements:
 * - P2P peer discovery and connection negotiation
 * - Autonomous Group Owner / Client TCP socket role assignment
 * - Full-duplex persistent framed TCP stream on port 8988
 * - Real-time diagnostics, byte telemetry, and round-trip latency tracking
 */
class WifiDirectTransportEngine(
    private val context: Context,
    private val p2pManager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager,
    val tcpSocketManager: PersistentTcpSocketManager = PersistentTcpSocketManager(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)
) : TransportEngine {

    companion object {
        private const val TAG = "WifiDirectEngine"
        const val TCP_PORT = 8988
        const val DEFAULT_GO_IP = "192.168.49.1"
    }

    private var channel: WifiP2pManager.Channel? = null
    private var receiver: WifiDirectBroadcastReceiver? = null
    private var isReceiverRegistered = false

    // ---------------------------------------------------------------------------------------------
    // Observable Flows
    // ---------------------------------------------------------------------------------------------

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    override val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

    private val _p2pStatus = MutableStateFlow(P2pStatus.OFFLINE)
    private val _connectedPeerName = MutableStateFlow<String?>(null)
    private val _connectedPeerAddress = MutableStateFlow<String?>(null)
    private val _isGroupOwner = MutableStateFlow(false)
    private val _localIpAddress = MutableStateFlow<String?>(null)
    private val _remoteIpAddress = MutableStateFlow<String?>(null)
    private val _lastError = MutableStateFlow<String?>(null)

    private val _diagnostics = MutableStateFlow(TransportDiagnostics())
    override val diagnostics: StateFlow<TransportDiagnostics> = _diagnostics.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    init {
        initializeChannel()
        startDiagnosticsObservation()
    }

    // ---------------------------------------------------------------------------------------------
    // Initialization & Lifecycle
    // ---------------------------------------------------------------------------------------------

    private fun initializeChannel() {
        if (p2pManager != null) {
            channel = p2pManager.initialize(context, context.mainLooper) {
                Logger.w(TAG, "Wi-Fi Direct channel lost. Attempting re-initialization...")
                _p2pStatus.value = P2pStatus.ERROR
                _lastError.value = "Wi-Fi Direct channel lost"
            }
            _p2pStatus.value = P2pStatus.INITIALIZING
            Logger.i(TAG, "WifiP2pManager channel initialized successfully.")
        } else {
            Logger.e(TAG, "WifiP2pManager is null. Device may not support Wi-Fi Direct.")
            _p2pStatus.value = P2pStatus.ERROR
            _lastError.value = "Wi-Fi Direct is not supported on this device"
        }
    }

    override fun initialize(): Result<Unit> {
        registerBroadcastReceiver()
        return Result.success(Unit)
    }

    override fun release() {
        scope.launch(ioDispatcher) {
            disconnect()
        }
        unregisterBroadcastReceiver()
        channel?.close()
        channel = null
    }

    private fun registerBroadcastReceiver() {
        if (isReceiverRegistered || p2pManager == null || channel == null) return

        val broadcastReceiver = WifiDirectBroadcastReceiver(
            manager = p2pManager,
            channel = channel!!,
            onWifiP2pEnabled = { enabled ->
                Logger.i(TAG, "Wi-Fi P2P enabled: $enabled")
                if (enabled) {
                    if (_p2pStatus.value == P2pStatus.OFFLINE || _p2pStatus.value == P2pStatus.INITIALIZING) {
                        _p2pStatus.value = P2pStatus.DISCONNECTED
                    }
                } else {
                    _p2pStatus.value = P2pStatus.OFFLINE
                    _lastError.value = "Wi-Fi Direct is disabled in system settings"
                }
            },
            onPeersAvailable = { peerList ->
                handlePeersDiscovered(peerList)
            },
            onConnectionInfoAvailable = { info ->
                handleConnectionEstablished(info)
            },
            onThisDeviceChanged = { device ->
                Logger.i(TAG, "Local device status: name='${device.deviceName}', status=${device.status}")
                if (_connectedPeerName.value == null && device.deviceName.isNotBlank()) {
                    _diagnostics.update { it.copy(localIpAddress = _localIpAddress.value) }
                }
            },
            onDisconnected = {
                handleDisconnected()
            }
        )

        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        ContextCompat.registerReceiver(
            context,
            broadcastReceiver,
            intentFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiver = broadcastReceiver
        isReceiverRegistered = true
        Logger.i(TAG, "WifiDirectBroadcastReceiver registered.")
    }

    private fun unregisterBroadcastReceiver() {
        if (isReceiverRegistered && receiver != null) {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                Logger.w(TAG, "Receiver was already unregistered: ${e.message}")
            }
            receiver = null
            isReceiverRegistered = false
            Logger.i(TAG, "WifiDirectBroadcastReceiver unregistered.")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Core TransportEngine Interface Implementation
    // ---------------------------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    override suspend fun startDiscovery() = withContext(ioDispatcher) {
        registerBroadcastReceiver()

        if (!WifiDirectPermissionHelper.hasRequiredPermissions(context)) {
            val err = "Missing Wi-Fi Direct permissions. Required: ${WifiDirectPermissionHelper.getRequiredPermissions().joinToString()}"
            Logger.e(TAG, err)
            _lastError.value = err
            _p2pStatus.value = P2pStatus.ERROR
            return@withContext
        }

        val mgr = p2pManager
        val ch = channel
        if (mgr == null || ch == null) {
            val err = "WifiP2pManager or Channel is null"
            Logger.e(TAG, err)
            _lastError.value = err
            _p2pStatus.value = P2pStatus.ERROR
            return@withContext
        }

        _p2pStatus.value = P2pStatus.DISCOVERING
        _lastError.value = null
        Logger.i(TAG, "Starting Wi-Fi Direct peer discovery...")

        mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Logger.i(TAG, "discoverPeers initiated successfully.")
            }

            override fun onFailure(reasonCode: Int) {
                val reason = reasonCodeToString(reasonCode)
                Logger.e(TAG, "discoverPeers failed: $reason (code $reasonCode)")
                _p2pStatus.value = P2pStatus.ERROR
                _lastError.value = "Discovery failed: $reason"
            }
        })
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(peer: Peer) = withContext(ioDispatcher) {
        registerBroadcastReceiver()

        if (!WifiDirectPermissionHelper.hasRequiredPermissions(context)) {
            val err = "Cannot connect: Missing Wi-Fi Direct permissions."
            Logger.e(TAG, err)
            _lastError.value = err
            _p2pStatus.value = P2pStatus.ERROR
            return@withContext
        }

        val mgr = p2pManager
        val ch = channel
        if (mgr == null || ch == null) {
            val err = "Cannot connect: WifiP2pManager or Channel is null"
            Logger.e(TAG, err)
            _lastError.value = err
            _p2pStatus.value = P2pStatus.ERROR
            return@withContext
        }

        val config = WifiP2pConfig().apply {
            deviceAddress = peer.deviceAddress
            wps.setup = WpsInfo.PBC
        }

        _p2pStatus.value = P2pStatus.CONNECTING
        _connectedPeerName.value = peer.deviceName
        _connectedPeerAddress.value = peer.deviceAddress
        _lastError.value = null
        Logger.i(TAG, "Initiating P2P connection to '${peer.deviceName}' (${peer.deviceAddress})...")

        mgr.connect(ch, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Logger.i(TAG, "P2P connection initiation succeeded for ${peer.deviceName}")
            }

            override fun onFailure(reasonCode: Int) {
                val reason = reasonCodeToString(reasonCode)
                Logger.e(TAG, "P2P connection initiation failed: $reason (code $reasonCode)")
                _p2pStatus.value = P2pStatus.ERROR
                _lastError.value = "Connection failed: $reason"
            }
        })
    }

    override suspend fun send(message: TransportMessage) {
        tcpSocketManager.send(message)
    }

    override fun incomingMessages(): Flow<TransportMessage> {
        return tcpSocketManager.incomingMessages
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect() = withContext(ioDispatcher) {
        Logger.i(TAG, "Disconnecting Wi-Fi Direct and TCP sockets...")

        // Disconnect TCP sockets
        tcpSocketManager.disconnect()

        // Disconnect P2P group if active
        val mgr = p2pManager
        val ch = channel
        if (mgr != null && ch != null) {
            mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Logger.i(TAG, "removeGroup succeeded.")
                }

                override fun onFailure(reasonCode: Int) {
                    Logger.d(TAG, "removeGroup failed or no group to remove: code $reasonCode")
                }
            })

            mgr.stopPeerDiscovery(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Logger.i(TAG, "stopPeerDiscovery succeeded.")
                }

                override fun onFailure(reasonCode: Int) {
                    Logger.d(TAG, "stopPeerDiscovery failed: code $reasonCode")
                }
            })
        }

        _p2pStatus.value = P2pStatus.DISCONNECTED
        _connectedPeerName.value = null
        _connectedPeerAddress.value = null
        _localIpAddress.value = null
        _remoteIpAddress.value = null
        _isGroupOwner.value = false
        _lastError.value = null
        Logger.i(TAG, "Wi-Fi Direct disconnected cleanly.")
    }

    // ---------------------------------------------------------------------------------------------
    // P2P Event Handling
    // ---------------------------------------------------------------------------------------------

    private fun handlePeersDiscovered(deviceList: WifiP2pDeviceList) {
        val mappedPeers = deviceList.deviceList.map { dev ->
            Peer(
                deviceAddress = dev.deviceAddress ?: "00:00:00:00:00:00",
                deviceName = if (dev.deviceName.isNullOrBlank()) "Peer [${dev.deviceAddress.takeLast(5)}]" else dev.deviceName,
                status = PeerStatus.fromCode(dev.status),
                isGroupOwner = dev.isGroupOwner
            )
        }
        _peers.value = mappedPeers
        Logger.i(TAG, "Discovered ${mappedPeers.size} peer(s): ${mappedPeers.joinToString { it.deviceName }}")
    }

    private fun handleConnectionEstablished(info: WifiP2pInfo) {
        if (!info.groupFormed) {
            Logger.i(TAG, "Connection info received but groupFormed is false.")
            return
        }

        val isGo = info.isGroupOwner
        val goAddress = info.groupOwnerAddress?.hostAddress ?: DEFAULT_GO_IP
        _isGroupOwner.value = isGo

        Logger.i(TAG, "Wi-Fi Direct group formed: isGroupOwner=$isGo, GroupOwnerIP=$goAddress")

        scope.launch(ioDispatcher) {
            if (isGo) {
                _p2pStatus.value = P2pStatus.CONNECTED_AS_GO
                _localIpAddress.value = goAddress
                _remoteIpAddress.value = "Client(s)"
                Logger.i(TAG, "Starting ServerSocket as Group Owner on port $TCP_PORT...")
                tcpSocketManager.startServer(TCP_PORT)
            } else {
                _p2pStatus.value = P2pStatus.CONNECTED_AS_CLIENT
                _localIpAddress.value = "Client"
                _remoteIpAddress.value = "$goAddress:$TCP_PORT"
                Logger.i(TAG, "Connecting to Group Owner at $goAddress:$TCP_PORT...")
                tcpSocketManager.connectToServer(hostAddress = goAddress, targetPort = TCP_PORT)
            }
        }
    }

    private fun handleDisconnected() {
        Logger.i(TAG, "Wi-Fi Direct network disconnected callback triggered.")
        _p2pStatus.value = P2pStatus.DISCONNECTED
        _connectedPeerName.value = null
        _connectedPeerAddress.value = null
        _localIpAddress.value = null
        _remoteIpAddress.value = null
        _isGroupOwner.value = false

        scope.launch(ioDispatcher) {
            tcpSocketManager.disconnect()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Diagnostics & Status Combine
    // ---------------------------------------------------------------------------------------------

    private fun startDiagnosticsObservation() {
        scope.launch(ioDispatcher) {
            combine(
                combine(_p2pStatus, tcpSocketManager.tcpStatus, _connectedPeerName, _connectedPeerAddress, _isGroupOwner) { p2p, tcp, pName, pAddr, isGo ->
                    DiagnosticsTuple1(p2p, tcp, pName, pAddr, isGo)
                },
                combine(_localIpAddress, _remoteIpAddress, tcpSocketManager.bytesSent, tcpSocketManager.bytesReceived) { lIp, rIp, sent, recv ->
                    DiagnosticsTuple2(lIp, rIp, sent, recv)
                },
                combine(tcpSocketManager.lastMessage, tcpSocketManager.roundTripLatencyMs, _lastError) { lastMsg, rtt, err ->
                    DiagnosticsTuple3(lastMsg, rtt, err)
                }
            ) { t1, t2, t3 ->
                TransportDiagnostics(
                    p2pStatus = t1.p2p,
                    tcpStatus = t1.tcp,
                    connectedPeerName = t1.pName,
                    connectedPeerAddress = t1.pAddr,
                    isGroupOwner = t1.isGo,
                    localIpAddress = t2.lIp,
                    remoteIpAddress = t2.rIp,
                    bytesSent = t2.sent,
                    bytesReceived = t2.recv,
                    lastMessage = t3.lastMsg,
                    roundTripLatencyMs = t3.rtt,
                    lastError = t3.err
                )
            }.collect { diag ->
                _diagnostics.value = diag

                // Update legacy ConnectionStatus for backward compatibility with existing UI
                _connectionStatus.value = when {
                    diag.tcpStatus == TcpStatus.CONNECTED -> ConnectionStatus.CONNECTED
                    diag.p2pStatus == P2pStatus.DISCOVERING ||
                    diag.p2pStatus == P2pStatus.CONNECTING ||
                    diag.p2pStatus == P2pStatus.CONNECTED_AS_GO ||
                    diag.p2pStatus == P2pStatus.CONNECTED_AS_CLIENT ||
                    diag.tcpStatus == TcpStatus.CONNECTING ||
                    diag.tcpStatus == TcpStatus.RECONNECTING ||
                    diag.tcpStatus == TcpStatus.LISTENING -> ConnectionStatus.SEARCHING
                    else -> ConnectionStatus.DISCONNECTED
                }
            }
        }
    }

    private fun reasonCodeToString(reason: Int): String = when (reason) {
        WifiP2pManager.P2P_UNSUPPORTED -> "P2P_UNSUPPORTED"
        WifiP2pManager.ERROR -> "ERROR"
        WifiP2pManager.BUSY -> "BUSY"
        else -> "UNKNOWN_REASON_$reason"
    }

    private data class DiagnosticsTuple1(
        val p2p: P2pStatus,
        val tcp: TcpStatus,
        val pName: String?,
        val pAddr: String?,
        val isGo: Boolean
    )

    private data class DiagnosticsTuple2(
        val lIp: String?,
        val rIp: String?,
        val sent: Long,
        val recv: Long
    )

    private data class DiagnosticsTuple3(
        val lastMsg: TransportMessage?,
        val rtt: Long?,
        val err: String?
    )
}
