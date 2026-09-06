package com.example.itantra.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.itantra.data.LatencyMetrics
import com.example.itantra.data.TransceiverState
import com.example.itantra.data.VerticalSliceTimestamps
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * VerticalSliceHUD: Core Telemetry & Pipeline State Dashboard for the iTantra Vertical Slice.
 *
 * Implements:
 * 1. Active Pipeline Status Banner for 6 states:
 *    `RECORDING`, `TRANSCRIBING`, `TRANSMITTING`, `RECEIVING`, `SPEAKING`, and `IDLE`.
 *    Uses distinctive color-coding (Red, Amber, Cyan, Purple, Green, Slate).
 * 2. Latency Telemetry Card:
 *    Individual latencies (STT, Network, TTS) and cumulative End-to-End latency in ms.
 * 3. Timestamps Display:
 *    Formatted with milliseconds (HH:mm:ss.SSS) for t0..t5:
 *    - t0 = PTT release
 *    - t1 = STT complete
 *    - t2 = message transmitted
 *    - t3 = message received
 *    - t4 = TTS complete
 *    - t5 = audio playback started
 *    (Shows "--:--:--" if not yet recorded).
 */
@Composable
fun VerticalSliceHUD(
    transceiverState: TransceiverState,
    timestamps: VerticalSliceTimestamps,
    metrics: LatencyMetrics? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "HUD",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "VERTICAL SLICE TELEMETRY HUD",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.8.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "HALF-DUPLEX 16kHz",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // 1. Active Pipeline Status Banner
            ActivePipelineStatusBanner(transceiverState = transceiverState)

            // 2. Latency Telemetry Card
            LatencyTelemetrySection(
                timestamps = timestamps,
                metrics = metrics
            )

            // 3. Timestamps Display (t0..t5 in HH:mm:ss.SSS)
            TimestampsDisplaySection(timestamps = timestamps)
        }
    }
}

// ============================================================================
// 1. ACTIVE PIPELINE STATUS BANNER
// ============================================================================

@Composable
private fun ActivePipelineStatusBanner(
    transceiverState: TransceiverState
) {
    // Distinctive state colors:
    // Red (REC), Amber (STT), Cyan (TX), Purple (RX), Green (SPEAKING), Slate/Teal (IDLE)
    val stateColor = when (transceiverState) {
        TransceiverState.RECORDING -> Color(0xFFFF1744)   // Red
        TransceiverState.TRANSCRIBING -> Color(0xFFFFB300) // Amber
        TransceiverState.TRANSMITTING -> Color(0xFF00E5FF) // Cyan
        TransceiverState.RECEIVING -> Color(0xFFD500F9)    // Purple
        TransceiverState.SPEAKING -> Color(0xFF00E676)     // Green
        TransceiverState.IDLE -> Color(0xFF78909C)         // Slate / Tactical Grey
    }

    val animatedStateColor by animateColorAsState(
        targetValue = stateColor,
        animationSpec = tween(durationMillis = 200),
        label = "HUDStateColor"
    )

    val stateTitle = when (transceiverState) {
        TransceiverState.RECORDING -> "RECORDING"
        TransceiverState.TRANSCRIBING -> "TRANSCRIBING"
        TransceiverState.TRANSMITTING -> "TRANSMITTING"
        TransceiverState.RECEIVING -> "RECEIVING"
        TransceiverState.SPEAKING -> "SPEAKING"
        TransceiverState.IDLE -> "IDLE / READY"
    }

    val stateDescription = when (transceiverState) {
        TransceiverState.RECORDING -> "Capturing 16kHz mono PCM microphone audio..."
        TransceiverState.TRANSCRIBING -> "Running on-device offline STT inference..."
        TransceiverState.TRANSMITTING -> "Transmitting compressed packet over Wi-Fi Direct..."
        TransceiverState.RECEIVING -> "Inbound packet stream received from remote node..."
        TransceiverState.SPEAKING -> "Synthesizing voice & playing audio via TTS..."
        TransceiverState.IDLE -> "Transceiver standby. Half-duplex link idle."
    }

    val stateIcon: ImageVector = when (transceiverState) {
        TransceiverState.RECORDING -> Icons.Default.Mic
        TransceiverState.TRANSCRIBING -> Icons.Default.GraphicEq
        TransceiverState.TRANSMITTING -> Icons.Default.CellTower
        TransceiverState.RECEIVING -> Icons.Default.Sensors
        TransceiverState.SPEAKING -> Icons.AutoMirrored.Filled.VolumeUp
        TransceiverState.IDLE -> Icons.Default.RadioButtonChecked
    }

    val infiniteTransition = rememberInfiniteTransition(label = "PulseAnim")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = animatedStateColor.copy(alpha = 0.12f),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                colors = listOf(
                    animatedStateColor.copy(alpha = if (transceiverState != TransceiverState.IDLE) pulseAlpha else 0.4f),
                    animatedStateColor.copy(alpha = 0.15f)
                )
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // State header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (transceiverState != TransceiverState.IDLE)
                                    animatedStateColor.copy(alpha = pulseAlpha)
                                else
                                    animatedStateColor
                            )
                    )
                    Text(
                        text = "PIPELINE: $stateTitle",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = animatedStateColor,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                Icon(
                    imageVector = stateIcon,
                    contentDescription = stateTitle,
                    tint = animatedStateColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Description text
            Text(
                text = stateDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.5.sp
            )

            // Visual Pipeline Flow Stepper
            PipelineFlowStepper(currentState = transceiverState)
        }
    }
}

