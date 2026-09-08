package com.itantra.voice.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itantra.voice.location.GeoPoint
import com.itantra.voice.ui.theme.Link

const val SENDER_LOCATION_TAG = "sender_location"
const val LOCATION_TOGGLE_TAG = "location_toggle"

/**
 * The sender's position, shown under a received message.
 *
 * Coordinates plus the distance and compass bearing from here, all computed on-device
 * from the two fixes — no map tiles, no geocoding, no network. In a rescue this is the
 * payload that matters, so it is rendered in green (the "received" colour) and tapping it
 * copies the coordinates for handing to a coordinator.
 */
@Composable
fun SenderLocationRow(
    senderLocation: GeoPoint?,
    bearingToSender: String?,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current

    AnimatedVisibility(visible = senderLocation != null) {
        val point = senderLocation ?: return@AnimatedVisibility
        val coordinates = point.format()

        Column(
            modifier = modifier
                .fillMaxWidth()
                .clickable { clipboard.setText(AnnotatedString(coordinates)) }
                .padding(top = 10.dp)
                .testTag(SENDER_LOCATION_TAG)
                .semantics {
                    contentDescription = "Sender position $coordinates" +
                        (bearingToSender?.let { ", $it away" } ?: "")
                }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Link,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "SENDER POSITION",
                    style = MaterialTheme.typography.labelSmall,
                    color = Link
                )
                bearingToSender?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "· $it AWAY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = coordinates,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            point.accuracyMetres?.let { accuracy ->
                Text(
                    text = "±${accuracy.toInt()} m · tap to copy",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Compact indicator of whether this device is attaching its own position to
 * transmissions, and whether it has a fix yet. Tapping toggles sharing.
 */
@Composable
fun LocationToggle(
    isEnabled: Boolean,
    hasFix: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val label = when {
        !isEnabled -> "GPS OFF"
        hasFix -> "GPS ON"
        else -> "GPS…"
    }

    Row(
        modifier = modifier
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp)
            .testTag(LOCATION_TOGGLE_TAG)
            .semantics { contentDescription = "Location sharing: $label" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = if (isEnabled) Icons.Default.LocationOn else Icons.Default.LocationOff,
            contentDescription = null,
            tint = if (isEnabled && hasFix) Link else muted,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isEnabled && hasFix) Link else muted
        )
    }
}
