package com.itantra.voice.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.voice.transport.DiscoveredPeer
import com.itantra.voice.transport.TransportConnectionState

/**
 * Compact P2P connection bar rendered on the main screen.
 * Displays real-time connection state (Disconnected, Discovering, Connecting, Connected)
 * and opens a lightweight peer selector without cluttering the screen.
 */
@Composable
fun P2pConnectionBar(
    state: TransportConnectionState,
    connectedPeer: DiscoveredPeer?,
    discoveredPeers: List<DiscoveredPeer>,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onConnectPeer: (DiscoveredPeer) -> Unit,
    onDisconnect: () -> Unit,
    onSendTestMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPeerDialog by remember { mutableStateOf(false) }

    val dotColor by animateColorAsState(
        targetValue = when (state) {
            TransportConnectionState.CONNECTED -> Color(0xFF2E7D32) // Green
            TransportConnectionState.CONNECTING -> Color(0xFF0288D1) // Blue
            TransportConnectionState.DISCOVERING -> Color(0xFFF57C00) // Amber
            TransportConnectionState.DISCONNECTING -> Color(0xFF757575)
            TransportConnectionState.ERROR -> MaterialTheme.colorScheme.error
            TransportConnectionState.DISCONNECTED -> Color(0xFF9E9E9E) // Gray
        },
        label = "p2pDotColor"
    )

    val statusText = when (state) {
        TransportConnectionState.CONNECTED -> "CONNECTED: Wi-Fi Direct"
        TransportConnectionState.CONNECTING -> "Connecting..."
        TransportConnectionState.DISCOVERING -> "Discovering peers..."
        TransportConnectionState.DISCONNECTING -> "Disconnecting..."
        TransportConnectionState.ERROR -> "P2P Error"
        TransportConnectionState.DISCONNECTED -> "Disconnected"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Dot + Text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    if (state == TransportConnectionState.DISCONNECTED) {
                        onStartDiscovery()
                        showPeerDialog = true
                    } else if (state == TransportConnectionState.DISCOVERING) {
                        showPeerDialog = true
                    }
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (state == TransportConnectionState.CONNECTED) FontWeight.Bold else FontWeight.Medium,
                        color = if (state == TransportConnectionState.CONNECTED) dotColor else MaterialTheme.colorScheme.onSurface
                    )
                    if (connectedPeer != null && state == TransportConnectionState.CONNECTED) {
                        Text(
                            text = "Peer: ${connectedPeer.name}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Compact Action Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (state) {
                    TransportConnectionState.CONNECTED -> {
                        // Gate 6 test button
                        TextButton(
                            onClick = onSendTestMessage,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Test",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        TextButton(
                            onClick = onDisconnect,
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Disconnect", fontSize = 11.sp)
                        }
                    }

                    TransportConnectionState.CONNECTING, TransportConnectionState.DISCONNECTING -> {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }

                    TransportConnectionState.DISCOVERING -> {
                        TextButton(
                            onClick = { showPeerDialog = true },
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("View (${discoveredPeers.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    else -> {
                        // DISCONNECTED or ERROR
                        OutlinedButton(
                            onClick = {
                                onStartDiscovery()
                                showPeerDialog = true
                            },
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = "Connect Device",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Connect Device", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    // Peer Selection Dialog
    if (showPeerDialog) {
        AlertDialog(
            onDismissRequest = {
                showPeerDialog = false
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Wi-Fi Direct Peers", style = MaterialTheme.typography.titleMedium)
                    if (state == TransportConnectionState.DISCOVERING) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (state == TransportConnectionState.DISCOVERING)
                            "Scanning for nearby iTantra devices..."
                        else
                            "Select a device to connect:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (discoveredPeers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No nearby iTantra peers found yet.\nEnsure Wi-Fi is enabled on both phones.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                            items(discoveredPeers) { peer ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = peer.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = peer.deviceAddress,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                onConnectPeer(peer)
                                                showPeerDialog = false
                                            },
                                            modifier = Modifier.height(30.dp),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("Connect", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (state == TransportConnectionState.DISCOVERING) {
                            onStopDiscovery()
                        } else {
                            onStartDiscovery()
                        }
                    }
                ) {
                    Text(if (state == TransportConnectionState.DISCOVERING) "Stop Scan" else "Rescan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPeerDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
