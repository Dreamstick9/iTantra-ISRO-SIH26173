package com.itantra.voice.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun QuickFeedbackBar(
    feedbackSubmitted: Boolean?,
    isEnabled: Boolean,
    onFeedback: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (feedbackSubmitted) {
                    true -> "Thank you for the feedback! (Accurate)"
                    false -> "Feedback recorded. We'll improve."
                    null -> "Rate translation quality:"
                },
                style = MaterialTheme.typography.labelSmall,
                color = when (feedbackSubmitted) {
                    true -> Color(0xFF4CAF50)
                    false -> MaterialTheme.colorScheme.error
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (feedbackSubmitted != null) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Thumbs Up Button
                val isPositiveSelected = feedbackSubmitted == true
                FilledTonalButton(
                    onClick = { onFeedback(true) },
                    enabled = isEnabled,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isPositiveSelected) Color(0xFF4CAF50).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isPositiveSelected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ThumbUp,
                        contentDescription = "Thumbs Up",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "👍", fontSize = 11.sp)
                }

                // Thumbs Down Button
                val isNegativeSelected = feedbackSubmitted == false
                FilledTonalButton(
                    onClick = { onFeedback(false) },
                    enabled = isEnabled,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isNegativeSelected) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isNegativeSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ThumbDown,
                        contentDescription = "Thumbs Down",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "👎", fontSize = 11.sp)
                }
            }
        }
    }
}
