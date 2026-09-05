package org.isro.itantra.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.isro.itantra.ui.PttState

@Composable
fun PttButton(
    pttState: PttState,
    amplitudeProvider: () -> Float,
    onPttDown: () -> Unit,
    onPttUp: () -> Unit,
    onPttCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val isRecording = pttState == PttState.RECORDING
    val isPlaying = pttState == PttState.PLAYING

    val currentPttState by rememberUpdatedState(pttState)
    val currentOnPttDown by rememberUpdatedState(onPttDown)
    val currentOnPttUp by rememberUpdatedState(onPttUp)
    val currentOnPttCancel by rememberUpdatedState(onPttCancel)

    val buttonColor by animateColorAsState(
        targetValue = when (pttState) {
            PttState.IDLE -> Color(0xFF1E293B)
            PttState.RECORDING -> Color(0xFFE53935)
            PttState.PLAYING -> Color(0xFF10B981)
        },
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "PttButtonColor"
    )

    val scale by animateFloatAsState(
        targetValue = when (pttState) {
            PttState.IDLE -> 1.0f
            PttState.RECORDING -> 1.18f
            PttState.PLAYING -> 1.06f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "PttButtonScale"
    )

    Box(
        modifier = modifier
            .size(240.dp)
            .semantics {
                contentDescription = when (pttState) {
                    PttState.IDLE -> "Walkie Talkie Push-To-Talk button. Hold to record."
                    PttState.RECORDING -> "Recording audio. Release to send."
                    PttState.PLAYING -> "Playing recorded audio."
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Radar pulse halos when active
        if (isRecording || isPlaying) {
            val pulseTransition = rememberInfiniteTransition(label = "RadarPulse")
            val pulseProgress by pulseTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "RadarPulseProgress"
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxRadius = size.minDimension / 2f
                val baseRadius = maxRadius * 0.52f
                val span = maxRadius - baseRadius
                val haloColor = if (isRecording) Color(0xFFE53935) else Color(0xFF10B981)

                for (i in 0 until 3) {
                    val ringProgress = (pulseProgress + (i / 3f)) % 1f
                    val easedScale = FastOutSlowInEasing.transform(ringProgress)
                    val radius = baseRadius + (span * easedScale)
                    val alpha = (1f - ringProgress) * 0.7f

                    drawCircle(
                        color = haloColor.copy(alpha = alpha.coerceIn(0f, 1f)),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 3.dp.toPx() * (1f - ringProgress * 0.5f))
                    )
                }
            }
        }

        // Audio-reactive ambient glow
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .graphicsLayer {
                        val reactive = 1.0f + (amplitudeProvider() * 0.25f)
                        scaleX = scale * reactive
                        scaleY = scale * reactive
                        alpha = 0.35f
                    }
                    .background(Color(0xFFE53935), CircleShape)
            )
        }

        // Main Tactile Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(elevation = if (isRecording) 16.dp else 6.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(buttonColor)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (currentPttState == PttState.PLAYING) return@awaitEachGesture

                        down.consume()
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        currentOnPttDown()

                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            up.consume()
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            currentOnPttUp()
                        } else {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            currentOnPttCancel()
                        }
                    }
                }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (pttState) {
                    PttState.RECORDING -> {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "RECORDING",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    PttState.PLAYING -> {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "PLAYING",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    PttState.IDLE -> {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "HOLD TO TALK",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}
