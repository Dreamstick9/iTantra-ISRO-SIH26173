package com.itantra.voice.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.voice.ui.PttState

@Composable
fun AppHeader(
    state: PttState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "iTantra",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "Neural Multilingual Transceiver",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        StatusChip(state = state)
    }
}

@Composable
fun StatusChip(
    state: PttState,
    modifier: Modifier = Modifier
) {
    val (statusLabel, statusColor) = when (state) {
        PttState.IDLE -> "IDLE" to Color(0xFF4CAF50)
        PttState.RECORDING -> "RECORDING" to Color(0xFFE53935)
        PttState.TRANSCRIBING -> "TRANSCRIBING" to Color(0xFF0288D1)
        PttState.TRANSLATING -> "TRANSLATING" to Color(0xFFFF9800)
        PttState.SYNTHESIZING -> "SYNTHESIZING" to Color(0xFF9C27B0)
        PttState.PLAYING -> "PLAYING" to Color(0xFF00897B)
        PttState.ERROR -> "ERROR" to Color(0xFFD32F2F)
    }

    val animatedColor by animateColorAsState(targetValue = statusColor, label = "statusColor")

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = animatedColor.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, animatedColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(animatedColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = animatedColor
            )
        }
    }
}
