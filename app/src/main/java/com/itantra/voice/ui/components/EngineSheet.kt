package com.itantra.voice.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.itantra.voice.ui.theme.Link
import com.itantra.voice.ui.theme.Signal

const val ENGINE_SHEET_TAG = "engine_sheet"
const val ENGINE_KEY_FIELD_TAG = "engine_key_field"
const val ENGINE_SAVE_TAG = "engine_save"

/**
 * Speech-engine settings.
 *
 * Exists so one distributed APK works for an operator who has a Sarvam key but no offline
 * voice pack, without rebuilding. Reached by tapping the engine name in the status bar.
 *
 * Not shown when the build was pinned offline at compile time
 * (`BuildConfig.FORCE_OFFLINE`) — that build has no cloud client to configure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineSheet(
    currentEngine: String,
    savedKey: String,
    preferOffline: Boolean,
    onSave: (key: String, preferOffline: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var keyText by remember { mutableStateOf(savedKey) }
    var offline by remember { mutableStateOf(preferOffline) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag(ENGINE_SHEET_TAG)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "SPEECH ENGINE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = currentEngine.ifBlank { "Not resolved" },
                style = MaterialTheme.typography.titleMedium,
                color = if (currentEngine.equals("On-device", ignoreCase = true)) Link
                else MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Force offline",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Never use the network. Needs an offline voice pack installed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = offline,
                    onCheckedChange = { offline = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = Link
                    )
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Sarvam API key",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Optional. Set this to use the cloud engine when no offline voice pack is installed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = keyText,
                onValueChange = { keyText = it },
                singleLine = true,
                enabled = !offline,
                placeholder = { Text("paste key") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ENGINE_KEY_FIELD_TAG)
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "CANCEL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = { onSave(keyText, offline) },
                    modifier = Modifier.testTag(ENGINE_SAVE_TAG)
                ) {
                    Text(
                        text = "SAVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Signal
                    )
                }
            }
        }
    }
}
