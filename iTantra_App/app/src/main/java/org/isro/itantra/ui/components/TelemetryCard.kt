package org.isro.itantra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.isro.itantra.telemetry.TelemetryStats

@Composable
fun TelemetryCard(
    stats: TelemetryStats,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⚡ SIH26173 TELEMETRY HUD",
                color = Color(0xFF38BDF8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "ISRO COMPLIANT",
                color = Color(0xFF4ADE80),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MetricItem(
                label = "MIC START LAG",
                value = if (stats.pressToFirstBufferMs > 0) "${stats.pressToFirstBufferMs} ms" else "--",
                highlight = stats.pressToFirstBufferMs in 1..45
            )
            MetricItem(
                label = "PLAYBACK LAG",
                value = if (stats.releaseToPlaybackStartMs > 0) "${stats.releaseToPlaybackStartMs} ms" else "--",
                highlight = stats.releaseToPlaybackStartMs in 1..40
            )
            MetricItem(
                label = "DURATION",
                value = if (stats.recordedDurationMs > 0) "${stats.recordedDurationMs / 1000f} s" else "--",
                highlight = false
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MetricItem(
                label = "FORMAT",
                value = "16kHz Mono",
                highlight = false
            )
            MetricItem(
                label = "BUFFER FRAME",
                value = "20ms (640B)",
                highlight = false
            )
            MetricItem(
                label = "RAW BITRATE",
                value = "${stats.bitrateKbps} kbps",
                highlight = false
            )
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    highlight: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = Color(0xFF64748B),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = if (highlight) Color(0xFF4ADE80) else Color(0xFFF1F5F9),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
