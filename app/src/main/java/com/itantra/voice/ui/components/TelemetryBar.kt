package com.itantra.voice.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.voice.ui.LatencyStats

@Composable
fun TelemetryBar(
    latencies: LatencyStats,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TelemetryMetric(label = "STT", valueMs = latencies.sttMs)
            Text(text = "|", color = MaterialTheme.colorScheme.outlineVariant)
            TelemetryMetric(label = "Trans", valueMs = latencies.translateMs)
            Text(text = "|", color = MaterialTheme.colorScheme.outlineVariant)
            TelemetryMetric(label = "TTS", valueMs = latencies.ttsMs)
            Text(text = "|", color = MaterialTheme.colorScheme.outlineVariant)
            TelemetryMetric(label = "Total", valueMs = latencies.totalMs, isTotal = true)
        }
    }
}

@Composable
private fun TelemetryMetric(
    label: String,
    valueMs: Long,
    isTotal: Boolean = false,
    modifier: Modifier = Modifier
) {
    val displayValue = if (valueMs > 0) "${valueMs}ms" else "--ms"
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelSmall,
            color = if (isTotal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = displayValue,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = if (isTotal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp
        )
    }
}
