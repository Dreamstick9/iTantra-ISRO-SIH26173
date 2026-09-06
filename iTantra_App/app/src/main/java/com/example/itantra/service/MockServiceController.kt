package com.example.itantra.service

import com.example.itantra.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MockServiceController : ServiceController {
    private val TAG = "MockServiceController"

    private val _isServiceRunning = MutableStateFlow(false)
    override val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    override fun startBackgroundService() {
        _isServiceRunning.value = true
        Logger.i(TAG, "Mock background service started.")
    }

    override fun stopBackgroundService() {
        _isServiceRunning.value = false
        Logger.i(TAG, "Mock background service stopped.")
    }
}
