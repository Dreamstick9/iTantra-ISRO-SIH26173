package com.itantra.voice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itantra.voice.ui.components.AppHeader
import com.itantra.voice.ui.components.ErrorBanner
import com.itantra.voice.ui.components.LanguageSelectorSection
import com.itantra.voice.ui.components.PttButton
import com.itantra.voice.ui.components.QuickFeedbackBar
import com.itantra.voice.ui.components.TelemetryBar
import com.itantra.voice.ui.components.TextCardsSection

/**
 * Single-Screen Compose transceiver UI for iTantra.
 * Implements strict visual hierarchy:
 * 1. App Header & Status Chip
 * 2. Telemetry Bar (latency readouts)
 * 3. Error Banner (animated expandable card)
 * 4. Language Selectors (Source & Target with swap button)
 * 5. Dual Text Cards (Source recognized text & Target translated text + Replay)
 * 6. Quick Feedback Bar (👍 / 👎)
 * 7. Tactile Push-to-Talk (PTT) Button
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onRequirePermission: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section (Header + Telemetry + Error Banner + Language Selectors)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.Top
            ) {
                AppHeader(state = uiState.state)

                Spacer(modifier = Modifier.height(4.dp))

                TelemetryBar(latencies = uiState.latencies)

                Spacer(modifier = Modifier.height(8.dp))

                ErrorBanner(
                    errorMessage = uiState.errorMessage,
                    onDismiss = { viewModel.onDismissError() },
                    onRetryPermission = onRequirePermission
                )

                Spacer(modifier = Modifier.height(8.dp))

                LanguageSelectorSection(
                    sourceLanguage = uiState.sourceLanguage,
                    targetLanguage = uiState.targetLanguage,
                    isEnabled = !uiState.isBusy && uiState.state != PttState.RECORDING,
                    onSourceChange = { viewModel.onSourceLanguageChange(it) },
                    onTargetChange = { viewModel.onTargetLanguageChange(it) },
                    onSwap = { viewModel.onSwapLanguages() }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Text Display Cards
                TextCardsSection(
                    sourceLanguage = uiState.sourceLanguage,
                    sourceTranscript = uiState.sourceTranscript,
                    targetLanguage = uiState.targetLanguage,
                    translatedText = uiState.translatedText,
                    hasAudioToReplay = uiState.hasAudioToReplay,
                    isPlaying = uiState.state == PttState.PLAYING,
                    isBusy = uiState.isBusy,
                    onReplayAudio = { viewModel.onReplayAudio() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Feedback Bar
                QuickFeedbackBar(
                    feedbackSubmitted = uiState.feedbackSubmitted,
                    isEnabled = uiState.translatedText.isNotBlank() && !uiState.isBusy && uiState.state != PttState.RECORDING,
                    onFeedback = { viewModel.onFeedback(it) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Section: Tactile Push-to-Talk (PTT) Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                PttButton(
                    state = uiState.state,
                    isHolding = uiState.isHolding,
                    amplitude = uiState.amplitude,
                    isEnabled = !uiState.isBusy,
                    onPress = {
                        if (!uiState.micPermissionGranted) {
                            onRequirePermission()
                        } else {
                            viewModel.onPttPress()
                        }
                    },
                    onRelease = {
                        if (uiState.micPermissionGranted) {
                            viewModel.onPttRelease()
                        }
                    }
                )
            }
        }
    }
}
