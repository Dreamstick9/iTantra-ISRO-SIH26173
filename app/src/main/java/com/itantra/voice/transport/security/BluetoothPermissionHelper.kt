package com.itantra.voice.transport.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Handles version-specific runtime permission auditing for Bluetooth Classic
 * and unified permission auditing across Wi-Fi Direct and Audio.
 *
 * Android 12+ (API 31+): BLUETOOTH_SCAN, BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE
 * Android 11 and below: ACCESS_FINE_LOCATION (BLUETOOTH/BLUETOOTH_ADMIN are install-time)
 */
object BluetoothPermissionHelper {

    fun getRequiredBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun hasBluetoothPermissions(context: Context): Boolean {
        val permissions = getRequiredBluetoothPermissions()
        return permissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Returns all required permissions for the entire app:
     * - RECORD_AUDIO
     * - Wi-Fi Direct permissions
     * - Bluetooth permissions
     */
    fun getAllRequiredAppPermissions(): Array<String> {
        val perms = mutableSetOf(Manifest.permission.RECORD_AUDIO)

        // Wi-Fi Direct
        perms.addAll(WifiDirectPermissionHelper.getRequiredWifiDirectPermissions())

        // Bluetooth
        perms.addAll(getRequiredBluetoothPermissions())

        return perms.toTypedArray()
    }

    fun hasAllRequiredAppPermissions(context: Context): Boolean {
        return getAllRequiredAppPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }
}
