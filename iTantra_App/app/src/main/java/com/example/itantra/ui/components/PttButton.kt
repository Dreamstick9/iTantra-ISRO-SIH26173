package com.example.itantra.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.itantra.data.PttState
import com.example.itantra.data.TransceiverState

/**
 * Tactical Single Push-To-Talk Button for iTantra Vertical Slice.
 *
 * Implements:
 * 1. Prominent "PUSH TO TALK" label when idle.
 * 2. Direct tactile press/release interaction (press down -> start recording; release -> stop, transcribe, transmit).
 * 3. Distinct visual cues reflecting the 6 transceiver states:
 *    - RECORDING (Vibrant Red)
 *    - TRANSCRIBING (Amber)
 *    - TRANSMITTING (Cyan)
 *    - RECEIVING (Purple)
 *    - SPEAKING (Green)
 *    - IDLE / "PUSH TO TALK" (Tactical Primary)
 */
@Composable
fun PttButton(
    transceiverState: TransceiverState = TransceiverState.IDLE,
    pttState: PttState = transceiverState.toPttState(),
    isLocked: Boolean = false,
    isEmergency: Boolean = false,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    onLockToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isBeingPressed by remember { mutableStateOf(false) }

    // Resolve the active transceiver state with instant press feedback
    val effectiveState = when {
        isBeingPressed -> TransceiverState.RECORDING
        transceiverState != TransceiverState.IDLE -> transceiverState
        pttState == PttState.RECORDING -> TransceiverState.RECORDING
        pttState == PttState.PROCESSING -> TransceiverState.TRANSCRIBING
        else -> TransceiverState.IDLE
    }

    val isActive = effectiveState != TransceiverState.IDLE || isLocked

    val buttonScale by animateFloatAsState(
        targetValue = if (isBeingPressed) 0.92f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "PttButtonScale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "PttRipple")
    val rippleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PttRippleProgress"
    )

    // Distinctive state colors: Red (Rec), Amber (STT), Cyan (TX), Purple (RX), Green (Speak)
    val stateColor = when {
        isEmergency -> Color(0xFFFF1744)
        effectiveState == TransceiverState.RECORDING -> Color(0xFFFF1744) // Red
        effectiveState == TransceiverState.TRANSCRIBING -> Color(0xFFFFB300) // Amber
        effectiveState == TransceiverState.TRANSMITTING -> Color(0xFF00E5FF) // Cyan
        effectiveState == TransceiverState.RECEIVING -> Color(0xFFD500F9) // Purple
        effectiveState == TransceiverState.SPEAKING -> Color(0xFF00E676) // Green
        else -> MaterialTheme.colorScheme.primary
    }

    val idleColor = MaterialTheme.colorScheme.primary
    val buttonColor by animateColorAsState(
        targetValue = if (isActive) stateColor else idleColor,
        animationSpec = tween(durationMillis = 200),
        label = "PttColor"
    )

    // High contrast foreground content color
    val contentColor = when (effectiveState) {
        TransceiverState.RECEIVING -> Color.White
        else -> Color.Black
    }

    // Dynamic icon for current state
    val icon = when (effectiveState) {
        TransceiverState.RECORDING -> Icons.Default.Mic
        TransceiverState.TRANSCRIBING -> Icons.Default.GraphicEq
        TransceiverState.TRANSMITTING -> Icons.Default.CellTower
        TransceiverState.RECEIVING -> Icons.Default.Sensors
        TransceiverState.SPEAKING -> Icons.AutoMirrored.Filled.VolumeUp
        TransceiverState.IDLE -> if (isLocked) Icons.Default.Lock else Icons.Default.Mic
    }

    // Prominent primary button text
    val buttonLabel = when (effectiveState) {
        TransceiverState.RECORDING -> "RECORDING"
        TransceiverState.TRANSCRIBING -> "TRANSCRIBING"
        TransceiverState.TRANSMITTING -> "TRANSMITTING"
        TransceiverState.RECEIVING -> "RECEIVING"
        TransceiverState.SPEAKING -> "SPEAKING"
        TransceiverState.IDLE -> if (isLocked) "LOCKED" else "PUSH TO TALK"
    }

    val currentOnPressStart by rememberUpdatedState(onPressStart)
    val currentOnPressEnd by rememberUpdatedState(onPressEnd)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(208.dp)
        ) {
            // Pulsing radar ripples when active or recording
            if (isActive) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val maxRadius = size.minDimension / 2f
                    val currentRadius1 = maxRadius * (0.6f + 0.4f * rippleProgress)
                    val alpha1 = (1f - rippleProgress).coerceIn(0f, 0.8f)

                    drawCircle(
                        color = buttonColor.copy(alpha = alpha1 * 0.4f),
                        radius = currentRadius1,
                        style = Stroke(width = 4.dp.toPx())
                    )

                    val staggered = (rippleProgress + 0.5f) % 1f
                    val currentRadius2 = maxRadius * (0.6f + 0.4f * staggered)
                    val alpha2 = (1f - staggered).coerceIn(0f, 0.8f)

                    drawCircle(
                        color = buttonColor.copy(alpha = alpha2 * 0.3f),
                        radius = currentRadius2,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }

            // Outer tactile bezel ring
            Canvas(modifier = Modifier.size(164.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF222B3D), Color(0xFF0C1017)),
                        center = Offset(size.width / 2, size.height / 2),
                        radius = size.width / 2
                    ),
                    radius = size.width / 2
                )
                drawCircle(
                    color = buttonColor.copy(alpha = if (isActive) 0.5f else 0.2f),
                    radius = size.width / 2,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Main tactile PTT Button
            Surface(
                modifier = Modifier
                    .size(140.dp)
                    .scale(buttonScale)
                    .shadow(elevation = if (isBeingPressed) 4.dp else 12.dp, shape = CircleShape)
                    .pointerInput(isLocked) {
                        if (!isLocked) {
                            awaitEachGesture {
                                awaitFirstDown().also {
                                    isBeingPressed = true
                                    currentOnPressStart()
                                }
                                waitForUpOrCancellation()
                                isBeingPressed = false
                                currentOnPressEnd()
                            }
                        }
                    },
                shape = CircleShape,
                color = buttonColor
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = buttonLabel,
                            tint = contentColor,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = buttonLabel,
                            color = contentColor,
                            fontWeight = FontWeight.Black,
                            fontSize = if (effectiveState == TransceiverState.IDLE && !isLocked) 13.sp else 12.sp,
                            textAlign = TextAlign.Center,
                            letterSpacing = if (effectiveState == TransceiverState.IDLE) 1.1.sp else 0.8.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Hands-free lock latch
            Surface(
                onClick = onLockToggle,
                shape = CircleShape,
                color = if (isLocked) Color(0xFFFFB300) else MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .size(38.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = (-16).dp, y = 16.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock PTT",
                        tint = if (isLocked) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Descriptive status cue under button
        Text(
            text = when {
                isEmergency && isActive -> "TRANSMITTING HIGH-PRIORITY EMERGENCY SPEECH"
                effectiveState == TransceiverState.RECORDING -> "Recording 16-bit Mono PCM (16kHz)... Release to send"
                effectiveState == TransceiverState.TRANSCRIBING -> "Transcribing speech on-device (Offline STT)..."
                effectiveState == TransceiverState.TRANSMITTING -> "Transmitting encrypted payload via Wi-Fi Direct..."
                effectiveState == TransceiverState.RECEIVING -> "Receiving packet payload from peer device..."
                effectiveState == TransceiverState.SPEAKING -> "Synthesizing voice & playing audio via TTS..."
                isLocked -> "Hands-Free Lock Active (Tap lock badge to release)"
                else -> "Press and hold to record, release to transcribe & transmit"
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (isEmergency) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
