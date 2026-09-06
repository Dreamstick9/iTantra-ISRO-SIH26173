package org.isro.itantra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.isro.itantra.telemetry.TelemetryStats
import org.isro.itantra.telemetry.TransceiverMode

@Composable
fun TelemetryCard(
    stats: TelemetryStats,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0B132B), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        // --- HEADER: TITLE & ENGINE BADGE ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF38BDF8), CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "iTantra TELEMETRY HUD",
                    color = Color(0xFF38BDF8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Box(
                modifier = Modifier
                    .background(Color(0xFF1E293B), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (stats.activeMode == TransceiverMode.TRANSMITTER) "M1 • PTT AUDIO I/O" else "M2 • TTS ENGINE",
                    color = if (stats.activeMode == TransceiverMode.TRANSMITTER) Color(0xFF10B981) else Color(0xFFA78BFA),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // --- EMERGENCY ALERT OVERRIDE BANNER ---
        if (stats.isAlertPriority) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x33EF4444), RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ALERT PRIORITY: NON-INTERRUPTIBLE MAX VOL",
                    color = Color(0xFFFCA5A5),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (stats.activeMode == TransceiverMode.TRANSMITTER) {
            // =========================================================
            // MILESTONE 1 PTT & AUDIO I/O TELEMETRY HUD
            // =========================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val micLagVal = if (stats.micStartLagNs > 0) String.format("%.2f ms", stats.micStartLagMs) else "--"
                MetricBox(
                    label = "MIC START LAG",
                    value = micLagVal,
                    statusText = if (stats.micStartLagMs in 0.01..50.0) "< 50ms PASS" else "URGENT AUDIO",
                    valueColor = if (stats.micStartLagMs in 0.01..50.0) Color(0xFF4ADE80) else Color(0xFFF1F5F9),
                    highlight = stats.micStartLagMs in 0.01..50.0,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                val turnaroundVal = if (stats.turnaroundLagNs > 0) String.format("%.2f ms", stats.turnaroundLagMs) else "--"
                MetricBox(
                    label = "TURNAROUND LAG",
                    value = turnaroundVal,
                    statusText = "DAC PLAYBACK",
                    valueColor = Color(0xFF38BDF8),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                val durationText = if (stats.recordedDurationMs > 0) {
                    String.format("%.2f s", stats.recordedDurationSec)
                } else "--"
                MetricBox(
                    label = "DURATION",
                    value = durationText,
                    statusText = "${stats.sampleRate / 1000}kHz MONO",
                    valueColor = Color(0xFFFBBF24),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val memoryText = if (stats.memoryBufferSizeBytes > 0) {
                    String.format("%.1f KiB", stats.memoryBufferSizeKb)
                } else "--"
                MetricBox(
                    label = "BUFFER MEMORY",
                    value = memoryText,
                    statusText = "${stats.recordedBytes} BYTES",
                    valueColor = Color(0xFFA78BFA),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                MetricBox(
                    label = "BITRATE (BASELINE)",
                    value = "${stats.bitrateKbps} kbps",
                    statusText = "RAW UNCOMPRESSED",
                    valueColor = Color(0xFF38BDF8),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                MetricBox(
                    label = "ACOUSTIC SPEC",
                    value = "16-BIT PCM",
                    statusText = "NEURAL COMPLIANT",
                    valueColor = Color(0xFF4ADE80),
                    highlight = true,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            // =========================================================
            // MILESTONE 2 NEURAL TTS TELEMETRY HUD
            // =========================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricBox(
                    label = "T_SYNTH (COMPUTE)",
                    value = if (stats.ttsSynthesisLatencyMs > 0) "${stats.ttsSynthesisLatencyMs} ms" else "--",
                    statusText = if (stats.ttsSynthesisLatencyMs in 1..250) "FAST" else "NORMAL",
                    valueColor = if (stats.ttsSynthesisLatencyMs in 1..250) Color(0xFF4ADE80) else Color(0xFFF1F5F9),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                MetricBox(
                    label = "T_AUDIO (SPEECH)",
                    value = if (stats.ttsAudioDurationMs > 0) "${String.format("%.2f", stats.ttsAudioDurationMs / 1000f)} s" else "--",
                    statusText = "${stats.sampleRate / 1000}kHz PCM",
                    valueColor = Color(0xFF38BDF8),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                val rtf = stats.ttsRealTimeFactor
                val rtfColor = when {
                    rtf == 0.0f -> Color(0xFF64748B)
                    rtf <= 0.35f -> Color(0xFF4ADE80)
                    rtf <= 1.0f -> Color(0xFFFBBF24)
                    else -> Color(0xFFEF4444)
                }
                val speedText = if (stats.realTimeSpeedMultiplier > 0f) {
                    "${String.format("%.1f", stats.realTimeSpeedMultiplier)}x RT"
                } else "--"

                MetricBox(
                    label = "RTF (SPEED)",
                    value = if (rtf > 0) String.format("%.3f", rtf) else "--",
                    statusText = speedText,
                    valueColor = rtfColor,
                    highlight = stats.isRealTimeCapable,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val e2e = stats.ttsTapToFirstAudioMs
                val e2eColor = when {
                    e2e == 0L -> Color(0xFF64748B)
                    e2e <= 300L -> Color(0xFF4ADE80)
                    e2e <= 500L -> Color(0xFFFBBF24)
                    else -> Color(0xFFEF4444)
                }

                MetricBox(
                    label = "TAP-TO-EAR (E2E)",
                    value = if (e2e > 0) "$e2e ms" else "--",
                    statusText = if (stats.isE2eCompliant) "ITU-T PASS" else "STANDBY",
                    valueColor = e2eColor,
                    highlight = stats.isE2eCompliant,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                MetricBox(
                    label = "MIC LAG (M1)",
                    value = if (stats.micStartLagNs > 0) String.format("%.2f ms", stats.micStartLagMs) else "--",
                    statusText = "HARDWARE HAL",
                    valueColor = Color(0xFFF1F5F9),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                MetricBox(
                    label = "MODEL ENGINE",
                    value = stats.ttsModelName,
                    statusText = "${stats.ttsLanguage.uppercase()} / 100% OFFLINE",
                    valueColor = Color(0xFFA78BFA),
                    modifier = Modifier.weight(1f)
                )
            }

            // Latency waterfall breakdown for TTS
            if (stats.ttsTapToFirstAudioMs > 0L) {
                Spacer(modifier = Modifier.height(10.dp))
                LatencyWaterfallBar(
                    e2eMs = stats.ttsTapToFirstAudioMs,
                    synthMs = stats.ttsSynthesisLatencyMs
                )
            }
        }
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    statusText: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Column(
        modifier = modifier
            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
            .border(1.dp, if (highlight) valueColor.copy(alpha = 0.5f) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFF64748B),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = statusText,
            color = if (highlight) valueColor else Color(0xFF94A3B8),
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
private fun LatencyWaterfallBar(
    e2eMs: Long,
    synthMs: Long
) {
    val prepMs = maxOf(0L, (e2eMs - synthMs) / 2)
    val dacMs = maxOf(0L, e2eMs - synthMs - prepMs)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF070D1E), RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "⏱ PIPELINE LATENCY WATERFALL",
                color = Color(0xFF64748B),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "TOTAL: $e2eMs ms",
                color = Color(0xFF38BDF8),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        ) {
            Box(
                modifier = Modifier
                    .weight(maxOf(0.01f, prepMs.toFloat()))
                    .background(Color(0xFFF59E0B))
            )
            Box(
                modifier = Modifier
                    .weight(maxOf(0.01f, synthMs.toFloat()))
                    .background(Color(0xFF10B981))
            )
            Box(
                modifier = Modifier
                    .weight(maxOf(0.01f, dacMs.toFloat()))
                    .background(Color(0xFF38BDF8))
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Norm: ${prepMs}ms", color = Color(0xFFF59E0B), fontSize = 7.5.sp, fontFamily = FontFamily.Monospace)
            Text("Neural Synth: ${synthMs}ms", color = Color(0xFF10B981), fontSize = 7.5.sp, fontFamily = FontFamily.Monospace)
            Text("AudioTrack: ${dacMs}ms", color = Color(0xFF38BDF8), fontSize = 7.5.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
