package com.itantra.voice.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.voice.ui.PttState

@Suppress("DEPRECATION")
@Composable
fun PttButton(
    state: PttState,
    isHolding: Boolean,
    amplitude: Float,
    isEnabled: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRecording = state == PttState.RECORDING || isHolding
    val isBusy = state in listOf(PttState.TRANSCRIBING, PttState.TRANSLATING, PttState.SYNTHESIZING)
    val isPlaying = state == PttState.PLAYING

    // Scale animation: target 1.15x when holding or recording
    val scaleFactor by animateFloatAsState(
        targetValue = if (isRecording) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pttScale"
    )

    // Dynamic color transition
    val buttonColor by animateColorAsState(
        targetValue = when {
            isRecording -> MaterialTheme.colorScheme.error
            isBusy -> MaterialTheme.colorScheme.surfaceVariant
            isPlaying -> Color(0xFF00897B)
            !isEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.primary
        },
        label = "pttColor"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isRecording -> MaterialTheme.colorScheme.onError
            isBusy -> MaterialTheme.colorScheme.onSurfaceVariant
            isPlaying -> Color.White
            !isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            else -> MaterialTheme.colorScheme.onPrimary
        },
        label = "pttContentColor"
    )

    Box(
        modifier = modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing ring that expands with audio amplitude during recording
        if (isRecording) {
            val ringScale = 1.0f + (amplitude.coerceIn(0f, 1f) * 0.35f)
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(ringScale)
                    .clip(CircleShape)
                    .background(buttonColor.copy(alpha = 0.25f))
            )
        }

        // Tactile Press-and-Hold Button
        Box(
            modifier = Modifier
                .size(130.dp)
                .scale(scaleFactor)
                .clip(CircleShape)
                .background(buttonColor)
                .pointerInput(isEnabled, isBusy) {
                    if (!isEnabled || isBusy) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        onPress()

                        var upOrCancel: PointerInputChange? = null
                        while (upOrCancel == null) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                upOrCancel = change ?: event.changes.firstOrNull() ?: down
                            }
                        }
                        upOrCancel.consume()
                        onRelease()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    isBusy -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = contentColor,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "PROCESSING",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                            fontSize = 11.sp
                        )
                    }
                    isPlaying -> {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Playing Audio",
                            tint = contentColor,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "PLAYING",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                            fontSize = 11.sp
                        )
                    }
                    isRecording -> {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Recording",
                            tint = contentColor,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "RELEASE TO SEND",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = contentColor,
                            fontSize = 10.sp
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Hold to Speak",
                            tint = contentColor,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "HOLD TO SPEAK",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = contentColor,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
