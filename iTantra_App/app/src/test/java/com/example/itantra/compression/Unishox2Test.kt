package com.example.itantra.compression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Unishox2Test {

    @Test
    fun testHelloCompression() {
        val original = "Hello"
        val rawBytes = original.toByteArray(Charsets.UTF_8)
        val compressed = Unishox2.compress(original)

        println("Hello: original=${rawBytes.size} bytes, compressed=${compressed.size} bytes")
        assertTrue("Hello should compress to <= 4 bytes", compressed.size <= 4)

        val decompressed = Unishox2.decompressToString(compressed)
        assertEquals("Decompressed string must match original exactly", original, decompressed)
    }

    @Test
    fun testEnglishSentenceCompression() {
        val original = "Cyclone alert evacuate to high ground immediately"
        val rawBytes = original.toByteArray(Charsets.UTF_8)
        val compressed = Unishox2.compress(original)

        val ratio = rawBytes.size.toDouble() / compressed.size.toDouble()
        val savedPct = ((rawBytes.size - compressed.size).toDouble() / rawBytes.size.toDouble()) * 100.0

        println("English: original=${rawBytes.size}B, compressed=${compressed.size}B, ratio=${"%.2f".format(ratio)}x, saved=${"%.1f".format(savedPct)}%")
        assertTrue("English sentence should achieve > 30% compression", savedPct > 30.0)

        val decompressed = Unishox2.decompressToString(compressed)
        assertEquals(original, decompressed)
    }

    @Test
    fun testTenIndianLanguagesCompression() {
        val testCorpus = mapOf(
            "Hindi" to "चक्रवात चेतावनी तुरंत सुरक्षित स्थान पर जाएं",
            "Bengali" to "ঘূর্ণিঝড় সতর্কতা দ্রুত নিরাপদ স্থানে যান",
            "Tamil" to "புயல் எச்சரிக்கை உடனே பாதுகாப்பான இடத்திற்கு செல்லவும்",
            "Telugu" to "తుఫాను హెచ్చరిక వెంటనే సురక్షిత ప్రాంతానికి వెళ్లండి",
            "Kannada" to "ಚಂಡಮಾರುತ ಎಚ್ಚರಿಕೆ ತಕ್ಷಣ ಸುರಕ್ಷಿತ ಸ್ಥಳಕ್ಕೆ ತೆರಳಿ",
            "Malayalam" to "ചുഴലിക്കാറ്റ് മുന്നറിയിപ്പ് ഉടൻ സുരക്ഷിത സ്ഥാനത്തേക്ക് പോകുക",
            "Marathi" to "चक्रीवादळ इशारा त्वरित सुरक्षित स्थळी जा",
            "Gujarati" to "વાવાઝોડાની ચેતવણી તાત્કાલિક સલામત સ્થળે જાઓ",
            "Odia" to "ବାତ୍ୟା ସତର୍କତା ତୁରନ୍ତ ନିରାପଦ ସ୍ଥାନକୁ ଯାଆନ୍ତୁ",
            "English" to "Disaster response team deployed to coastal sector"
        )

        for ((lang, sentence) in testCorpus) {
            val rawBytes = sentence.toByteArray(Charsets.UTF_8)
            val compressed = Unishox2.compress(sentence)

            val ratio = rawBytes.size.toDouble() / compressed.size.toDouble()
            val savedPct = ((rawBytes.size - compressed.size).toDouble() / rawBytes.size.toDouble()) * 100.0

            println("[$lang] Raw: ${rawBytes.size}B -> Compressed: ${compressed.size}B (Ratio: ${"%.2f".format(ratio)}x, Saved: ${"%.1f".format(savedPct)}%)")

            val decompressed = Unishox2.decompressToString(compressed)
            assertEquals("Lossless roundtrip failed for language $lang", sentence, decompressed)

            if (lang != "English") {
                // Indic scripts benefit heavily from delta unicode compression in Unishox2
                assertTrue("Indic language $lang should achieve significant compression", compressed.size < rawBytes.size)
            }
        }
    }

    @Test
    fun testRepeatedCharacters() {
        val original = "AAAAAAAAAA1111111111"
        val compressed = Unishox2.compress(original)
        val decompressed = Unishox2.decompressToString(compressed)
        assertEquals(original, decompressed)
    }

    @Test
    fun testEmptyAndShortStrings() {
        assertEquals("", Unishox2.decompressToString(Unishox2.compress("")))
        assertEquals("A", Unishox2.decompressToString(Unishox2.compress("A")))
        assertEquals("1", Unishox2.decompressToString(Unishox2.compress("1")))
        assertEquals("OK", Unishox2.decompressToString(Unishox2.compress("OK")))
        assertEquals("SOS", Unishox2.decompressToString(Unishox2.compress("SOS")))
    }

    @Test
    fun testUnishox2CompressionEngineRoundtrip() {
        val engine = Unishox2CompressionEngine()
        val text = "Cyclone alert: Evacuate immediately! चक्रवात चेतावनी"
        val rawBytes = text.toByteArray(Charsets.UTF_8)

        val wireBytes = engine.compressText(text)
        assertTrue("Wire bytes should have magic prefix", wireBytes.isNotEmpty())
        assertEquals("Wire byte 0 should be MAGIC_UNISHOX2", Unishox2CompressionEngine.MAGIC_UNISHOX2, wireBytes[0])

        val restoredText = engine.decompressText(wireBytes)
        assertEquals("Restored text must match original", text, restoredText)
    }

    @Test
    fun testEngineBackwardCompatibilityWithRawUtf8() {
        val engine = Unishox2CompressionEngine()
        val rawText = "Plain uncompressed text"
        val rawBytes = rawText.toByteArray(Charsets.UTF_8)

        // Decompressing legacy raw bytes without magic header should not crash and return original
        val decompressed = engine.decompressText(rawBytes)
        assertEquals(rawText, decompressed)
    }
}
