package com.itantra.voice.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coordinate maths and the fixed-point wire format.
 *
 * Distances are checked against known real-world separations because an error in the
 * Haversine implementation produces plausible-looking numbers rather than an exception —
 * on a rescue screen that is worse than a crash.
 */
class GeoPointTest {

    private val mumbai = GeoPoint(19.0759837, 72.8776559)
    private val delhi = GeoPoint(28.7040592, 77.1024902)
    private val chennai = GeoPoint(13.0826802, 80.2707184)

    @Test
    fun `distance matches the known Mumbai to Delhi separation`() {
        // Great-circle distance is ~1153 km; allow 1% for the spherical-Earth model.
        val km = mumbai.distanceMetresTo(delhi) / 1000
        assertTrue("expected ~1153 km, got $km", km in 1140.0..1165.0)
    }

    @Test
    fun `distance is symmetric`() {
        assertEquals(
            mumbai.distanceMetresTo(delhi),
            delhi.distanceMetresTo(mumbai),
            0.5
        )
    }

    @Test
    fun `distance to self is zero`() {
        assertEquals(0.0, mumbai.distanceMetresTo(mumbai), 0.001)
    }

    @Test
    fun `short distances are accurate to the metre`() {
        // 0.001 degrees of latitude is ~111.2 m anywhere on Earth.
        val near = GeoPoint(mumbai.latitude + 0.001, mumbai.longitude)
        assertEquals(111.2, mumbai.distanceMetresTo(near), 1.0)
    }

    @Test
    fun `bearing from Mumbai to Delhi is just east of due north`() {
        // The true initial bearing is 21.0 degrees. On an 8-point compass the NE sector
        // starts at 22.5, so this correctly reads "N" — a 16-point rose would say NNE.
        val bearing = mumbai.bearingDegreesTo(delhi)
        assertEquals(21.0, bearing, 0.5)
        assertEquals("N", GeoPoint.compassPoint(bearing))
    }

    @Test
    fun `a genuinely north-east target reads NE`() {
        val northEast = GeoPoint(mumbai.latitude + 1.0, mumbai.longitude + 1.0)
        val bearing = mumbai.bearingDegreesTo(northEast)
        assertTrue("expected NE sector, got $bearing", bearing in 22.5..67.5)
        assertEquals("NE", GeoPoint.compassPoint(bearing))
    }

    @Test
    fun `bearing from Mumbai to Chennai is south-east`() {
        val bearing = mumbai.bearingDegreesTo(chennai)
        assertTrue("expected SE quadrant, got $bearing", bearing in 90.0..180.0)
    }

    @Test
    fun `compass points map to the right sectors`() {
        assertEquals("N", GeoPoint.compassPoint(0.0))
        assertEquals("NE", GeoPoint.compassPoint(45.0))
        assertEquals("E", GeoPoint.compassPoint(90.0))
        assertEquals("S", GeoPoint.compassPoint(180.0))
        assertEquals("W", GeoPoint.compassPoint(270.0))
        assertEquals("N", GeoPoint.compassPoint(359.0))
        // Wrapping past a full turn must not throw or index out of bounds.
        assertEquals("N", GeoPoint.compassPoint(720.0))
        assertEquals("N", GeoPoint.compassPoint(-1.0))
    }

    @Test
    fun `wire encoding round-trips within its stated precision`() {
        val encodedLat = GeoPoint.encode(mumbai.latitude)
        val encodedLon = GeoPoint.encode(mumbai.longitude)

        val restored = GeoPoint.fromWire(encodedLat, encodedLon)
        assertNotNull(restored)
        // 1e5 scaling resolves ~1.1 m, so degrees agree to 5 decimal places.
        assertEquals(mumbai.latitude, restored!!.latitude, 0.00001)
        assertEquals(mumbai.longitude, restored.longitude, 0.00001)
    }

    @Test
    fun `wire round-trip error stays inside GNSS accuracy`() {
        val restored = GeoPoint.fromWire(
            GeoPoint.encode(mumbai.latitude),
            GeoPoint.encode(mumbai.longitude)
        )!!
        // Rounding must cost far less than a consumer GNSS fix's own error.
        assertTrue(mumbai.distanceMetresTo(restored) < 2.0)
    }

    @Test
    fun `southern and western hemispheres survive the round trip`() {
        val sydney = GeoPoint(-33.8688, 151.2093)
        val lima = GeoPoint(-12.0464, -77.0428)

        for (point in listOf(sydney, lima)) {
            val restored = GeoPoint.fromWire(
                GeoPoint.encode(point.latitude),
                GeoPoint.encode(point.longitude)
            )!!
            assertEquals(point.latitude, restored.latitude, 0.00001)
            assertEquals(point.longitude, restored.longitude, 0.00001)
        }
    }

    @Test
    fun `a missing coordinate yields no point`() {
        assertNull(GeoPoint.fromWire(null, GeoPoint.encode(72.0)))
        assertNull(GeoPoint.fromWire(GeoPoint.encode(19.0), null))
        assertNull(GeoPoint.fromWire(null, null))
    }

    @Test
    fun `out of range wire values are rejected rather than clamped`() {
        // A corrupt frame must not render as a plausible position on a rescue screen.
        assertNull(GeoPoint.fromWire(GeoPoint.encode(91.0), GeoPoint.encode(0.0)))
        assertNull(GeoPoint.fromWire(GeoPoint.encode(-91.0), GeoPoint.encode(0.0)))
        assertNull(GeoPoint.fromWire(GeoPoint.encode(0.0), GeoPoint.encode(181.0)))
        assertNull(GeoPoint.fromWire(GeoPoint.encode(0.0), GeoPoint.encode(-181.0)))
    }

    @Test
    fun `coordinates format with hemisphere letters`() {
        assertEquals("19.07598°N, 72.87766°E", mumbai.format())
        assertEquals("33.86880°S, 151.20930°E", GeoPoint(-33.8688, 151.2093).format())
        assertEquals("12.04640°S, 77.04280°W", GeoPoint(-12.0464, -77.0428).format())
    }

    @Test
    fun `distance is rendered at a resolution a rescuer can use`() {
        assertEquals("0 m", GeoPoint.formatDistance(0.0))
        assertEquals("450 m", GeoPoint.formatDistance(450.0))
        assertEquals("999 m", GeoPoint.formatDistance(999.4))
        assertEquals("1.5 km", GeoPoint.formatDistance(1500.0))
        assertEquals("42 km", GeoPoint.formatDistance(42_000.0))
    }
}
