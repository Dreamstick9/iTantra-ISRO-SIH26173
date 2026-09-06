package com.example.itantra.transport.wifidirect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import com.example.itantra.util.Logger

/**
 * BroadcastReceiver listening for Wi-Fi Direct framework state transitions:
 * - P2P enabled / disabled
 * - Discovered peer list updates
 * - Connection established / lost (Group formed)
 * - Local device status changes
 */
class WifiDirectBroadcastReceiver(
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val onWifiP2pEnabled: (Boolean) -> Unit,
    private val onPeersAvailable: (WifiP2pDeviceList) -> Unit,
    private val onConnectionInfoAvailable: (WifiP2pInfo) -> Unit,
    private val onThisDeviceChanged: (WifiP2pDevice) -> Unit,
    private val onDisconnected: () -> Unit
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "WifiDirectReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                val isEnabled = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                Logger.i(TAG, "Wi-Fi P2P state changed: isEnabled=$isEnabled")
                onWifiP2pEnabled(isEnabled)
            }

            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                Logger.d(TAG, "WIFI_P2P_PEERS_CHANGED_ACTION: requesting peers...")
                try {
                    manager.requestPeers(channel) { peers ->
                        Logger.i(TAG, "Discovered ${peers.deviceList.size} Wi-Fi Direct peer(s)")
                        onPeersAvailable(peers)
                    }
                } catch (e: SecurityException) {
                    Logger.e(TAG, "SecurityException requesting peers: ${e.message}")
                }
            }

            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                @Suppress("DEPRECATION")
                val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                Logger.i(TAG, "WIFI_P2P_CONNECTION_CHANGED_ACTION: isConnected=${networkInfo?.isConnected}")

                if (networkInfo?.isConnected == true) {
                    try {
                        manager.requestConnectionInfo(channel) { info ->
                            Logger.i(TAG, "Connection info: groupFormed=${info.groupFormed}, isGroupOwner=${info.isGroupOwner}, goAddress=${info.groupOwnerAddress?.hostAddress}")
                            onConnectionInfoAvailable(info)
                        }
                    } catch (e: SecurityException) {
                        Logger.e(TAG, "SecurityException requesting connection info: ${e.message}")
                    }
                } else {
                    Logger.i(TAG, "Wi-Fi P2P network disconnected.")
                    onDisconnected()
                }
            }

            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                @Suppress("DEPRECATION")
                val device = intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                if (device != null) {
                    Logger.i(TAG, "This device: name='${device.deviceName}', addr='${device.deviceAddress}', status=${device.status}")
                    onThisDeviceChanged(device)
                }
            }
        }
    }
}
