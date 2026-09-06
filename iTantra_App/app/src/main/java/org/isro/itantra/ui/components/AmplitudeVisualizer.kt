package org.isro.itantra.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * Real-time 10-bar logarithmic audio amplitude visualizer.
 * Features:
 * - Smooth spring/decay animation per bar
 * - Canvas hardware rendering with zero recomposition overhead
 * - Emergency amber/red gradient under high speech peaks
 */
@Composable
fun AmplitudeVisualizer(
    amplitude: Float,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 10
) {
    val smoothAmp by animateFloatAsState(
        targetValue = if (isRecording) amplitude.coerceIn(0.0f, 1.0f) else 0.0f,
        animationSpec = tween(durationMillis = 60),
        label = "SmoothAmplitude"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Color(0xFF070D1E), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val totalWidth = size.width
            val totalHeight = size.height
            val spacing = 6.dp.toPx()
            val availableBarWidth = (totalWidth - (spacing * (barCount - 1))) / barCount
            val barWidth = maxOf(2.dp.toPx(), availableBarWidth)
            val cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)

            for (i in 0 until barCount) {
                // Spectral weighting curve (higher energy in middle speech frequencies)
                val centerNorm = (i - (barCount / 2.0f)) / (barCount / 2.0f)
                val spectralWeight = 1.0f - (0.35f * centerNorm * centerNorm)
                val phaseOffset = sin(i * 0.8f + (smoothAmp * 4f)) * 0.1f

                val barHeightFactor = if (isRecording) {
                    val raw = (smoothAmp * spectralWeight) + (if (smoothAmp > 0.05f) phaseOffset else 0.0f)
                    raw.coerceIn(0.08f, 1.0f)
                } else {
                    0.06f // Idle baseline tick
                }

                val barHeight = maxOf(4.dp.toPx(), totalHeight * barHeightFactor)
                val x = i * (barWidth + spacing)
                val y = (totalHeight - barHeight) / 2f

                // Dynamic gradient based on amplitude peak
                val barGradient = if (smoothAmp > 0.75f) {
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFEF4444), Color(0xFFF59E0B)),
                        startY = y,
                        endY = y + barHeight
                    )
                } else if (isRecording) {
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF38BDF8), Color(0xFF10B981)),
                        startY = y,
                        endY = y + barHeight
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF334155), Color(0xFF1E293B)),
                        startY = y,
                        endY = y + barHeight
                    )
                }

                drawRoundRect(
                    brush = barGradient,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = cornerRadius
                )
            }
        }
    }
}

/**
 * Backward-compatible lambda-based provider overload for AmplitudeVisualizer.
 */
@Composable
fun AmplitudeVisualizer(
    amplitudeProvider: () -> Float,
    barColor: Color = Color(0xFF38BDF8),
    modifier: Modifier = Modifier,
    barCount: Int = 10
) {
    val amp = amplitudeProvider()
    AmplitudeVisualizer(
        amplitude = amp,
        isRecording = amp > 0.005f,
        modifier = modifier,
        barCount = barCount
    )
}
