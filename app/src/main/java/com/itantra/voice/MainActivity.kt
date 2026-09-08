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
import com.itantra.voice.pipeline.ConfigurableSpeechPipeline
import com.itantra.voice.pipeline.OnDeviceSpeechPipeline
import com.itantra.voice.pipeline.SarvamSpeechPipeline
import com.itantra.voice.pipeline.SpeechPipeline
import com.itantra.voice.pipeline.recognition.PlatformSpeechRecognizer
import com.itantra.voice.transport.security.WifiDirectPermissionHelper
import com.itantra.voice.transport.wifidirect.WifiDirectTransportEngine
import com.itantra.voice.settings.SettingsStore
import com.itantra.voice.ui.EngineSettings
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
    private lateinit var settings: SettingsStore

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
        settings = SettingsStore(applicationContext)
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
                        // A compile-time offline build has no cloud client to configure,
                        // so the settings affordance is hidden entirely.
                        engineSettings = if (BuildConfig.FORCE_OFFLINE) null else EngineSettings(
                            savedKey = { settings.sarvamKey },
                            preferOffline = { settings.preferOffline },
                            save = { key, offline ->
                                settings.sarvamKey = key
                                settings.preferOffline = offline
                            }
                        ),
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
     * Which engine leads is decided by configuration, not by a runtime probe:
     *
     * - `itantra.force.offline=true` pins the on-device engine and never touches the
     *   network. This is the build to demo for SIH26173, which mandates a fully offline
     *   pipeline; it is provably air-gapped regardless of what else is configured.
     * - Otherwise a real `sarvam.api.key` means the developer explicitly opted into the
     *   cloud engine, so it leads and on-device is the fallback.
     * - With no key at all, on-device leads and the app runs entirely offline.
     *
     * Preferring on-device whenever it merely *reports* availability does not work:
     * `SpeechRecognizer.isRecognitionAvailable()` is true on any phone that has a
     * recognition service, even when no offline language pack is installed for the
     * chosen language. That made the on-device engine always win and a configured
     * Sarvam key never get used, and recognition then failed at runtime with
     * "language pack not installed". A language pack's presence cannot be queried
     * without attempting recognition, so the choice is made from explicit
     * configuration instead of an unreliable probe.
     */
    private fun buildSpeechPipeline(): SpeechPipeline {
        val onDevice = OnDeviceSpeechPipeline(
            context = applicationContext,
            cacheDir = cacheDir,
            recognizer = PlatformSpeechRecognizer(applicationContext)
        )

        if (BuildConfig.FORCE_OFFLINE) {
            return onDevice.also { speechPipeline = it }
        }

        // A key entered in the app wins over one baked in at build time, so a single
        // distributed APK works for an operator who has a key but no offline voice pack.
        val resolveKey = {
            settings.sarvamKey.ifBlank { BuildConfig.SARVAM_API_KEY }
        }

        return ConfigurableSpeechPipeline(
            onDevice = onDevice,
            cloud = SarvamSpeechPipeline(apiKeyProvider = resolveKey),
            keyProvider = resolveKey,
            preferOffline = { settings.preferOffline }
        ).also { speechPipeline = it }
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
