package com.itantra.voice.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itantra.voice.data.Language
import com.itantra.voice.ui.theme.Link
import com.itantra.voice.ui.theme.Signal

/**
 * The conversation surface: what was heard, and what will be spoken.
 *
 * The output is weighted heavier than the input because it is the thing the operator
 * verifies before transmitting. A hairline, not a card, separates them — the screen
 * carries no elevation anywhere.
 */
@Composable
fun TranscriptPanel(
    sourceLanguage: Language,
    sourceText: String,
    targetLanguage: Language,
    outputText: String,
    isRemote: Boolean,
    isAlert: Boolean,
    canReplay: Boolean,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier,
    senderLocation: com.itantra.voice.location.GeoPoint? = null,
    bearingToSender: String? = null,
    translationSkipped: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        TranscriptBlock(
            label = if (isRemote) "RECEIVED" else "HEARD · ${sourceLanguage.displayName.uppercase()}",
            labelColor = if (isRemote) Link else MaterialTheme.colorScheme.onSurfaceVariant,
            text = sourceText,
            placeholder = if (isRemote) {
                "Waiting for the paired device…"
            } else {
                "Hold the button and speak in ${sourceLanguage.displayName}."
            },
            emphasised = false,
            modifier = Modifier.weight(1f)
        )

        // Sits with the received message: it describes where that message came from.
        SenderLocationRow(
            senderLocation = senderLocation,
            bearingToSender = bearingToSender
        )

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.2f),
            verticalAlignment = Alignment.Top
        ) {
            TranscriptBlock(
                label = when {
                    isAlert -> "EMERGENCY ALERT"
                    // Say "relayed" rather than "speaking English" when nothing translated
                    // it: the text is still in the source language.
                    translationSkipped -> "RELAYED · ${sourceLanguage.displayName.uppercase()} · NOT TRANSLATED"
                    else -> "SPEAKING · ${targetLanguage.displayName.uppercase()}"
                },
                labelColor = if (isAlert) Signal else MaterialTheme.colorScheme.onSurfaceVariant,
                text = outputText,
                placeholder = "The translation appears here.",
                emphasised = true,
                modifier = Modifier.weight(1f)
            )

            if (canReplay) {
                IconButton(
                    onClick = onReplay,
                    modifier = Modifier
                        .size(40.dp)
                        .semantics { contentDescription = "Replay last message" }
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TranscriptBlock(
    label: String,
    labelColor: androidx.compose.ui.graphics.Color,
    text: String,
    placeholder: String,
    emphasised: Boolean,
    modifier: Modifier = Modifier
) {
    val hasText = text.isNotBlank()

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (hasText) text else placeholder,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (emphasised && hasText) FontWeight.Medium else FontWeight.Normal,
            color = if (hasText) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .fillMaxWidth()
                // A long transcript scrolls inside its own block so it can never push
                // the push-to-talk button off the bottom of the screen.
                .weight(1f)
                .verticalScroll(rememberScrollState())
        )
    }
}
