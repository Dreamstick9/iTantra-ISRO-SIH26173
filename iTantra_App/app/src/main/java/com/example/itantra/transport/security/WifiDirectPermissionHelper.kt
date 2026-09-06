package com.example.itantra.transport.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.itantra.util.Logger

/**
 * Security and runtime permission helper for Wi-Fi Direct (WifiP2pManager)
 * across Android 8.0 (API 26) through Android 15 (API 35).
 *
 * Permission Matrix:
 * - API 33+ (Android 13, 14, 15):
 *     Requires [Manifest.permission.NEARBY_WIFI_DEVICES].
 *     Because `android:usesPermissionFlags="neverForLocation"` is declared in AndroidManifest.xml,
 *     the OS does not require FINE_LOCATION permission, and users do not need to enable
 *     system-wide Location Services (GPS) to discover Wi-Fi Direct peers.
 * - API 26-32 (Android 8.0 - 12L):
 *     Requires [Manifest.permission.ACCESS_FINE_LOCATION] at runtime.
 *     On API 29-32, Location Services (GPS toggle) must also be enabled by the user for
 *     WifiP2pManager peer discovery to return results.
 */
object WifiDirectPermissionHelper {

    private const val TAG = "WifiDirectPermHelper"

    /**
     * Data class reflecting current readiness for Wi-Fi Direct operations.
     */
    data class PermissionStatus(
        val hasPermissions: Boolean,
        val missingPermissions: List<String>,
        val isWifiHardwareEnabled: Boolean,
        val isLocationServiceEnabled: Boolean,
        val isLocationServiceRequired: Boolean,
        val isReady: Boolean,
        val diagnosticMessage: String
    )

    /**
     * Returns the exact runtime permissions required for the current device's Android OS version.
     */
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    /**
     * Checks if all required runtime permissions for Wi-Fi Direct have been granted.
     */
    fun hasRequiredPermissions(context: Context): Boolean {
        val required = getRequiredPermissions()
        val allGranted = required.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        Logger.d(TAG, "Runtime permissions check (SDK ${Build.VERSION.SDK_INT}): allGranted=$allGranted, required=${required.joinToString()}")
        return allGranted
    }

    /**
     * Returns the list of any missing runtime permissions.
     */
    fun getMissingPermissions(context: Context): List<String> {
        return getRequiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks whether Wi-Fi hardware is currently enabled on the device.
     */
    fun isWifiEnabled(context: Context): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wifiManager?.isWifiEnabled ?: false
    }

    /**
     * Checks whether system Location Services (GPS / Network location) is enabled.
     * Only strictly necessary for Wi-Fi P2P scan on Android 10-12 (API 29-32).
     */
    fun isLocationServiceEnabled(context: Context): Boolean {
        val locationManager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }

    /**
     * Evaluates comprehensive readiness for Wi-Fi Direct peer discovery and connection.
     */
    fun checkStatus(context: Context): PermissionStatus {
        val missing = getMissingPermissions(context)
        val hasPerms = missing.isEmpty()
        val wifiEnabled = isWifiEnabled(context)

        // On API 33+ with neverForLocation, Location Services toggle is NOT required.
        // On API 29-32, Location Services toggle IS required by the Android OS framework.
        val locationRequired = Build.VERSION.SDK_INT in Build.VERSION_CODES.Q..Build.VERSION_CODES.S_V2
        val locationEnabled = isLocationServiceEnabled(context)

        val isReady = hasPerms && wifiEnabled && (!locationRequired || locationEnabled)

        val message = when {
            !hasPerms -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    "Nearby Wi-Fi Devices permission required to connect to tactical mesh."
                } else {
                    "Location permission required for peer discovery on this Android version."
                }
            }
            !wifiEnabled -> "Wi-Fi is currently disabled. Please enable Wi-Fi in system settings."
            locationRequired && !locationEnabled -> "Location Services (GPS) must be enabled on Android 10-12 for Wi-Fi Direct discovery."
            else -> "Ready for Wi-Fi Direct peer discovery and secure P2P transmission."
        }

        return PermissionStatus(
            hasPermissions = hasPerms,
            missingPermissions = missing,
            isWifiHardwareEnabled = wifiEnabled,
            isLocationServiceEnabled = locationEnabled,
            isLocationServiceRequired = locationRequired,
            isReady = isReady,
            diagnosticMessage = message
        )
    }
}
