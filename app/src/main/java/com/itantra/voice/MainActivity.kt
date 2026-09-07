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
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.itantra.voice.transport.CompositeTransportEngine
import com.itantra.voice.transport.bluetooth.BluetoothTransportEngine
import com.itantra.voice.transport.security.BluetoothPermissionHelper
import com.itantra.voice.transport.wifidirect.WifiDirectTransportEngine
import com.itantra.voice.ui.MainScreen
import com.itantra.voice.ui.MainViewModel
import com.itantra.voice.ui.theme.ITantraTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var compositeEngine: CompositeTransportEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        viewModel.initAudioPlayer(cacheDir)
        viewModel.initFeedbackRepository(filesDir)

        val wifiEngine = WifiDirectTransportEngine(
            context = applicationContext,
            scope = lifecycleScope
        )
        val bluetoothEngine = BluetoothTransportEngine(
            context = applicationContext,
            scope = lifecycleScope
        )
        val composite = CompositeTransportEngine(
            wifiEngine = wifiEngine,
            bluetoothEngine = bluetoothEngine,
            scope = lifecycleScope
        )
        compositeEngine = composite
        viewModel.setTransportEngine(composite)

        setContent {
            ITantraTheme {
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val micGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
                    viewModel.onPermissionResult(micGranted)
                }

                LaunchedEffect(Unit) {
                    val allRequired = BluetoothPermissionHelper.getAllRequiredAppPermissions()
                    val hasAll = BluetoothPermissionHelper.hasAllRequiredAppPermissions(this@MainActivity)
                    val micGranted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    viewModel.onPermissionResult(micGranted)

                    if (!hasAll) {
                        permissionLauncher.launch(allRequired)
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        onRequirePermission = {
                            permissionLauncher.launch(BluetoothPermissionHelper.getAllRequiredAppPermissions())
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeEngine?.release()
    }
}
