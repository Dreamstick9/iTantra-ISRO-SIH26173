package com.itantra.voice.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.itantra.voice.ui.PttState
import com.itantra.voice.ui.theme.Link
import com.itantra.voice.ui.theme.Signal

/** Stable handle for UI tests. */
const val TALK_BUTTON_TAG = "talk_button"

/**
 * Push-to-talk control.
 *
 * A single filled circle: red while transmitting, green while the received message is
 * being spoken, outlined and neutral at rest. The ring around it tracks live input
 * level so the operator can see the microphone is hearing them without reading text.
 */
@Composable
fun TalkButton(
    state: PttState,
    isHolding: Boolean,
    amplitude: Float,
    isEnabled: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRecording = state == PttState.RECORDING || isHolding
    val isBusy = state == PttState.TRANSCRIBING ||
        state == PttState.TRANSLATING ||
        state == PttState.SYNTHESIZING
    val isPlaying = state == PttState.PLAYING

    val scale by animateFloatAsState(
        targetValue = if (isRecording) 1.06f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "talkScale"
    )

    val fill = when {
        isRecording -> Signal
        isPlaying -> Link
        else -> MaterialTheme.colorScheme.surface
    }
    val content = when {
        isRecording || isPlaying -> MaterialTheme.colorScheme.onPrimary
        !isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> Signal
    }
    val ringColor = when {
        !isEnabled -> MaterialTheme.colorScheme.outline
        else -> Signal
    }

    val caption = when {
        isBusy -> "WORKING"
        isPlaying -> "SPEAKING"
        isRecording -> "RELEASE TO SEND"
        !isEnabled -> "UNAVAILABLE"
        else -> "HOLD TO TALK"
    }

    Box(
        modifier = modifier.size(184.dp),
        contentAlignment = Alignment.Center
    ) {
        // Amplitude ring: grows with the operator's voice.
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(184.dp)
                    .scale(0.88f + amplitude.coerceIn(0f, 1f) * 0.12f)
                    .clip(CircleShape)
                    .background(Signal.copy(alpha = 0.12f))
            )
        }

        Box(
            modifier = Modifier
                .size(156.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(fill)
                .border(
                    width = if (isRecording || isPlaying) 0.dp else 2.dp,
                    color = ringColor,
                    shape = CircleShape
                )
                .testTag(TALK_BUTTON_TAG)
                .semantics { contentDescription = "Push to talk. $caption" }
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = content,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = content
                )
            }
        }
    }
}
