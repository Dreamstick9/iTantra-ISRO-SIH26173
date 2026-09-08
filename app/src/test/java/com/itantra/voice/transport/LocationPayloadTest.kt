package com.itantra.voice.transport

import com.itantra.voice.location.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Position on the wire.
 *
 * SIH26173 scores efficiency and targets links budgeted in tens of bytes (a 27-byte NavIC
 * subframe, a 277-byte packet), so the byte cost of attaching a position is asserted, not
 * assumed.
 */
class LocationPayloadTest {

    private val fix = GeoPoint(19.0759837, 72.8776559, accuracyMetres = 12.4f)

    private fun message() = TransportMessage(
        sourceLanguage = "hi-IN",
        targetLanguage = "en-IN",
        text = "Need water"
    )

    @Test
    fun `a position survives serialisation to the far handset`() {
        val sent = TransportMessage.withLocation(message(), fix)
        val received = TransportMessage.fromJson(sent.toJson()).getOrThrow()

        val position = received.senderLocation()
        assertNotNull(position)
        assertEquals(fix.latitude, position!!.latitude, 0.00001)
        assertEquals(fix.longitude, position.longitude, 0.00001)
        assertEquals(12f, position.accuracyMetres!!, 1f)
    }

    @Test
    fun `a message without a fix carries no position`() {
        val sent = TransportMessage.withLocation(message(), null)
        assertFalse(sent.hasLocation)
        assertNull(sent.senderLocation())

        val received = TransportMessage.fromJson(sent.toJson()).getOrThrow()
        assertNull(received.senderLocation())
    }

    @Test
    fun `omitting a fix costs no bytes at all`() {
        val withoutFix = TransportMessage.withLocation(message(), null).toJson()

        // Absent fields must not serialise as explicit nulls; that wasted 27 bytes on a
        // link the brief budgets in tens of bytes.
        assertFalse("null coordinates must not be serialised", withoutFix.contains("null"))
        assertFalse(withoutFix.contains("\"la\""))
        assertFalse(withoutFix.contains("\"lo\""))
    }

    @Test
    fun `attaching a position stays inside the NavIC packet budget`() {
        val withFix = TransportMessage.withLocation(message(), fix).toJson()
        val withoutFix = TransportMessage.withLocation(message(), null).toJson()

        val cost = withFix.toByteArray(Charsets.UTF_8).size -
            withoutFix.toByteArray(Charsets.UTF_8).size

        // Fixed-point integers with two-letter keys; doubles under full-length keys would
        // roughly double this.
        assertTrue("position cost $cost bytes, expected under 40", cost < 40)

        // The whole frame must still fit a 277-byte NavIC packet.
        assertTrue(
            "frame was ${withFix.toByteArray().size} bytes",
            withFix.toByteArray(Charsets.UTF_8).size < 277
        )
    }

    @Test
    fun `an emergency alert still fits the packet budget with a position attached`() {
        val alert = TransportMessage.withLocation(
            TransportMessage(
                type = TransportMessageType.ALERT,
                sourceLanguage = "hi-IN",
                targetLanguage = "en-IN",
                text = "Trapped under rubble, three people, need medical help",
                priority = 1
            ),
            fix
        )
        assertTrue(alert.toJson().toByteArray(Charsets.UTF_8).size < 277)
        assertTrue(alert.hasLocation)
    }

    @Test
    fun `a frame from an older build without coordinates still parses`() {
        // Forward compatibility with handsets running the pre-GPS release.
        val legacy = """{"messageId":"abc","timestamp":1,"type":"TRANSLATION",""" +
            """"sourceLanguage":"hi-IN","targetLanguage":"en-IN","text":"hello","priority":0}"""

        val parsed = TransportMessage.fromJson(legacy).getOrThrow()
        assertEquals("hello", parsed.text)
        assertNull(parsed.senderLocation())
    }

    @Test
    fun `a corrupt coordinate is dropped rather than shown as a position`() {
        val corrupt = TransportMessage(
            sourceLanguage = "hi-IN",
            targetLanguage = "en-IN",
            text = "x",
            latitudeE5 = GeoPoint.encode(120.0),  // impossible latitude
            longitudeE5 = GeoPoint.encode(72.0)
        )
        assertNull(corrupt.senderLocation())
        assertFalse(corrupt.hasLocation)
    }
}
