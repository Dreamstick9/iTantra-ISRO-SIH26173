package com.example.itantra.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.itantra.transport.*

/**
 * Diagnostic HUD and control panel for Wi-Fi Direct phone-to-phone text transport.
 *
 * Displays:
 * - P2P connection status & role (Group Owner vs Client)
 * - Persistent TCP socket status on port 8988
 * - Connected peer metadata (Device Name, MAC address, IP endpoint)
 * - Real-time wire telemetry: Bytes Sent, Bytes Received, Round-trip Latency (ms)
 * - Last transmitted/received message inspection
 * - Discovered peer list with direct "Connect" trigger
 * - Transmission controls: "HELLO FROM PHONE A", custom text framing, emergency alert
 */
@Composable
fun WifiDirectDiagnosticCard(
    diagnostics: TransportDiagnostics,
    peers: List<Peer>,
    onStartDiscovery: () -> Unit,
    onConnectPeer: (Peer) -> Unit,
    onDisconnect: () -> Unit,
    onSendTextMessage: (String) -> Unit,
    onSendAlertMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var customText by remember { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(true) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // -------------------------------------------------------------------------
            // Header: Title, Badges & Expand/Collapse
            // -------------------------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (diagnostics.tcpStatus == TcpStatus.CONNECTED) Color(0xFF00E676).copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = if (diagnostics.tcpStatus == TcpStatus.CONNECTED) Color(0xFF00E676)
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Wi-Fi Direct P2P HUD",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tactical Offline Transport",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    P2pStatusChip(status = diagnostics.p2pStatus)
                    TcpStatusChip(status = diagnostics.tcpStatus)
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // -------------------------------------------------------------------------
                    // Connection Role & Peer Endpoint Metadata
                    // -------------------------------------------------------------------------
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ROLE: ${
                                        when {
                                            diagnostics.p2pStatus == P2pStatus.CONNECTED_AS_GO || diagnostics.isGroupOwner -> "GROUP OWNER (SERVER)"
                                            diagnostics.p2pStatus == P2pStatus.CONNECTED_AS_CLIENT -> "CLIENT (CONNECTED TO GO)"
                                            else -> "UNASSIGNED"
                                        }
                                    }",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (diagnostics.isGroupOwner) Color(0xFF80D8FF) else Color(0xFFFFD54F),
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "PORT: 8988",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (diagnostics.connectedPeerName != null || diagnostics.connectedPeerAddress != null) {
                                Text(
                                    text = "Peer: ${diagnostics.connectedPeerName ?: "Direct Peer"} (${diagnostics.connectedPeerAddress ?: "Unknown MAC"})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (diagnostics.localIpAddress != null || diagnostics.remoteIpAddress != null) {
                                Text(
                                    text = "IP: [Local: ${diagnostics.localIpAddress ?: "--"}] ➔ [Remote: ${diagnostics.remoteIpAddress ?: "--"}]",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // -------------------------------------------------------------------------
                    // Real-Time Wire Telemetry Grid (Bytes Sent, Received, Latency, Last Msg)
                    // -------------------------------------------------------------------------
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            label = "BYTES SENT",
                            value = formatBytes(diagnostics.bytesSent),
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "BYTES RECEIVED",
                            value = formatBytes(diagnostics.bytesReceived),
                            modifier = Modifier.weight(1f)
                        )
                        LatencyMetricCard(
                            latencyMs = diagnostics.roundTripLatencyMs,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Last Message Preview Box
                    if (diagnostics.lastMessage != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MarkChatRead,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Last Msg: [${diagnostics.lastMessage.messageType}] \"${diagnostics.lastMessage.text.take(35)}${if (diagnostics.lastMessage.text.length > 35) "..." else ""}\"",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Error Banner if present
                    if (diagnostics.lastError != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "⚠ ${diagnostics.lastError}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    // -------------------------------------------------------------------------
                    // Peer Discovery & Connection Section
                    // -------------------------------------------------------------------------
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Discovered Peers (${peers.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Button(
                                onClick = onStartDiscovery,
                                enabled = diagnostics.p2pStatus != P2pStatus.DISCOVERING,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                if (diagnostics.p2pStatus == P2pStatus.DISCOVERING) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Scanning...", fontSize = 12.sp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Scan Peers", fontSize = 12.sp)
                                }
                            }
                        }

                        if (peers.isEmpty()) {
                            Text(
                                text = if (diagnostics.p2pStatus == P2pStatus.DISCOVERING)
                                    "Scanning 2.4/5GHz Wi-Fi Direct tactical channels..."
                                else
                                    "No nearby Wi-Fi Direct devices discovered yet. Tap 'Scan Peers'.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                peers.forEach { peer ->
                                    PeerRow(
                                        peer = peer,
                                        isCurrentlyConnected = diagnostics.connectedPeerAddress == peer.deviceAddress && diagnostics.tcpStatus == TcpStatus.CONNECTED,
                                        onConnect = { onConnectPeer(peer) }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // -------------------------------------------------------------------------
                    // Transmission Testing Controls (DoD: Phone A -> Phone B "HELLO FROM PHONE A")
                    // -------------------------------------------------------------------------
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Transmission Verification Controls",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Quick Test Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onSendTextMessage("HELLO FROM PHONE A") },
                                enabled = diagnostics.tcpStatus == TcpStatus.CONNECTED,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E88E5)
                                )
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Send \"HELLO FROM PHONE A\"", fontSize = 11.sp, maxLines = 1)
                            }

                            FilledTonalButton(
                                onClick = { onSendAlertMessage("CYCLONE ADVISORY: Evacuate coastal lowlands") },
                                enabled = diagnostics.tcpStatus == TcpStatus.CONNECTED,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Alert", fontSize = 11.sp)
                            }
                        }

                        // Custom Text Input
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customText,
                                onValueChange = { customText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Enter custom message...", fontSize = 12.sp) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall
                            )

                            Button(
                                onClick = {
                                    if (customText.isNotBlank()) {
                                        onSendTextMessage(customText.trim())
                                        customText = ""
                                    }
                                },
                                enabled = diagnostics.tcpStatus == TcpStatus.CONNECTED && customText.isNotBlank()
                            ) {
                                Text("Send", fontSize = 12.sp)
                            }
                        }

                        // Disconnect Action
                        if (diagnostics.p2pStatus != P2pStatus.OFFLINE && diagnostics.p2pStatus != P2pStatus.DISCONNECTED) {
                            OutlinedButton(
                                onClick = onDisconnect,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Disconnect Wi-Fi Direct & TCP Link")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// UI Helper Components
// ---------------------------------------------------------------------------------------------

@Composable
private fun PeerRow(
    peer: Peer,
    isCurrentlyConnected: Boolean,
    onConnect: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.deviceName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${peer.deviceAddress} • ${peer.status.name}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isCurrentlyConnected) {
                Surface(
                    color = Color(0xFF00E676).copy(alpha = 0.15f),
                    contentColor = Color(0xFF00E676),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "CONNECTED",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onConnect,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Connect", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun P2pStatusChip(status: P2pStatus) {
    val (color, label) = when (status) {
        P2pStatus.CONNECTED_AS_GO -> Color(0xFF00E676) to "P2P: GO"
        P2pStatus.CONNECTED_AS_CLIENT -> Color(0xFF00E676) to "P2P: CLIENT"
        P2pStatus.CONNECTING -> Color(0xFFFFB300) to "P2P: CONNECTING"
        P2pStatus.DISCOVERING -> Color(0xFF29B6F6) to "P2P: SCANNING"
        P2pStatus.INITIALIZING -> Color(0xFFB0BEC5) to "P2P: INIT"
        P2pStatus.ERROR -> Color(0xFFFF5252) to "P2P: ERROR"
        P2pStatus.DISCONNECTED, P2pStatus.OFFLINE -> Color(0xFF757575) to "P2P: OFFLINE"
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun TcpStatusChip(status: TcpStatus) {
    val (color, label) = when (status) {
        TcpStatus.CONNECTED -> Color(0xFF00E676) to "TCP: ONLINE"
        TcpStatus.LISTENING -> Color(0xFF80D8FF) to "TCP: LISTEN"
        TcpStatus.CONNECTING, TcpStatus.RECONNECTING -> Color(0xFFFFB300) to "TCP: RETRY"
        TcpStatus.ERROR -> Color(0xFFFF5252) to "TCP: ERROR"
        TcpStatus.DISCONNECTED -> Color(0xFF757575) to "TCP: OFF"
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun LatencyMetricCard(
    latencyMs: Long?,
    modifier: Modifier = Modifier
) {
    val (color, display) = when {
        latencyMs == null -> MaterialTheme.colorScheme.onSurfaceVariant to "-- ms"
        latencyMs < 50L -> Color(0xFF00E676) to "${latencyMs} ms"
        latencyMs < 150L -> Color(0xFFFFB300) to "${latencyMs} ms"
        else -> Color(0xFFFF5252) to "${latencyMs} ms"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "RTT LATENCY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = display,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format("%.1f KB", bytes / 1024.0)
        else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
    }
}
