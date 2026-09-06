package com.example.itantra.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.itantra.data.LatencyMetrics

@Composable
fun LatencyStatsCard(
    metrics: LatencyMetrics?,
    modifier: Modifier = Modifier
) {
    val m = metrics ?: LatencyMetrics()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TELEMETRY & LATENCY HUD (20% EVAL)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "RTF: %.2f".format(m.realTimeFactor),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (m.realTimeFactor < 1.0) Color(0xFF00E676) else Color(0xFFFFB300),
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TelemetryMetricItem(title = "STT Delay", value = "${m.sttLatencyMs} ms")
                TelemetryMetricItem(title = "Radio Link", value = "${m.transmissionLatencyMs} ms")
                TelemetryMetricItem(title = "TTS Delay", value = "${m.ttsLatencyMs} ms")
                TelemetryMetricItem(title = "Total E2E", value = "${m.endToEndLatencyMs} ms", isHighlight = true)
            }
        }
    }
}

@Composable
fun TelemetryMetricItem(
    title: String,
    value: String,
    isHighlight: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isHighlight) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (isHighlight) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurface
        )
    }
}
