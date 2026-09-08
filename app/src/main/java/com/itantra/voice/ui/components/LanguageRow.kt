package com.itantra.voice.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itantra.voice.data.Language

/**
 * Source and target language with a swap control between them.
 *
 * Rendered as plain text rather than boxed dropdowns: the languages are read far more
 * often than they are changed, so they should look like a heading, not a form.
 */
@Composable
fun LanguageRow(
    source: Language,
    target: Language,
    isEnabled: Boolean,
    onSourceChange: (Language) -> Unit,
    onTargetChange: (Language) -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        LanguagePicker(
            label = "FROM",
            selected = source,
            isEnabled = isEnabled,
            onSelect = onSourceChange,
            alignment = Alignment.Start,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = onSwap,
            enabled = isEnabled,
            modifier = Modifier
                .size(44.dp)
                .semantics { contentDescription = "Swap languages" }
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LanguagePicker(
            label = "TO",
            selected = target,
            isEnabled = isEnabled,
            onSelect = onTargetChange,
            alignment = Alignment.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LanguagePicker(
    label: String,
    selected: Language,
    isEnabled: Boolean,
    onSelect: (Language) -> Unit,
    alignment: Alignment.Horizontal,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val textAlign = if (alignment == Alignment.End) TextAlign.End else TextAlign.Start

    Box(modifier = modifier) {
        Column(
            horizontalAlignment = alignment,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = isEnabled) { expanded = true }
                .padding(vertical = 6.dp)
                .semantics {
                    contentDescription = "$label language: ${selected.displayName}"
                }
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = selected.nativeName,
                style = MaterialTheme.typography.titleMedium,
                color = if (isEnabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = textAlign,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            Language.entries.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${language.nativeName}  ·  ${language.displayName}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = {
                        onSelect(language)
                        expanded = false
                    }
                )
            }
        }
    }
}
