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
            // Android 12 splits location into precise/approximate and rejects a FINE-only
            // request; both must be asked for together or the dialog never appears.
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    /**
     * True when Wi-Fi Direct discovery is permitted.
     *
     * Below Android 13 the user may grant only approximate location, which is still
     * sufficient for peer discovery, so *any* granted location permission counts rather
     * than requiring all of them.
     */
    fun hasWifiDirectPermissions(context: Context): Boolean {
        val permissions = getRequiredWifiDirectPermissions()
        return permissions.any { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getAllRequiredPermissions(): Array<String> {
        return arrayOf(Manifest.permission.RECORD_AUDIO) + getRequiredWifiDirectPermissions()
    }

    /** True when the microphone is granted and Wi-Fi Direct discovery is permitted. */
    fun hasAllRequiredPermissions(context: Context): Boolean {
        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        return micGranted && hasWifiDirectPermissions(context)
    }
}
