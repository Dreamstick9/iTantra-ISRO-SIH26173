package com.example.itantra.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import com.example.itantra.data.TtsUiState
import com.example.itantra.tts.TtsPlaybackState

/**
 * Dedicated 100% Offline Neural TTS Diagnostic & Test Card.
 * Allows entering custom text, testing offline synthesis, monitoring playback state,
 * and inspecting real-time neural synthesis latency ($T_{synth}$) and Real-Time Factor (RTF).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TtsTestCard(
    ttsState: TtsUiState,
    onTextChanged: (String) -> Unit,
    onSpeak: (String?) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = listOf(
        "Hello, this is iTantra.",
        "Cyclone advisory: Evacuate immediately.",
        "NavIC satellite transceiver signal acquired."
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Title + Offline Model Badge + Playback State Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "TTS Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Offline Neural TTS Engine",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "sherpa-onnx VITS Piper · 16 kHz Mono AudioTrack",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Playback State Badge
                PlaybackStateBadge(state = ttsState.playbackState)
            }

            // Text Input Field
            OutlinedTextField(
                value = ttsState.inputText,
                onValueChange = onTextChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Text to Synthesize") },
                placeholder = { Text("Enter text, e.g. \"Hello, this is iTantra.\"") },
                singleLine = false,
                maxLines = 3,
                trailingIcon = {
                    if (ttsState.inputText.isNotEmpty()) {
                        IconButton(onClick = { onTextChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear text",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(10.dp)
            )

            // Quick Presets
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Quick Test Presets:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presets.forEach { preset ->
                        AssistChip(
                            onClick = { onTextChanged(preset) },
                            label = {
                                Text(
                                    text = preset,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (ttsState.inputText == preset) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                        )
                    }
                }
            }

            // Action Buttons: [Speak] and [Stop]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // [Speak] Button
                Button(
                    onClick = { onSpeak(null) },
                    modifier = Modifier.weight(1f),
                    enabled = !ttsState.playbackState.isBusy && ttsState.inputText.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (ttsState.playbackState == TtsPlaybackState.SYNTHESIZING || ttsState.playbackState == TtsPlaybackState.INITIALIZING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (ttsState.playbackState == TtsPlaybackState.INITIALIZING) "Loading Model..." else "Synthesizing...",
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Speak Icon",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Speak",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // [Stop] Button
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.weight(0.5f),
                    enabled = ttsState.playbackState == TtsPlaybackState.PLAYING,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, if (ttsState.playbackState == TtsPlaybackState.PLAYING) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop Icon",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Stop",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Telemetry & Latency Telemetry HUD
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "SYNTHESIS METRICS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (ttsState.isModelLoaded) "MODEL READY (OFFLINE)" else "INITIALIZING",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ttsState.isModelLoaded) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricItem(
                            label = "T_synth (Latency)",
                            value = if (ttsState.synthesisLatencyMs > 0) "${ttsState.synthesisLatencyMs} ms" else "---"
                        )
                        MetricItem(
                            label = "RTF (Speed)",
                            value = if (ttsState.realTimeFactor > 0.0) "${"%.2f".format(ttsState.realTimeFactor)} (${"%.1f".format(1.0 / ttsState.realTimeFactor)}x RT)" else "---"
                        )
                        MetricItem(
                            label = "T_audio (Duration)",
                            value = if (ttsState.audioDurationMs > 0) "${"%.2f".format(ttsState.audioDurationMs / 1000.0)} s" else "---"
                        )
                    }
                }
            }

            // Error Display Banner
            AnimatedVisibility(visible = ttsState.errorMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = ttsState.errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackStateBadge(state: TtsPlaybackState) {
    val (bgColor, textColor, text) = when (state) {
        TtsPlaybackState.IDLE -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "IDLE")
        TtsPlaybackState.INITIALIZING -> Triple(Color(0xFFE3F2FD), Color(0xFF1565C0), "INIT")
        TtsPlaybackState.SYNTHESIZING -> Triple(Color(0xFFE1F5FE), Color(0xFF0277BD), "SYNTHESIZING")
        TtsPlaybackState.PLAYING -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "PLAYING")
        TtsPlaybackState.STOPPED -> Triple(Color(0xFFFFF8E1), Color(0xFFF57F17), "STOPPED")
        TtsPlaybackState.ERROR -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "ERROR")
    }

    Surface(
        shape = CircleShape,
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
