package com.itantra.voice.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.itantra.voice.ui.theme.Signal

const val NOTICE_BAR_TAG = "notice_bar"

/**
 * Full-width red notice for anything that blocks transmitting.
 *
 * Tapping the message itself retries the underlying action (typically re-requesting
 * the microphone permission), so the operator never has to hunt for a second control.
 */
@Composable
fun NoticeBar(
    message: String?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = message != null,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        val text = message.orEmpty()
        val isPermissionIssue = text.contains("permission", ignoreCase = true)

        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(Signal)
                .clickable(enabled = isPermissionIssue) { onRetry() }
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag(NOTICE_BAR_TAG)
                .semantics { contentDescription = "Notice: $text" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onDismiss)
            )
        }
    }
}
