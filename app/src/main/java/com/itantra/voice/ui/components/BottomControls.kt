package com.itantra.voice.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.itantra.voice.ui.theme.Link
import com.itantra.voice.ui.theme.Signal

const val EMERGENCY_TOGGLE_TAG = "emergency_toggle"

/**
 * Emergency arming on the left, translation rating on the right.
 *
 * Arming is a persistent toggle rather than a momentary action because the SIH26173
 * brief treats alert traffic as a mode of operation: everything transmitted while it is
 * armed is flagged ALERT and announced at alarm volume on the receiving handset.
 */
@Composable
fun BottomControls(
    isEmergencyArmed: Boolean,
    onToggleEmergency: () -> Unit,
    canRateTranslation: Boolean,
    feedbackSubmitted: Boolean?,
    onFeedback: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isLocationSharing: Boolean = false,
    hasLocationFix: Boolean = false,
    onToggleLocation: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmergencyToggle(isArmed = isEmergencyArmed, onToggle = onToggleEmergency)
            Spacer(Modifier.width(14.dp))
            LocationToggle(
                isEnabled = isLocationSharing,
                hasFix = hasLocationFix,
                onToggle = onToggleLocation
            )
        }

        AnimatedVisibility(visible = canRateTranslation) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RatingButton(
                    positive = true,
                    selected = feedbackSubmitted == true,
                    enabled = feedbackSubmitted == null,
                    onClick = { onFeedback(true) }
                )
                Spacer(Modifier.width(4.dp))
                RatingButton(
                    positive = false,
                    selected = feedbackSubmitted == false,
                    enabled = feedbackSubmitted == null,
                    onClick = { onFeedback(false) }
                )
            }
        }
    }
}

@Composable
private fun EmergencyToggle(isArmed: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .then(
                if (isArmed) {
                    Modifier.background(Signal)
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                }
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(EMERGENCY_TOGGLE_TAG)
            .semantics {
                contentDescription = if (isArmed) "Emergency mode armed" else "Arm emergency mode"
            }
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = if (isArmed) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = if (isArmed) "EMERGENCY ARMED" else "EMERGENCY",
            style = MaterialTheme.typography.labelSmall,
            color = if (isArmed) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RatingButton(
    positive: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val tint = when {
        selected && positive -> Link
        selected && !positive -> Signal
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Icon(
        imageVector = if (positive) Icons.Default.ThumbUp else Icons.Default.ThumbDown,
        contentDescription = if (positive) "Good translation" else "Poor translation",
        tint = tint,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp)
            .size(18.dp)
    )
}
