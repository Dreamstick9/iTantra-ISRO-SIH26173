package com.itantra.voice

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.itantra.voice.audio.AudioPlayer
import com.itantra.voice.audio.SystemAlarmVolumeController
import com.itantra.voice.pipeline.FallbackSpeechPipeline
import com.itantra.voice.pipeline.OnDeviceSpeechPipeline
import com.itantra.voice.pipeline.SarvamSpeechPipeline
import com.itantra.voice.pipeline.SpeechPipeline
import com.itantra.voice.pipeline.recognition.PlatformSpeechRecognizer
import com.itantra.voice.transport.security.WifiDirectPermissionHelper
import com.itantra.voice.transport.wifidirect.WifiDirectTransportEngine
import com.itantra.voice.ui.MainScreen
import com.itantra.voice.ui.MainViewModel
import com.itantra.voice.ui.theme.ITantraTheme

/**
 * Single activity hosting the transceiver.
 *
 * Assembles the speech pipeline here rather than inside the ViewModel so the ViewModel
 * stays free of Android framework types and remains unit-testable on the JVM.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var transportEngine: WifiDirectTransportEngine? = null
    private var speechPipeline: SpeechPipeline? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Route the app's volume keys to the stream the transceiver actually plays on,
        // so the operator can adjust playback without leaving the screen.
        volumeControlStream = AudioManager.STREAM_MUSIC

        viewModel.setAudioPlayer(
            AudioPlayer(
                cacheDir = cacheDir,
                alarmVolumeController = SystemAlarmVolumeController(applicationContext)
            )
        )
        viewModel.initFeedbackRepository(filesDir)
        viewModel.setSpeechPipeline(buildSpeechPipeline())

        val engine = WifiDirectTransportEngine(
            context = applicationContext,
            scope = lifecycleScope
        )
        transportEngine = engine
        viewModel.setTransportEngine(engine)

        setContent {
            ITantraTheme {
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    // A permission absent from the result map was already granted.
                    val micGranted = permissions[Manifest.permission.RECORD_AUDIO]
                        ?: hasMicPermission()
                    viewModel.onPermissionResult(micGranted)
                }

                LaunchedEffect(Unit) {
                    viewModel.onPermissionResult(hasMicPermission())
                    if (!WifiDirectPermissionHelper.hasAllRequiredPermissions(this@MainActivity)) {
                        permissionLauncher.launch(WifiDirectPermissionHelper.getAllRequiredPermissions())
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        onRequirePermission = {
                            permissionLauncher.launch(
                                WifiDirectPermissionHelper.getAllRequiredPermissions()
                            )
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }

    /**
     * Builds the speech pipeline.
     *
     * The on-device engine is preferred because SIH26173 mandates fully offline
     * operation; the Sarvam cloud engine is used only as a fallback, and only when a
     * real key has been configured in `local.properties`. With no key the composite
     * still works, entirely offline.
     */
    private fun buildSpeechPipeline(): SpeechPipeline {
        val onDevice = OnDeviceSpeechPipeline(
            context = applicationContext,
            cacheDir = cacheDir,
            recognizer = PlatformSpeechRecognizer(applicationContext)
        )
        val cloud = SarvamSpeechPipeline()
        return FallbackSpeechPipeline(preferred = onDevice, fallback = cloud)
            .also { speechPipeline = it }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        super.onDestroy()
        transportEngine?.release()
        speechPipeline?.release()
    }
}
