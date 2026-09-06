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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.itantra.data.PttState

@Composable
fun PttButton(
    pttState: PttState,
    isLocked: Boolean,
    isEmergency: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    onLockToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isBeingPressed by remember { mutableStateOf(false) }
    val isActive = pttState != PttState.IDLE || isBeingPressed || isLocked

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

    val activeColor = when {
        isEmergency -> Color(0xFFFF1744)
        pttState == PttState.PROCESSING -> Color(0xFFFFB300)
        else -> Color(0xFF00E676)
    }
    val idleColor = MaterialTheme.colorScheme.primary
    val buttonColor by animateColorAsState(
        targetValue = if (isActive) activeColor else idleColor,
        animationSpec = tween(durationMillis = 200),
        label = "PttColor"
    )

    val currentOnPressStart by rememberUpdatedState(onPressStart)
    val currentOnPressEnd by rememberUpdatedState(onPressEnd)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(200.dp)
        ) {
            // Pulsing radar ripples
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
            Canvas(modifier = Modifier.size(160.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF222B3D), Color(0xFF0C1017)),
                        center = Offset(size.width / 2, size.height / 2),
                        radius = size.width / 2
                    ),
                    radius = size.width / 2
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.15f),
                    radius = size.width / 2,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Main tactile PTT Button
            Surface(
                modifier = Modifier
                    .size(136.dp)
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
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isActive) Icons.Default.Mic else Icons.Default.MicNone,
                            contentDescription = "Push To Talk",
                            tint = Color.Black,
                            modifier = Modifier.size(46.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when {
                                pttState == PttState.RECORDING -> "RECORDING"
                                pttState == PttState.PROCESSING -> "PROCESSING"
                                isLocked -> "LOCKED"
                                else -> "IDLE"
                            },
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
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

        Text(
            text = when {
                isEmergency && isActive -> "TRANSMITTING HIGH-PRIORITY EMERGENCY SPEECH"
                pttState == PttState.RECORDING -> "Recording 16-bit Mono PCM audio (16kHz)..."
                pttState == PttState.PROCESSING -> "Processing & finalizing PCM buffer..."
                isLocked -> "Hands-Free Lock Active (Tap lock to release)"
                else -> "Hold to record audio or tap Lock badge for hands-free"
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (isEmergency) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
