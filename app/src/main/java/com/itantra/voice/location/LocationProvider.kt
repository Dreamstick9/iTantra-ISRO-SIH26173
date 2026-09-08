package com.itantra.voice.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

/**
 * Source of the device's own position.
 *
 * Abstracted so the transceiver can be tested on the JVM without Android's
 * LocationManager, and so a different source (an external GNSS puck over serial, say)
 * could be substituted without touching the ViewModel.
 */
interface LocationProvider {

    /** Latest known fix, or null before the first one arrives. */
    val currentFix: StateFlow<GeoPoint?>

    /** True when the app holds a location permission and the GNSS provider is enabled. */
    fun isAvailable(): Boolean

    /** Begins listening for fixes. Safe to call repeatedly. */
    fun start()

    /** Stops listening. Safe to call when not started. */
    fun stop()
}

/** Provider used when location is unavailable or the caller opted out. */
object NoLocationProvider : LocationProvider {
    override val currentFix: StateFlow<GeoPoint?> = MutableStateFlow(null)
    override fun isAvailable(): Boolean = false
    override fun start() = Unit
    override fun stop() = Unit
}

/**
 * Position from the device's GNSS receiver.
 *
 * **This is fully offline.** A GNSS fix is computed on the handset from signals broadcast
 * by the satellites themselves; no network, SIM or data connection is involved. Only
 * `NETWORK_PROVIDER` (cell/Wi-Fi trilateration) and reverse geocoding need connectivity,
 * and neither is used here — `GPS_PROVIDER` is requested explicitly rather than going
 * through the fused provider, which would silently fall back to the network.
 *
 * The trade-off is a slower first fix, especially indoors, so the last known fix is
 * adopted immediately at start and refined as real ones arrive.
 */
class GpsLocationProvider(
    private val context: Context,
    private val minIntervalMs: Long = DEFAULT_INTERVAL_MS,
    private val minDistanceM: Float = DEFAULT_MIN_DISTANCE_M,
    private val looper: Looper? = Looper.getMainLooper(),
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : LocationProvider {

    private val log = Logger.getLogger("GpsLocationProvider")

    private val _currentFix = MutableStateFlow<GeoPoint?>(null)
    override val currentFix: StateFlow<GeoPoint?> = _currentFix.asStateFlow()

    private val listening = AtomicBoolean(false)

    private val manager: LocationManager? by lazy {
        ContextCompat.getSystemService(context, LocationManager::class.java)
    }

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _currentFix.value = location.toGeoPoint()
        }

        // Required on API < 30; removing them makes the listener abstract on older devices.
        @Deprecated("Deprecated in API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override fun isAvailable(): Boolean {
        if (!hasPermission()) return false
        val mgr = manager ?: return false
        return runCatching { mgr.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    override fun start() {
        if (!hasPermission()) {
            log.info("Location permission not granted; position will not be attached")
            return
        }
        if (listening.getAndSet(true)) return

        val mgr = manager ?: run {
            listening.set(false)
            return
        }

        // Seed from the last known fix so an early transmission still carries a position
        // while the receiver is still acquiring satellites.
        runCatching {
            mgr.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                _currentFix.value = it.toGeoPoint()
            }
        }

        runCatching {
            mgr.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                minIntervalMs,
                minDistanceM,
                listener,
                looper
            )
        }.onFailure {
            log.warning("Could not start GNSS updates: ${it.message}")
            listening.set(false)
        }
    }

    override fun stop() {
        if (!listening.getAndSet(false)) return
        runCatching { manager?.removeUpdates(listener) }
    }

    private fun Location.toGeoPoint(): GeoPoint = GeoPoint(
        latitude = latitude,
        longitude = longitude,
        accuracyMetres = if (hasAccuracy()) accuracy else null,
        timestampMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && time > 0) time
        else timeProvider()
    )

    private companion object {
        const val DEFAULT_INTERVAL_MS = 5_000L
        const val DEFAULT_MIN_DISTANCE_M = 5f
    }
}
