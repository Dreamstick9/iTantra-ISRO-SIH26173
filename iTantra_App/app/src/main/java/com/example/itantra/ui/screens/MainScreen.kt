package com.example.itantra.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            // 1. Connection Status Card
            ConnectionStatusCard(
                status = state.connectionStatus,
                transportType = state.transportType,
                deviceName = state.connectedDeviceName,
                deviceAddress = state.connectedDeviceAddress,
                onConnectOrScanClick = { viewModel.onConnectOrScanClick() },
                onDisconnectClick = { viewModel.onDisconnectClick() }
            )

            // 2. Subsystem Health Indicators
            SubsystemStatusIndicators(status = state.subsystems)

            // 3. Transmission Mode Switch (PTT vs Phone)
            TransmissionModeSwitch(
                currentMode = state.transmissionMode,
                onModeChanged = { viewModel.onTransmissionModeChanged(it) }
            )

            // 4. Multilingual Selection (10 SIH Languages)
            DualLanguageSelector(
                inputLanguage = state.inputLanguage,
                outputLanguage = state.outputLanguage,
                onInputLanguageSelected = { viewModel.onInputLanguageSelected(it) },
                onOutputLanguageSelected = { viewModel.onOutputLanguageSelected(it) },
                onSwapLanguages = { viewModel.onSwapLanguages() }
            )

            // 5. Emergency Alert Toggle (ISRO/INCOIS Distress Mode)
            EmergencyAlertToggle(
                alertState = state.emergencyAlert,
                onToggle = { viewModel.onEmergencyAlertToggled(it) }
            )

            // 6. Push-To-Talk Button
            PttButton(
                pttState = state.pttState,
                isLocked = state.isPttLocked,
                isEmergency = state.emergencyAlert.isActive,
                onPressStart = { viewModel.onPttPressed() },
                onPressEnd = { viewModel.onPttReleased() },
                onLockToggle = { viewModel.onPttLockToggled() }
            )

            // 7. Latency & Telemetry HUD Card
            LatencyStatsCard(metrics = state.lastLatencyMetrics)

            // 8. Transcript / Received Messages Area
            ReceivedMessagesArea(
                messages = state.messages,
                onPlayAudio = { viewModel.onPlayMessageAudio(it) },
                onSimulateReceive = { viewModel.simulateReceiveMessage() }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
