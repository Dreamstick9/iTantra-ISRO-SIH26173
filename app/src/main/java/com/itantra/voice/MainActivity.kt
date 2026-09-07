package com.itantra.voice

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.itantra.voice.ui.MainScreen
import com.itantra.voice.ui.MainViewModel
import com.itantra.voice.ui.theme.ITantraTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        viewModel.initAudioPlayer(cacheDir)
        viewModel.initFeedbackRepository(filesDir)

        setContent {
            ITantraTheme {
                var hasRecordAudioPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
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

                LaunchedEffect(Unit) {
                    val isGranted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    hasRecordAudioPermission = isGranted
                    viewModel.onPermissionResult(isGranted)
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        onRequirePermission = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}
