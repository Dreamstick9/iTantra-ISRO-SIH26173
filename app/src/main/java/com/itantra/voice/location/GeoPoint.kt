package com.itantra.voice.location

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A position fix.
 *
 * @param latitude degrees, -90..90
 * @param longitude degrees, -180..180
 * @param accuracyMetres horizontal accuracy radius, or null when the provider gave none
 * @param timestampMs when the fix was taken, device clock
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMetres: Float? = null,
    val timestampMs: Long = 0L
) {

    /**
     * Human-readable coordinates, e.g. `19.07598°N, 72.87766°E`.
     *
     * Five decimal places is ~1.1 m at the equator — far finer than any consumer GNSS fix,
     * and the same precision the wire format carries.
     */
    fun format(): String {
        val ns = if (latitude >= 0) "N" else "S"
        val ew = if (longitude >= 0) "E" else "W"
        return "%.5f°%s, %.5f°%s".format(abs(latitude), ns, abs(longitude), ew)
    }

    /** Great-circle distance to [other] in metres. */
    fun distanceMetresTo(other: GeoPoint): Double {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Initial great-circle bearing to [other], degrees clockwise from true north. */
    fun bearingDegreesTo(other: GeoPoint): Double {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLon = Math.toRadians(other.longitude - longitude)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    companion object {
        private const val EARTH_RADIUS_M = 6_371_008.8

        /**
         * Fixed-point scale used on the wire.
         *
         * SIH26173 is about links measured in tens of bytes, so coordinates are sent as
         * scaled integers rather than doubles: `19.0759837` serialises as `1907598`,
         * which is 7 JSON characters instead of 10 and drops the decimal point. 1e5 gives
         * ~1.1 m resolution, well inside GNSS error, so nothing useful is lost.
         */
        const val WIRE_SCALE = 100_000.0

        /** Encodes a coordinate to its fixed-point wire representation. */
        fun encode(degrees: Double): Int = (degrees * WIRE_SCALE).roundToLong().toInt()

        /** Decodes a fixed-point wire value back to degrees. */
        fun decode(scaled: Int): Double = scaled / WIRE_SCALE

        /**
         * Rebuilds a point from wire values, or null when either is absent.
         *
         * Out-of-range values are rejected rather than clamped: a corrupt frame should not
         * be rendered as a plausible position on a rescue screen.
         */
        fun fromWire(
            scaledLat: Int?,
            scaledLon: Int?,
            accuracyMetres: Float? = null,
            timestampMs: Long = 0L
        ): GeoPoint? {
            if (scaledLat == null || scaledLon == null) return null
            val lat = decode(scaledLat)
            val lon = decode(scaledLon)
            if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) return null
            return GeoPoint(lat, lon, accuracyMetres, timestampMs)
        }

        /** Compass point for a bearing, e.g. 47° -> "NE". */
        fun compassPoint(bearingDegrees: Double): String {
            val points = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
            val index = ((bearingDegrees % 360.0 + 360.0) % 360.0 / 45.0).roundToInt() % 8
            return points[index]
        }

        /** Distance rendered for a rescuer: metres up to 1 km, then kilometres. */
        fun formatDistance(metres: Double): String = when {
            metres < 1_000 -> "${metres.roundToInt()} m"
            metres < 10_000 -> "%.1f km".format(metres / 1000)
            else -> "${(metres / 1000).roundToInt()} km"
        }
    }
}
