package com.itantra.voice.transport.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Handles version-specific permission auditing for Wi-Fi Direct and Audio.
 *
 * Android 13+ (API 33+): NEARBY_WIFI_DEVICES
 * Android 12 and below: ACCESS_FINE_LOCATION
 */
object WifiDirectPermissionHelper {

    fun getRequiredWifiDirectPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun hasWifiDirectPermissions(context: Context): Boolean {
        val permissions = getRequiredWifiDirectPermissions()
        return permissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getAllRequiredPermissions(): Array<String> {
        return arrayOf(Manifest.permission.RECORD_AUDIO) + getRequiredWifiDirectPermissions()
    }

    fun hasAllRequiredPermissions(context: Context): Boolean {
        return getAllRequiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }
}
