package com.example.itantra.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.itantra.ui.MainViewModel
import com.example.itantra.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.appState.collectAsState()
    val diagnostics by viewModel.transportDiagnostics.collectAsState()
    val peers by viewModel.discoveredPeers.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    var hasRecordAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRecordAudioPermission = isGranted
        viewModel.onPermissionResult(isGranted)
    }

    var hasWifiDirectPermissions by remember {
        mutableStateOf(com.example.itantra.transport.security.WifiDirectPermissionHelper.hasRequiredPermissions(context))
    }

    val wifiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasWifiDirectPermissions = results.values.all { it }
        if (hasWifiDirectPermissions) {
            viewModel.onStartPeerDiscovery()
        }
    }

    LaunchedEffect(hasRecordAudioPermission) {
        viewModel.onPermissionResult(hasRecordAudioPermission)
    }

    var showAdvancedDiagnostics by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "iTantra Transceiver",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.statusMessage ?: "Offline Neural Transceiver Active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission Banner (if mic permission missing)
            if (!hasRecordAudioPermission) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Mic Permission",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Microphone permission is required for Push-To-Talk.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 1. Vertical Slice HUD (Active Status Banner + Latencies + Timestamps + Compression)
            VerticalSliceHUD(
                transceiverState = state.transceiverState,
                timestamps = state.verticalSliceTimestamps,
                metrics = state.lastLatencyMetrics,
                compressionMetrics = state.lastCompressionMetrics,
                continuousState = state.continuousModeState,
                transmissionMode = state.transmissionMode,
                isEmergencyAlert = state.isEmergencyPlaybackActive || state.emergencyAlert.isActive,
                alertPlaybackResult = state.lastAlertPlaybackResult
            )

            // 2. Multilingual Configuration (10 SIH Mandated Languages)
            DualLanguageSelector(
                inputLanguage = state.inputLanguage,
                outputLanguage = state.outputLanguage,
                onInputLanguageSelected = { viewModel.onInputLanguageSelected(it) },
                onOutputLanguageSelected = { viewModel.onOutputLanguageSelected(it) },
                onSwapLanguages = { viewModel.onSwapLanguages() }
            )

            // Mode Selector: Push-To-Talk vs Continuous Hands-Free (Silero VAD)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = state.transmissionMode == com.example.itantra.data.TransmissionMode.PUSH_TO_TALK,
                        onClick = { viewModel.onTransmissionModeChanged(com.example.itantra.data.TransmissionMode.PUSH_TO_TALK) },
                        label = {
                            Text(
                                "Push-To-Talk",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = state.transmissionMode == com.example.itantra.data.TransmissionMode.CONTINUOUS,
                        onClick = { viewModel.onTransmissionModeChanged(com.example.itantra.data.TransmissionMode.CONTINUOUS) },
                        label = {
                            Text(
                                "Continuous VAD",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Hearing, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 2. Active Interaction Area
            if (state.transmissionMode == com.example.itantra.data.TransmissionMode.PUSH_TO_TALK) {
                // Tactile Push-To-Talk Button (Single button interaction reflecting 6 states)
                PttButton(
                    transceiverState = state.transceiverState,
                    pttState = state.pttState,
                    isLocked = state.isPttLocked,
                    isEmergency = state.emergencyAlert.isActive,
                    onPressStart = {
                        if (!hasRecordAudioPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.onPttPressed()
                        }
                    },
                    onPressEnd = { viewModel.onPttReleased() },
                    onLockToggle = {
                        if (!hasRecordAudioPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.onPttLockToggled()
                        }
                    }
                )
            } else {
                // Continuous Hands-Free VAD Mode Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "SILERO VAD HANDS-FREE",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = state.continuousModeState.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = when (state.continuousModeState) {
                                        com.example.itantra.data.ContinuousModeState.IDLE -> MaterialTheme.colorScheme.surface
                                        com.example.itantra.data.ContinuousModeState.LISTENING -> Color(0xFF00B0FF).copy(alpha = 0.2f)
                                        com.example.itantra.data.ContinuousModeState.SPEECH_DETECTED -> Color(0xFFFFD600).copy(alpha = 0.2f)
                                        com.example.itantra.data.ContinuousModeState.RECORDING -> Color(0xFFFF1744).copy(alpha = 0.2f)
                                        com.example.itantra.data.ContinuousModeState.POSSIBLE_END -> Color(0xFFFF9100).copy(alpha = 0.2f)
                                        com.example.itantra.data.ContinuousModeState.FINALIZING -> Color(0xFFE040FB).copy(alpha = 0.2f)
                                        com.example.itantra.data.ContinuousModeState.TRANSCRIBING -> Color(0xFFFFB300).copy(alpha = 0.2f)
                                        com.example.itantra.data.ContinuousModeState.TRANSMITTING -> Color(0xFF00E5FF).copy(alpha = 0.2f)
                                    }
                                )
                            )
                        }

                        Text(
                            text = if (state.continuousModeState.isIdle) {
                                "Hands-free continuous mode is idle. Tap below to begin voice-activated streaming."
                            } else {
                                "Microphone streaming into Silero VAD. Utterance boundaries and silences are automatically segmented."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = {
                                if (!hasRecordAudioPermission) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    viewModel.onToggleContinuousMode()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.continuousModeState.isIdle) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color(0xFFFF1744)
                                }
                            )
                        ) {
                            Icon(
                                imageVector = if (state.continuousModeState.isIdle) Icons.Default.Hearing else Icons.Default.HearingDisabled,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.continuousModeState.isIdle) {
                                    "START HANDS-FREE LISTENING"
                                } else {
                                    "STOP HANDS-FREE LISTENING"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // 3. Conversation History & Transcript Log
            ReceivedMessagesArea(
                messages = state.messages,
                onPlayAudio = { viewModel.onPlayMessageAudio(it) },
                onSimulateReceive = { viewModel.simulateReceiveMessage() }
            )

            // 4. Wi-Fi Direct Phone-to-Phone Transport & Link Diagnostic HUD
            ConnectionStatusCard(
                status = state.connectionStatus,
                transportType = state.transportType,
                deviceName = state.connectedDeviceName,
                deviceAddress = state.connectedDeviceAddress,
                onConnectOrScanClick = {
                    if (!com.example.itantra.transport.security.WifiDirectPermissionHelper.hasRequiredPermissions(context)) {
                        wifiPermissionLauncher.launch(com.example.itantra.transport.security.WifiDirectPermissionHelper.getRequiredPermissions())
                    } else {
                        viewModel.onStartPeerDiscovery()
                    }
                },
                onDisconnectClick = { viewModel.onDisconnectTransport() }
            )

            WifiDirectDiagnosticCard(
                diagnostics = diagnostics,
                peers = peers,
                onStartDiscovery = {
                    if (!com.example.itantra.transport.security.WifiDirectPermissionHelper.hasRequiredPermissions(context)) {
                        wifiPermissionLauncher.launch(com.example.itantra.transport.security.WifiDirectPermissionHelper.getRequiredPermissions())
                    } else {
                        viewModel.onStartPeerDiscovery()
                    }
                },
                onConnectPeer = { peer ->
                    if (!com.example.itantra.transport.security.WifiDirectPermissionHelper.hasRequiredPermissions(context)) {
                        wifiPermissionLauncher.launch(com.example.itantra.transport.security.WifiDirectPermissionHelper.getRequiredPermissions())
                    } else {
                        viewModel.onConnectToPeer(peer)
                    }
                },
                onDisconnect = { viewModel.onDisconnectTransport() },
                onSendTextMessage = { text -> viewModel.onSendTextMessage(text) },
                onSendAlertMessage = { alert -> viewModel.onSendAlertMessage(alert) }
            )

            // 5. Tactical Settings & Subsystem Indicators

            EmergencyAlertToggle(
                alertState = state.emergencyAlert,
                onToggle = { viewModel.onEmergencyAlertToggled(it) }
            )

            SubsystemStatusIndicators(status = state.subsystems)

            TransmissionModeSwitch(
                currentMode = state.transmissionMode,
                onModeChanged = { viewModel.onTransmissionModeChanged(it) }
            )

            // 6. Advanced Engine Diagnostics (Neatly organized & collapsible to keep vertical slice uncluttered)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        onClick = { showAdvancedDiagnostics = !showAdvancedDiagnostics },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Diagnostics",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Advanced Subsystem Diagnostics",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                imageVector = if (showAdvancedDiagnostics) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showAdvancedDiagnostics) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showAdvancedDiagnostics,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Low-level Audio Capture HUD
                            AudioCaptureDebugCard(
                                pttState = state.pttState,
                                audioLevel = state.audioLevel,
                                debugInfo = state.lastAudioDebugInfo,
                                errorMessage = state.errorMessage,
                                hasPermission = hasRecordAudioPermission,
                                onRequestPermission = {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            )

                            // Offline STT Diagnostic Card
                            SttDiagnosticCard(
                                diagnosticState = state.sttDiagnostics
                            )

                            // Offline TTS Manual Test Card
                            TtsTestCard(
                                ttsState = state.ttsState,
                                onTextChanged = { viewModel.onTtsInputChanged(it) },
                                onSpeak = { viewModel.onSpeakTts(it) },
                                onStop = { viewModel.onStopTts() }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
