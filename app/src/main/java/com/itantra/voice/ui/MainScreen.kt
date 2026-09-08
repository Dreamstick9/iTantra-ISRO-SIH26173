package com.itantra.voice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itantra.voice.transport.TransportConnectionState
import com.itantra.voice.ui.components.BottomControls
import com.itantra.voice.ui.components.EngineSheet
import com.itantra.voice.ui.components.LanguageRow
import com.itantra.voice.ui.components.LatencyStrip
import com.itantra.voice.ui.components.NoticeBar
import com.itantra.voice.ui.components.PairingSheet
import com.itantra.voice.ui.components.StatusBar
import com.itantra.voice.ui.components.TalkButton
import com.itantra.voice.ui.components.TranscriptPanel

/**
 * Callbacks the screen needs to read and write speech-engine settings.
 *
 * Passed in rather than reached through the ViewModel so the ViewModel stays free of
 * Android storage types. Null when the build is pinned offline at compile time, in which
 * case there is nothing to configure and the affordance is hidden.
 */
data class EngineSettings(
    val savedKey: () -> String,
    val preferOffline: () -> Boolean,
    val save: (key: String, preferOffline: Boolean) -> Unit
)

/**
 * The whole app: one screen, read top to bottom.
 *
 *   status  ->  languages  ->  transcript  ->  talk button  ->  controls
 *
 * Everything above the talk button is information; the button is the only large target.
 * The transcript takes the flexible space and scrolls internally, so the button stays
 * anchored at the bottom on every screen size rather than being pushed off by long text.
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onRequirePermission: () -> Unit = {},
    engineSettings: EngineSettings? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showPairing by remember { mutableStateOf(false) }
    var showEngine by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            StatusBar(
                transportState = uiState.transportState,
                peerName = uiState.connectedPeer?.name,
                engineName = uiState.engineName,
                onClick = {
                    if (uiState.transportState == TransportConnectionState.DISCONNECTED) {
                        viewModel.onStartDiscovery()
                    }
                    showPairing = true
                },
                onEngineClick = if (engineSettings != null) {
                    { showEngine = true }
                } else null
            )

            NoticeBar(
                message = uiState.errorMessage,
                onDismiss = { viewModel.onDismissError() },
                onRetry = onRequirePermission
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Spacer(Modifier.height(20.dp))

            LanguageRow(
                source = uiState.sourceLanguage,
                target = uiState.targetLanguage,
                isEnabled = uiState.canRecord,
                onSourceChange = viewModel::onSourceLanguageChange,
                onTargetChange = viewModel::onTargetLanguageChange,
                onSwap = viewModel::onSwapLanguages
            )

            Spacer(Modifier.height(20.dp))

            // Flexible middle: absorbs leftover height so the button never moves.
            Box(modifier = Modifier.weight(1f)) {
                TranscriptPanel(
                    sourceLanguage = uiState.sourceLanguage,
                    sourceText = uiState.sourceTranscript,
                    targetLanguage = uiState.targetLanguage,
                    outputText = uiState.translatedText,
                    isRemote = uiState.isRemoteMessage,
                    isAlert = uiState.isAlertPlaying || uiState.isEmergencyMode,
                    canReplay = uiState.hasAudioToReplay,
                    onReplay = viewModel::onReplayAudio,
                    senderLocation = uiState.senderLocation,
                    bearingToSender = uiState.bearingToSender
                )
            }

            LatencyStrip(latencies = uiState.latencies)

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TalkButton(
                    state = uiState.state,
                    isHolding = uiState.isHolding,
                    amplitude = uiState.amplitude,
                    isEnabled = !uiState.isBusy,
                    onPress = {
                        if (uiState.micPermissionGranted) {
                            viewModel.onPttPress()
                        } else {
                            onRequirePermission()
                        }
                    },
                    onRelease = {
                        if (uiState.micPermissionGranted) viewModel.onPttRelease()
                    }
                )
            }

            Spacer(Modifier.height(20.dp))

            BottomControls(
                isEmergencyArmed = uiState.isEmergencyMode,
                onToggleEmergency = viewModel::onToggleEmergencyMode,
                canRateTranslation = uiState.translatedText.isNotBlank() && uiState.canRecord,
                feedbackSubmitted = uiState.feedbackSubmitted,
                onFeedback = viewModel::onFeedback,
                isLocationSharing = uiState.locationSharingEnabled,
                hasLocationFix = uiState.ownLocation != null,
                onToggleLocation = viewModel::onToggleLocationSharing
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showEngine && engineSettings != null) {
        EngineSheet(
            currentEngine = uiState.engineName,
            savedKey = engineSettings.savedKey(),
            preferOffline = engineSettings.preferOffline(),
            onSave = { key, offline ->
                engineSettings.save(key, offline)
                viewModel.refreshEngine()
                showEngine = false
            },
            onDismiss = { showEngine = false }
        )
    }

    if (showPairing) {
        PairingSheet(
            state = uiState.transportState,
            peers = uiState.discoveredPeers,
            connectedPeer = uiState.connectedPeer,
            onScan = viewModel::onStartDiscovery,
            onStopScan = viewModel::onStopDiscovery,
            onConnect = { peer ->
                viewModel.onConnectPeer(peer)
                showPairing = false
            },
            onDisconnect = {
                viewModel.onDisconnectTransport()
                showPairing = false
            },
            onDismiss = {
                if (uiState.transportState == TransportConnectionState.DISCOVERING) {
                    viewModel.onStopDiscovery()
                }
                showPairing = false
            }
        )
    }
}
