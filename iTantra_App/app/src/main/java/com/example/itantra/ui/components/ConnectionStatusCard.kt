package com.example.itantra.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.itantra.data.ConnectionStatus
import com.example.itantra.data.TransportType

@Composable
fun ConnectionStatusCard(
    status: ConnectionStatus,
    transportType: TransportType,
    deviceName: String?,
    deviceAddress: String?,
    onConnectOrScanClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val icon = when (transportType) {
                        TransportType.WIFI_DIRECT, TransportType.WIFI_AWARE -> Icons.Default.Wifi
                        TransportType.BLUETOOTH_RFCOMM, TransportType.BLE_GATT -> Icons.Default.Bluetooth
                        else -> Icons.Default.WifiOff
                    }
                    val iconTint = when (status) {
                        ConnectionStatus.CONNECTED -> Color(0xFF00E676)
                        ConnectionStatus.SEARCHING -> Color(0xFFFFB300)
                        ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.outline
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Transport Icon",
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = transportType.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                ConnectionStateBadge(status = status)
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (status == ConnectionStatus.CONNECTED) {
                    Text(
                        text = deviceName ?: "Unknown Peer Transceiver",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Endpoint: ${deviceAddress ?: "Ad-hoc Direct"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = when (status) {
                            ConnectionStatus.SEARCHING -> "Searching for nearby iTantra neural nodes..."
                            ConnectionStatus.DISCONNECTED -> "No active peer connection"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (status == ConnectionStatus.CONNECTED) {
                    OutlinedButton(
                        onClick = onDisconnectClick,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Disconnect")
                    }
                } else {
                    Button(
                        onClick = onConnectOrScanClick,
                        enabled = status != ConnectionStatus.SEARCHING
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (status == ConnectionStatus.SEARCHING) "Searching..." else "Scan & Connect"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionStateBadge(status: ConnectionStatus) {
    val (color, label) = when (status) {
        ConnectionStatus.CONNECTED -> Color(0xFF00E676) to "CONNECTED"
        ConnectionStatus.SEARCHING -> Color(0xFFFFB300) to "SEARCHING"
        ConnectionStatus.DISCONNECTED -> Color(0xFF757575) to "OFFLINE"
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
