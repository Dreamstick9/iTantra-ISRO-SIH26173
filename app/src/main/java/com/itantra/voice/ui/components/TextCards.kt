package com.itantra.voice.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.voice.data.Language

@Composable
fun TextCardsSection(
    sourceLanguage: Language,
    sourceTranscript: String,
    targetLanguage: Language,
    translatedText: String,
    hasAudioToReplay: Boolean,
    isPlaying: Boolean,
    isBusy: Boolean,
    onReplayAudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Source Transcript Card
        SpeechTextCard(
            title = "INPUT SPEECH",
            language = sourceLanguage,
            text = sourceTranscript,
            placeholder = "Hold button and speak in ${sourceLanguage.displayName}...",
            onCopy = {
                if (sourceTranscript.isNotBlank()) {
                    copyToClipboard(context, "Source Transcript", sourceTranscript)
                }
            }
        )

        // Translated Text Card
        SpeechTextCard(
            title = "TRANSLATION",
            language = targetLanguage,
            text = translatedText,
            placeholder = "Translated text will appear here...",
            hasAudioToReplay = hasAudioToReplay,
            isPlaying = isPlaying,
            isBusy = isBusy,
            onReplayAudio = onReplayAudio,
            onCopy = {
                if (translatedText.isNotBlank()) {
                    copyToClipboard(context, "Translated Text", translatedText)
                }
            }
        )
    }
}

@Composable
private fun SpeechTextCard(
    title: String,
    language: Language,
    text: String,
    placeholder: String,
    hasAudioToReplay: Boolean = false,
    isPlaying: Boolean = false,
    isBusy: Boolean = false,
    onReplayAudio: (() -> Unit)? = null,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${language.displayName.uppercase()} · ${language.bcp47Code}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onReplayAudio != null) {
                        FilledTonalButton(
                            onClick = onReplayAudio,
                            enabled = hasAudioToReplay && !isBusy,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 10.dp,
                                vertical = 4.dp
                            ),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Replay Audio",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPlaying) "Playing..." else "Replay Audio",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    if (text.isNotBlank()) {
                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Text",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Text Content with scroll support for long texts
            val hasContent = text.isNotBlank()
            Text(
                text = if (hasContent) text else placeholder,
                style = if (hasContent) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                fontWeight = if (hasContent) FontWeight.Medium else FontWeight.Normal,
                color = if (hasContent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                lineHeight = 22.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}