@Composable
private fun PipelineFlowStepper(
    currentState: TransceiverState
) {
    val steps = listOf(
        Triple("REC", TransceiverState.RECORDING, Color(0xFFFF1744)),
        Triple("STT", TransceiverState.TRANSCRIBING, Color(0xFFFFB300)),
        Triple("TX", TransceiverState.TRANSMITTING, Color(0xFF00E5FF)),
        Triple("RX", TransceiverState.RECEIVING, Color(0xFFD500F9)),
        Triple("TTS", TransceiverState.SPEAKING, Color(0xFF00E676))
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, (label, state, color) ->
            val isActive = currentState == state
            val isPassed = when (currentState) {
                TransceiverState.IDLE -> false
                TransceiverState.RECORDING -> false
                TransceiverState.TRANSCRIBING -> index < 1
                TransceiverState.TRANSMITTING -> index < 2
                TransceiverState.RECEIVING -> index < 3
                TransceiverState.SPEAKING -> index < 4
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = when {
                    isActive -> color
                    isPassed -> color.copy(alpha = 0.25f)
                    else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                },
                border = if (isActive) null else CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.linearGradient(
                        listOf(
                            if (isPassed) color.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (isActive) FontWeight.Black else FontWeight.Bold,
                        color = when {
                            isActive -> if (state == TransceiverState.RECEIVING) Color.White else Color.Black
                            isPassed -> color
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        }
                    )
                }
            }

            if (index < steps.size - 1) {
                Text(
                    text = "›",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }
    }
}

// ============================================================================
// 2. LATENCY TELEMETRY CARD
// ============================================================================

@Composable
private fun LatencyTelemetrySection(
    timestamps: VerticalSliceTimestamps,
    metrics: LatencyMetrics?
) {
    // Resolve latencies from timestamps model or metrics
    val sttLatency = when {
        timestamps.sttLatencyMs > 0L -> timestamps.sttLatencyMs
        metrics != null && metrics.sttLatencyMs > 0L -> metrics.sttLatencyMs
        timestamps.t1SttComplete >= timestamps.t0PttReleased && timestamps.t0PttReleased > 0L -> timestamps.t1SttComplete - timestamps.t0PttReleased
        else -> 0L
    }

    val netLatency = when {
        timestamps.networkLatencyMs > 0L -> timestamps.networkLatencyMs
        metrics != null && metrics.transmissionLatencyMs > 0L -> metrics.transmissionLatencyMs
        timestamps.t3Received >= timestamps.t2Transmitted && timestamps.t2Transmitted > 0L -> timestamps.t3Received - timestamps.t2Transmitted
        else -> 0L
    }

    val ttsLatency = when {
        timestamps.ttsLatencyMs > 0L -> timestamps.ttsLatencyMs
        metrics != null && metrics.ttsLatencyMs > 0L -> metrics.ttsLatencyMs
        timestamps.t4TtsComplete >= timestamps.t3Received && timestamps.t3Received > 0L -> timestamps.t4TtsComplete - timestamps.t3Received
        else -> 0L
    }

    val endToEndLatency = when {
        timestamps.endToEndLatencyMs > 0L -> timestamps.endToEndLatencyMs
        metrics != null && metrics.endToEndLatencyMs > 0L -> metrics.endToEndLatencyMs
        timestamps.t5PlaybackStarted >= timestamps.t0PttReleased && timestamps.t0PttReleased > 0L -> timestamps.t5PlaybackStarted - timestamps.t0PttReleased
        sttLatency > 0L || netLatency > 0L || ttsLatency > 0L -> sttLatency + netLatency + ttsLatency
        else -> 0L
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                )
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // End-to-End Latency Hero Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "END-TO-END LATENCY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Text(
                        text = if (endToEndLatency > 0L) "$endToEndLatency ms" else "-- ms",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = when {
                            endToEndLatency <= 0L -> MaterialTheme.colorScheme.onSurfaceVariant
                            endToEndLatency < 1000L -> Color(0xFF00E676) // Excellent: Green
                            endToEndLatency < 2000L -> Color(0xFFFFB300) // Warning: Amber
                            else -> Color(0xFFFF1744)                    // High: Red
                        }
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        endToEndLatency <= 0L -> MaterialTheme.colorScheme.surfaceVariant
                        endToEndLatency < 1000L -> Color(0xFF00E676).copy(alpha = 0.15f)
                        endToEndLatency < 2000L -> Color(0xFFFFB300).copy(alpha = 0.15f)
                        else -> Color(0xFFFF1744).copy(alpha = 0.15f)
                    }
                ) {
                    Text(
                        text = when {
                            endToEndLatency <= 0L -> "STANDBY"
                            endToEndLatency < 1000L -> "OPTIMAL (< 1.0s)"
                            endToEndLatency < 2000L -> "ACCEPTABLE (< 2.0s)"
                            else -> "HIGH LATENCY"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = when {
                            endToEndLatency <= 0L -> MaterialTheme.colorScheme.onSurfaceVariant
                            endToEndLatency < 1000L -> Color(0xFF00E676)
                            endToEndLatency < 2000L -> Color(0xFFFFB300)
                            else -> Color(0xFFFF1744)
                        },
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Individual Subsystem Latencies (STT, Network, TTS)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LatencyMetricTile(
                    title = "STT Latency",
                    valueMs = sttLatency,
                    accentColor = Color(0xFFFFB300),
                    modifier = Modifier.weight(1f)
                )
                LatencyMetricTile(
                    title = "Network Latency",
                    valueMs = netLatency,
                    accentColor = Color(0xFF00E5FF),
                    modifier = Modifier.weight(1f)
                )
                LatencyMetricTile(
                    title = "TTS Latency",
                    valueMs = ttsLatency,
                    accentColor = Color(0xFF00E676),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LatencyMetricTile(
    title: String,
    valueMs: Long,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(accentColor.copy(alpha = 0.35f), Color.Transparent)
            )
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.5.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = if (valueMs > 0L) "$valueMs ms" else "-- ms",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Black,
                color = if (valueMs > 0L) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}

// ============================================================================
// 3. TIMESTAMPS DISPLAY SECTION (HH:mm:ss.SSS)
// ============================================================================

@Composable
private fun TimestampsDisplaySection(
    timestamps: VerticalSliceTimestamps
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    Color.Transparent
                )
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PIPELINE TIMESTAMPS (HH:mm:ss.SSS)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp
                )
                Text(
                    text = "EVALUATION TRACE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp
                )
            }

            // Grid of 6 timestamps (t0 .. t5)
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TimestampItem(
                        code = "t0",
                        label = "PTT release",
                        timestamp = timestamps.t0PttReleased,
                        accentColor = Color(0xFFFF1744),
                        modifier = Modifier.weight(1f)
                    )
                    TimestampItem(
                        code = "t1",
                        label = "STT complete",
                        timestamp = timestamps.t1SttComplete,
                        accentColor = Color(0xFFFFB300),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TimestampItem(
                        code = "t2",
                        label = "Msg transmitted",
                        timestamp = timestamps.t2Transmitted,
                        accentColor = Color(0xFF00E5FF),
                        modifier = Modifier.weight(1f)
                    )
                    TimestampItem(
                        code = "t3",
                        label = "Msg received",
                        timestamp = timestamps.t3Received,
                        accentColor = Color(0xFFD500F9),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TimestampItem(
                        code = "t4",
                        label = "TTS complete",
                        timestamp = timestamps.t4TtsComplete,
                        accentColor = Color(0xFF00E676),
                        modifier = Modifier.weight(1f)
                    )
                    TimestampItem(
                        code = "t5",
                        label = "Audio playback",
                        timestamp = timestamps.t5PlaybackStarted,
                        accentColor = Color(0xFF00C853),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimestampItem(
    code: String,
    label: String,
    timestamp: Long,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val isRecorded = timestamp > 0L
    val formattedTime = formatTimestampWithMillis(timestamp)

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    if (isRecorded) accentColor.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    Color.Transparent
                )
            )
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = code,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isRecorded) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = label,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Text(
                text = formattedTime,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isRecorded) FontWeight.Bold else FontWeight.Normal,
                color = if (isRecorded) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
            )
        }
    }
}

/**
 * Formats epoch millisecond timestamps into HH:mm:ss.SSS or "--:--:--" if not yet recorded.
 */
private fun formatTimestampWithMillis(epochMs: Long): String {
    if (epochMs <= 0L) return "--:--:--"
    return try {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        sdf.format(Date(epochMs))
    } catch (e: Exception) {
        "--:--:--"
    }
}
