package com.itantra.voice.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itantra.voice.ui.LatencyStats

/**
 * Per-stage latency readout.
 *
 * SIH26173 scores latency at 20%, so the numbers stay on screen rather than living in a
 * debug menu — but they appear only once there is a measurement, to keep the resting
 * screen quiet.
 */
@Composable
fun LatencyStrip(latencies: LatencyStats, modifier: Modifier = Modifier) {
    val hasData = latencies.totalMs > 0

    AnimatedVisibility(visible = hasData) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Metric("STT", latencies.sttMs)
            Metric("TRANS", latencies.translateMs)
            Metric("TTS", latencies.ttsMs)
            latencies.netMs?.let { Metric("LINK", it) }
            Metric("TOTAL", latencies.totalMs, emphasised = true)
        }
    }
}

@Composable
private fun Metric(label: String, valueMs: Long, emphasised: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${valueMs}ms",
            style = MaterialTheme.typography.labelLarge,
            color = if (emphasised) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
