package com.example.itantra.service

import kotlinx.coroutines.flow.StateFlow

enum class ServiceState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}

interface ITantraService {
    val serviceState: StateFlow<ServiceState>
    fun startTransceiver()
    fun stopTransceiver()
    fun isRunning(): Boolean
}

interface ServiceController {
    val isServiceRunning: StateFlow<Boolean>
    fun startBackgroundService()
    fun stopBackgroundService()
}
