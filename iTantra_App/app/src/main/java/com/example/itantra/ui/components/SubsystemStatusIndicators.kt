package com.example.itantra.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.itantra.data.SubsystemState
import com.example.itantra.data.SubsystemStatus

@Composable
fun SubsystemStatusIndicators(
    status: SubsystemStatus,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "NEURAL & AUDIO SUBSYSTEM HEALTH",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SubsystemBadge(
                    name = "AUDIO (16kHz PCM)",
                    state = status.audio,
                    modifier = Modifier.weight(1f)
                )
                SubsystemBadge(
                    name = "STT (AI4Bharat)",
                    state = status.stt,
                    modifier = Modifier.weight(1f)
                )
                SubsystemBadge(
                    name = "TTS (Indic-TTS)",
                    state = status.tts,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun SubsystemBadge(
    name: String,
    state: SubsystemState,
    modifier: Modifier = Modifier
) {
    val (color, label) = when (state) {
        SubsystemState.READY -> Color(0xFF00E676) to "READY"
        SubsystemState.ACTIVE -> Color(0xFF00D2FF) to "ACTIVE"
        SubsystemState.INITIALIZING -> Color(0xFFFFB300) to "INIT"
        SubsystemState.ERROR -> Color(0xFFFF1744) to "ERROR"
        SubsystemState.OFFLINE -> Color(0xFF757575) to "OFFLINE"
    }

    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                fontSize = 11.sp
            )
        }
    }
}
