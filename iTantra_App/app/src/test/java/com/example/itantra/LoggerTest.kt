package com.example.itantra

import com.example.itantra.util.LogLevel
import com.example.itantra.util.LogBuffer
import com.example.itantra.util.Logger
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LoggerTest {

    @Before
    fun setUp() {
        Logger.clearBuffer()
        Logger.minLevel = LogLevel.DEBUG
        Logger.isEnabled = true
        Logger.tagPrefix = "iTantra:"
    }

    @After
    fun tearDown() {
        Logger.clearBuffer()
    }

    @Test
    fun testLogLevelFiltering() {
        Logger.minLevel = LogLevel.WARN

        Logger.d("Tag1", "Debug message should be ignored")
        Logger.i("Tag1", "Info message should be ignored")
        Logger.w("Tag1", "Warning message should be captured")
        Logger.e("Tag1", "Error message should be captured")

        val entries = Logger.buffer.getEntries()
        assertEquals(2, entries.size)
        assertEquals(LogLevel.WARN, entries[0].level)
        assertEquals(LogLevel.ERROR, entries[1].level)
    }

    @Test
    fun testTagFormatting() {
        val longTag = "VeryLongSubsystemTagExceedingLength"
        val formatted = Logger.formatTag(longTag)
        assertTrue(formatted.length <= 23)
        assertTrue(formatted.startsWith("iTantra:"))
    }

    @Test
    fun testLogBufferCapacityEviction() {
        val smallBuffer = LogBuffer(capacity = 5)
        for (i in 1..8) {
            smallBuffer.append(LogLevel.INFO, "Tag", "Msg $i")
        }

        assertEquals(5, smallBuffer.size)
        val entries = smallBuffer.getEntries()
        assertEquals("Msg 4", entries[0].message)
        assertEquals("Msg 8", entries[4].message)
    }

    @Test
    fun testDiagnosticExport() {
        Logger.i("Diag", "Transceiver online")
        val dump = Logger.exportDiagnostics()
        assertTrue(dump.contains("iTantra On-Device Diagnostics Log Dump"))
        assertTrue(dump.contains("Transceiver online"))
    }
}
