package com.itantra.voice.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itantra.voice.transport.TransportConnectionState
import com.itantra.voice.ui.theme.Link
import com.itantra.voice.ui.theme.Signal

/**
 * Single-line status strip: link state on the left, active speech engine on the right.
 *
 * A filled dot carries the link state rather than a coloured pill, so green appears on
 * screen only when the transceiver is genuinely paired.
 */
@Composable
fun StatusBar(
    transportState: TransportConnectionState,
    peerName: String?,
    engineName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLinked = transportState == TransportConnectionState.CONNECTED
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val dotColor by animateColorAsState(
        targetValue = when (transportState) {
            TransportConnectionState.CONNECTED -> Link
            TransportConnectionState.ERROR -> Signal
            else -> muted
        },
        label = "linkDot"
    )

    val label = when (transportState) {
        TransportConnectionState.CONNECTED -> peerName?.let { "LINKED · $it" } ?: "LINKED"
        TransportConnectionState.CONNECTING -> "CONNECTING"
        TransportConnectionState.DISCOVERING -> "SCANNING"
        TransportConnectionState.DISCONNECTING -> "CLOSING"
        TransportConnectionState.ERROR -> "LINK ERROR"
        TransportConnectionState.DISCONNECTED -> "NO LINK · TAP TO PAIR"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .semantics { contentDescription = "Link status: $label" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isLinked) Link else muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (engineName.isNotBlank()) {
            Text(
                text = engineName.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = muted,
                maxLines = 1
            )
        }
    }
}
