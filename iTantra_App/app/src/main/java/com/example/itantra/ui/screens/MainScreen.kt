package com.example.itantra.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

    LaunchedEffect(hasRecordAudioPermission) {
        viewModel.onPermissionResult(hasRecordAudioPermission)
    }

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Stage 1 Audio Capture Subsystem & Debug HUD
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

            // 2. Push-To-Talk Button (Stage 1 Core Interaction)
            PttButton(
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

            // 3. Subsystem Health Indicators
            SubsystemStatusIndicators(status = state.subsystems)

            // 4. Connection Status Card
            ConnectionStatusCard(
                status = state.connectionStatus,
                transportType = state.transportType,
                deviceName = state.connectedDeviceName,
                deviceAddress = state.connectedDeviceAddress,
                onConnectOrScanClick = { viewModel.onConnectOrScanClick() },
                onDisconnectClick = { viewModel.onDisconnectClick() }
            )

            // 5. Multilingual Selection (10 SIH Languages)
            DualLanguageSelector(
                inputLanguage = state.inputLanguage,
                outputLanguage = state.outputLanguage,
                onInputLanguageSelected = { viewModel.onInputLanguageSelected(it) },
                onOutputLanguageSelected = { viewModel.onOutputLanguageSelected(it) },
                onSwapLanguages = { viewModel.onSwapLanguages() }
            )

            // 6. Emergency Alert Toggle (ISRO/INCOIS Distress Mode)
            EmergencyAlertToggle(
                alertState = state.emergencyAlert,
                onToggle = { viewModel.onEmergencyAlertToggled(it) }
            )

            // 7. Transmission Mode Switch
            TransmissionModeSwitch(
                currentMode = state.transmissionMode,
                onModeChanged = { viewModel.onTransmissionModeChanged(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
