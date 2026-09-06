package org.isro.itantra.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.isro.itantra.ui.PttState

/**
 * PTT Interaction Mode:
 * - HOLD_TO_TALK: Standard physical walkie-talkie press-and-hold behavior.
 * - CLICK_TO_TALK: Toggle mode (click once to start, click again to stop & transmit).
 */
enum class PttMode {
    HOLD_TO_TALK,
    CLICK_TO_TALK
}

/**
 * Dual-Mode Push-To-Talk Component for iTantra Neural Transceiver.
 *
 * Designed for both physical device touchscreens and desktop emulator mouse interactions.
 * Uses pointerInput(Unit) with rememberUpdatedState to prevent mid-gesture recomposition drops.
 */
@Composable
fun DualModePttComponent(
    pttState: PttState,
    amplitude: Float,
    onPttDown: () -> Unit,
    onPttUp: () -> Unit,
    onPttCancel: () -> Unit,
    modifier: Modifier = Modifier,
    initialMode: PttMode = PttMode.HOLD_TO_TALK
) {
    var pttMode by remember { mutableStateOf(initialMode) }
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    // Stable references to prevent pointerInput cancellation
    val currentOnPttDown by rememberUpdatedState(onPttDown)
    val currentOnPttUp by rememberUpdatedState(onPttUp)
    val currentOnPttCancel by rememberUpdatedState(onPttCancel)
    val currentPttState by rememberUpdatedState(pttState)

    // Dynamic color theming based on engine state
    val buttonColor by animateColorAsState(
        targetValue = when (pttState) {
            PttState.IDLE -> Color(0xFF10B981) // Emerald Green (Ready)
            PttState.RECORDING -> Color(0xFFEF4444) // Vivid Red (Recording)
            PttState.PLAYING -> Color(0xFF0284C7) // Sky Blue (Playback)
        },
        animationSpec = tween(250),
        label = "ButtonColor"
    )

    val haloColor by animateColorAsState(
        targetValue = when (pttState) {
            PttState.IDLE -> Color(0xFF10B981).copy(alpha = 0.15f)
            PttState.RECORDING -> Color(0xFFEF4444).copy(alpha = 0.35f)
            PttState.PLAYING -> Color(0xFF0284C7).copy(alpha = 0.25f)
        },
        animationSpec = tween(250),
        label = "HaloColor"
    )

    // Physical pulse & audio reactivity animations
    val infiniteTransition = rememberInfiniteTransition(label = "PttInfiniteTransition")
    val idlePulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "IdlePulseScale"
    )

    val recordingWaveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RecordingWaveAlpha"
    )

    val recordingWaveScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RecordingWaveScale"
    )

    val audioReactiveScale by animateFloatAsState(
        targetValue = if (pttState == PttState.RECORDING) {
            1.0f + (amplitude.coerceIn(0.0f, 1.0f) * 0.20f)
        } else if (pttState == PttState.IDLE) {
            idlePulseScale
        } else {
            1.0f
        },
        animationSpec = tween(60),
        label = "AudioReactiveScale"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- MODE TOGGLE SELECTOR ---
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0B132B),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeBadge(
                    text = "HOLD TO TALK",
                    selected = pttMode == PttMode.HOLD_TO_TALK,
                    onClick = {
                        if (pttState == PttState.RECORDING) onPttCancel()
                        pttMode = PttMode.HOLD_TO_TALK
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                )
                Spacer(modifier = Modifier.width(4.dp))
                ModeBadge(
                    text = "CLICK TO TALK",
                    selected = pttMode == PttMode.CLICK_TO_TALK,
                    onClick = {
                        if (pttState == PttState.RECORDING) onPttCancel()
                        pttMode = PttMode.CLICK_TO_TALK
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                )
            }
        }

        // --- PTT INTERACTIVE BUTTON WITH ANIMATED HALO ---
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(170.dp)
        ) {
            // Ripple wave ring during recording
            if (pttState == PttState.RECORDING) {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .scale(recordingWaveScale)
                        .graphicsLayer { alpha = recordingWaveAlpha }
                        .border(2.dp, Color(0xFFEF4444), CircleShape)
                )
            }

            // Outer ambient glow ring
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .scale(audioReactiveScale)
                    .clip(CircleShape)
                    .background(haloColor)
            )

            // Inner touch/click target
            val pressAnimatable = remember { Animatable(1.0f) }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(124.dp)
                    .scale(pressAnimatable.value)
                    .shadow(16.dp, CircleShape, spotColor = buttonColor)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                buttonColor.copy(alpha = 0.95f),
                                buttonColor.copy(alpha = 0.75f)
                            )
                        )
                    )
                    .border(
                        width = 3.dp,
                        color = Color.White.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
                    .then(
                        if (pttMode == PttMode.HOLD_TO_TALK) {
                            // POINTER GESTURE: Stable Unit key prevents recomposition cancellation
                            Modifier.pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    coroutineScope.launch {
                                        pressAnimatable.animateTo(0.92f, tween(100))
                                    }
                                    currentOnPttDown()

                                    val up = waitForUpOrCancellation()
                                    coroutineScope.launch {
                                        pressAnimatable.animateTo(1.0f, tween(150))
                                    }

                                    if (up != null) {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
                                        currentOnPttUp()
                                    } else {
                                        currentOnPttCancel()
                                    }
                                }
                            }
                        } else {
                            // CLICK / TOGGLE MODE
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                when (currentPttState) {
                                    PttState.IDLE -> currentOnPttDown()
                                    PttState.RECORDING -> currentOnPttUp()
                                    PttState.PLAYING -> { /* playback in progress */ }
                                }
                            }
                        }
                    )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = when (pttState) {
                            PttState.IDLE -> Icons.Default.Mic
                            PttState.RECORDING -> Icons.Default.Stop
                            PttState.PLAYING -> Icons.Default.PlayArrow
                        },
                        contentDescription = "Push To Talk",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = when (pttState) {
                            PttState.IDLE -> if (pttMode == PttMode.HOLD_TO_TALK) "HOLD" else "START"
                            PttState.RECORDING -> if (pttMode == PttMode.HOLD_TO_TALK) "RELEASE" else "STOP"
                            PttState.PLAYING -> "PLAYING"
                        },
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // State label and hint
        Text(
            text = when (pttState) {
                PttState.IDLE -> if (pttMode == PttMode.HOLD_TO_TALK) "PRESS & HOLD TO TRANSMIT" else "TAP TO START RECORDING"
                PttState.RECORDING -> if (pttMode == PttMode.HOLD_TO_TALK) "RECORDING 16 kHz PCM (RELEASE TO SEND)" else "RECORDING... TAP TO TRANSMIT"
                PttState.PLAYING -> "PLAYING AUDIO LOOPBACK..."
            },
            color = when (pttState) {
                PttState.IDLE -> Color(0xFF94A3B8)
                PttState.RECORDING -> Color(0xFFF87171)
                PttState.PLAYING -> Color(0xFF38BDF8)
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ModeBadge(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(0xFF1E3A8A) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = if (selected) Color(0xFF60A5FA) else Color(0xFF64748B),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
