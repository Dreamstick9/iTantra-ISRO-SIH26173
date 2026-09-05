package org.isro.itantra.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.exp

/**
 * Real-time audio waveform visualizer executing purely in the GPU Draw pass.
 * Never triggers Compose recomposition loops during high-frequency audio callbacks.
 */
@Composable
fun AmplitudeVisualizer(
    amplitudeProvider: () -> Float,
    barColor: Color = Color(0xFFFF5722),
    modifier: Modifier = Modifier,
    barCount: Int = 9
) {
    Canvas(modifier = modifier) {
        val amp = amplitudeProvider().coerceIn(0.04f, 1.0f)
        val canvasWidth = size.width
        val canvasHeight = size.height
        val barSpacing = 4.dp.toPx()
        val totalSpacing = barSpacing * (barCount - 1)
        val barWidth = (canvasWidth - totalSpacing) / barCount
        val centerY = canvasHeight / 2f
        val maxBarHeight = canvasHeight * 0.9f
        val minBarHeight = 4.dp.toPx()

        val centerIdx = (barCount - 1) / 2f
        val sigma = 1.8f

        for (i in 0 until barCount) {
            val dist = abs(i - centerIdx)
            val weight = exp(-(dist * dist) / (2 * sigma * sigma))

            val currentHeight = minBarHeight + (maxBarHeight - minBarHeight) * amp * weight
            val x = i * (barWidth + barSpacing)
            val y = centerY - (currentHeight / 2f)

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, currentHeight),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}
