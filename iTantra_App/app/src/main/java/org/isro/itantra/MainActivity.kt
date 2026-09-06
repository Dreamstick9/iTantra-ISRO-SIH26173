package org.isro.itantra

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.isro.itantra.ui.MainViewModel
import org.isro.itantra.ui.PttState
import org.isro.itantra.ui.components.AmplitudeVisualizer
import org.isro.itantra.ui.components.OutgoingTransmissionCard
import org.isro.itantra.ui.components.PttButton
import org.isro.itantra.ui.components.TelemetryCard
import org.isro.itantra.ui.components.TtsControlCard
import org.isro.itantra.ui.theme.ITantraTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ITantraTheme {
                MainScreen(viewModel)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRecordPermission = granted
    }

    val pttState by viewModel.pttState.collectAsState()
    val amplitude by viewModel.amplitude.collectAsState()
    val stats by viewModel.telemetryStats.collectAsState()
    val hasAudio by viewModel.hasRecordedAudio.collectAsState()

    // Milestone 3 STT & Outgoing Transmission States
    val outgoingText by viewModel.outgoingText.collectAsState()
    val isTranscribing by viewModel.isTranscribing.collectAsState()
    val sttStatusMessage by viewModel.sttStatusMessage.collectAsState()

    // Milestone 2 TTS States
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val ttsInputText by viewModel.ttsInputText.collectAsState()
    val isSynthesizing by viewModel.isSynthesizing.collectAsState()
    val isPlayingTts by viewModel.isPlayingTts.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) } // Default to PTT Transceiver

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0A0F1D)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Title & Badges
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "iTantra",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "ISRO SIH26173 • Offline Neural Walkie-Talkie",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(text = "M1: MIC I/O", color = Color(0xFF38BDF8))
                    StatusBadge(text = "M2: OFFLINE TTS", color = Color(0xFF4ADE80))
                    StatusBadge(text = "M3: OFFLINE STT", color = Color(0xFFA78BFA))
                    StatusBadge(text = "ZERO CLOUD", color = Color(0xFFFBBF24))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dual-Station Mode Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color(0xFF0F172A),
                contentColor = Color(0xFF38BDF8),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = Color(0xFF38BDF8)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PTT Transceiver", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Indic TTS Station", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Shared ISRO Telemetry HUD
            TelemetryCard(stats = stats)

            Spacer(modifier = Modifier.height(10.dp))

            // Tab Content
            if (selectedTabIndex == 0) {
                // --- TAB 1: PTT TRANSCEIVER (MILESTONE 1 & 3) ---
                if (!hasRecordPermission) {
                    PermissionCard(
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = when (pttState) {
                                PttState.RECORDING -> "LIVE MICROPHONE INPUT (RECORDING & TRANSCRIBING)"
                                PttState.PLAYING -> "AUDIO PLAYBACK IN PROGRESS"
                                PttState.IDLE -> "AUDIO ENGINE READY (MIC ACTIVE)"
                            },
                            color = when (pttState) {
                                PttState.RECORDING -> Color(0xFFEF4444)
                                PttState.PLAYING -> Color(0xFF10B981)
                                PttState.IDLE -> Color(0xFF64748B)
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        AmplitudeVisualizer(
                            amplitudeProvider = { amplitude },
                            barColor = if (pttState == PttState.RECORDING) Color(0xFFEF4444) else Color(0xFF38BDF8),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        PttButton(
                            pttState = pttState,
                            amplitudeProvider = { amplitude },
                            onPttDown = { viewModel.onPttDown() },
                            onPttUp = { viewModel.onPttUp() },
                            onPttCancel = { viewModel.onPttCancel() }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Controls: Toggle Record & Replay
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewModel.toggleRecording() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (pttState == PttState.RECORDING) Color(0xFFDC2626) else Color(0xFF0284C7)
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (pttState == PttState.RECORDING) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (pttState == PttState.RECORDING) "STOP RECORDING" else "CLICK TO TALK",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            if (hasAudio && pttState == PttState.IDLE) {
                                OutlinedButton(
                                    onClick = { viewModel.replayLastAudio() },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFF38BDF8)
                                    ),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(
                                        brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF334155))
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Replay (${String.format("%.1f", stats.recordedDurationMs / 1000f)}s)",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // --- SENDER OUTGOING TRANSMISSION HUB (WHAT SENDER IS SENDING) ---
                        OutgoingTransmissionCard(
                            text = outgoingText,
                            isTranscribing = isTranscribing,
                            statusMessage = sttStatusMessage,
                            selectedLanguage = selectedLanguage,
                            onTextChanged = { viewModel.updateOutgoingText(it) },
                            onLanguageSelected = { viewModel.selectLanguage(it) },
                            onSynthesizeAndSpeak = { isEmergency -> viewModel.synthesizeOutgoingText(isEmergency) },
                            onClear = { viewModel.clearOutgoingText() }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            } else {
                // --- TAB 2: INDIC TTS STATION (MILESTONE 2) ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    TtsControlCard(
                        inputText = ttsInputText,
                        selectedLanguage = selectedLanguage,
                        isSynthesizing = isSynthesizing,
                        isPlaying = isPlayingTts,
                        onInputTextChanged = { viewModel.updateInputText(it) },
                        onLanguageSelected = { viewModel.selectLanguage(it) },
                        onEmergencyPresetSelected = { viewModel.selectEmergencyPreset(it) },
                        onSynthesizeAndSpeak = { isEmergency -> viewModel.synthesizeAndSpeak(isEmergency) },
                        onStopPlayback = { viewModel.stopTtsPlayback() }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun PermissionCard(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            tint = Color(0xFF38BDF8),
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Microphone Access Required",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "iTantra operates 100% offline. Microphone access is required to capture 16 kHz PCM voice notes for Walkie-Talkie transmission. No audio ever leaves your device.",
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF38BDF8),
                contentColor = Color(0xFF0A0F1D)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(imageVector = Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Enable Microphone", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}
