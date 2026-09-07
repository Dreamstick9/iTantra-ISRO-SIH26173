package com.itantra.voice.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import com.itantra.voice.fixtures.SarvamMockFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Empirical Adversarial Stress Test Suite for the Audio Engine:
 * - WavEncoder: Math fidelity, 0-byte, 1-byte, odd byte lengths, 10 MB buffers, custom sample rates, bit depths.
 * - AudioPlayer: Malformed Base64, corrupted RIFF headers, zero-length audio, rapid repeated playbacks, concurrency.
 * - AudioRecorder: Buffer math (16000 * 1 * 2 = 32000 bytes/sec), 30s threshold, amplitude math, lifecycle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioEngineAdversarialStressTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var cacheDir: File

    private class StressMockNativeMediaPlayer : NativeMediaPlayer {
        override var onCompletionListener: (() -> Unit)? = null
        override var onErrorListener: ((what: Int, extra: Int) -> Boolean)? = null

        var contentType: Int = -1
        var usage: Int = -1
        var dataSourcePath: String? = null
        val prepareCallCount = AtomicInteger(0)
        val startCallCount = AtomicInteger(0)
        val stopCallCount = AtomicInteger(0)
        val resetCallCount = AtomicInteger(0)
        val releaseCallCount = AtomicInteger(0)
        private var playing = false

        override fun setAudioAttributes(contentType: Int, usage: Int) {
            this.contentType = contentType
            this.usage = usage
        }

        override fun setDataSource(path: String) {
            this.dataSourcePath = path
        }

        override fun prepare() {
            prepareCallCount.incrementAndGet()
        }

        override fun start() {
            startCallCount.incrementAndGet()
            playing = true
        }

        override fun stop() {
            stopCallCount.incrementAndGet()
            playing = false
        }

        override fun reset() {
            resetCallCount.incrementAndGet()
            playing = false
        }

        override fun release() {
            releaseCallCount.incrementAndGet()
            playing = false
        }

        override val isPlaying: Boolean
            get() = playing

        fun triggerCompletion() {
            playing = false
            onCompletionListener?.invoke()
        }

        fun triggerError(what: Int = 1, extra: Int = -1) {
            playing = false
            onErrorListener?.invoke(what, extra)
        }
    }

    private class MockNativeAudioRecord(
        override var state: Int = AudioRecord.STATE_INITIALIZED,
        override var recordingState: Int = AudioRecord.RECORDSTATE_STOPPED,
        val chunksToSupply: List<ByteArray> = emptyList(),
        val readErrorCode: Int? = null
    ) : NativeAudioRecord {

        var startRecordingCalled = false
        var stopCalled = false
        var releaseCalled = false
        private var chunkIndex = 0
        private var chunkOffset = 0

        override fun startRecording() {
            startRecordingCalled = true
            recordingState = AudioRecord.RECORDSTATE_RECORDING
        }

        override fun stop() {
            stopCalled = true
            recordingState = AudioRecord.RECORDSTATE_STOPPED
        }

        override fun release() {
            releaseCalled = true
        }

        override fun read(audioData: ByteArray, offsetInBytes: Int, sizeInBytes: Int): Int {
            if (readErrorCode != null) return readErrorCode
            if (chunkIndex >= chunksToSupply.size) return 0

            val currentChunk = chunksToSupply[chunkIndex]
            val remaining = currentChunk.size - chunkOffset
            val bytesToCopy = minOf(remaining, sizeInBytes)
            System.arraycopy(currentChunk, chunkOffset, audioData, offsetInBytes, bytesToCopy)
            chunkOffset += bytesToCopy
            if (chunkOffset >= currentChunk.size) {
                chunkIndex++
                chunkOffset = 0
            }
            return bytesToCopy
        }
    }

    @Before
    fun setUp() {
        cacheDir = tempFolder.newFolder("audio_stress_cache")
    }

    // =========================================================================
    // 1. WavEncoder Stress Tests
    // =========================================================================

    @Test
    fun testWavEncoderZeroBytes() {
        val emptyPcm = ByteArray(0)
        val wavBytes = WavEncoder.encode(emptyPcm)

        assertEquals("WAV size for 0 bytes must be exactly 44 bytes header", 44, wavBytes.size)

        val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("ChunkSize must be 36 for 0 PCM bytes", 36, buffer.getInt(4))
        assertEquals("Subchunk1Size must be 16", 16, buffer.getInt(16))
        assertEquals("Subchunk2Size must be 0 for 0 PCM bytes", 0, buffer.getInt(40))
        assertEquals("AudioFormat must be 1 (PCM)", 1, buffer.getShort(20).toInt())
        assertEquals("NumChannels must be 1", 1, buffer.getShort(22).toInt())
        assertEquals("SampleRate must be 16000", 16000, buffer.getInt(24))
        assertEquals("ByteRate must be 32000", 32000, buffer.getInt(28))
        assertEquals("BlockAlign must be 2", 2, buffer.getShort(32).toInt())
        assertEquals("BitsPerSample must be 16", 16, buffer.getShort(34).toInt())
    }

    @Test
    fun testWavEncoderSingleByte() {
        val singleBytePcm = byteArrayOf(0x42)
        val wavBytes = WavEncoder.encode(singleBytePcm)

        assertEquals("WAV size for 1 byte PCM must be 45 bytes", 45, wavBytes.size)

        val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("ChunkSize must be 37 for 1 byte PCM", 37, buffer.getInt(4))
        assertEquals("Subchunk2Size must be 1", 1, buffer.getInt(40))
        assertEquals("Payload byte at offset 44 must match", 0x42.toByte(), wavBytes[44])
    }

    @Test
    fun testWavEncoderOddByteLengths() {
        val oddLengths = listOf(3, 5, 7, 101, 555, 65535, 65537)
        for (len in oddLengths) {
            val pcm = ByteArray(len) { (it % 127).toByte() }
            val wavBytes = WavEncoder.encode(pcm)

            assertEquals("WAV size must be 44 + $len", len + 44, wavBytes.size)

            val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals("ChunkSize must be 36 + $len", len + 36, buffer.getInt(4))
            assertEquals("Subchunk2Size must be $len", len, buffer.getInt(40))

            // Verify payload start and end
            assertEquals("First byte must match", pcm[0], wavBytes[44])
            assertEquals("Last byte must match", pcm[len - 1], wavBytes[44 + len - 1])
        }
    }

    @Test
    fun testWavEncoderLargeBuffer10MB() {
        val tenMb = 10 * 1024 * 1024 // 10,485,760 bytes (~327.68 seconds of audio)
        val pcm = ByteArray(tenMb)
        pcm[0] = 0x11
        pcm[tenMb / 2] = 0x22
        pcm[tenMb - 1] = 0x33

        val wavBytes = WavEncoder.encode(pcm)

        assertEquals("WAV size must be 10MB + 44 bytes", tenMb + 44, wavBytes.size)

        val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
        val expectedChunkSize = tenMb + 36
        assertEquals("ChunkSize must equal 10MB + 36", expectedChunkSize, buffer.getInt(4))
        assertEquals("Subchunk2Size must equal 10MB", tenMb, buffer.getInt(40))

        // Spot-check payload integrity across 10MB
        assertEquals(0x11.toByte(), wavBytes[44])
        assertEquals(0x22.toByte(), wavBytes[44 + tenMb / 2])
        assertEquals(0x33.toByte(), wavBytes[44 + tenMb - 1])
    }

    @Test
    fun testWavEncoderCustomSampleRatesAndChannelsAndBitDepths() {
        val testConfigs = listOf(
            Triple(8000, 1, 16),      // 8 kHz Mono 16-bit (Telephony)
            Triple(11025, 1, 16),     // 11.025 kHz Mono 16-bit
            Triple(16000, 1, 16),     // 16 kHz Mono 16-bit (Standard)
            Triple(22050, 1, 16),     // 22.05 kHz Mono 16-bit
            Triple(32000, 1, 16),     // 32 kHz Mono 16-bit
            Triple(44100, 2, 16),     // 44.1 kHz Stereo 16-bit (CD Quality)
            Triple(48000, 2, 24),     // 48 kHz Stereo 24-bit (Studio Quality)
            Triple(96000, 6, 32)      // 96 kHz 5.1 Surround 32-bit (Hi-Res)
        )

        for ((rate, channels, bits) in testConfigs) {
            val pcm = ByteArray(100)
            val wav = WavEncoder.encode(pcm, sampleRate = rate, channels = channels, bitsPerSample = bits)
            val buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)

            val bytesPerSample = bits / 8
            val expectedByteRate = rate * channels * bytesPerSample
            val expectedBlockAlign = channels * bytesPerSample

            assertEquals("Sample rate $rate mismatch", rate, buffer.getInt(24))
            assertEquals("Channels $channels mismatch", channels, buffer.getShort(22).toInt())
            assertEquals("Bits per sample $bits mismatch", bits, buffer.getShort(34).toInt())
            assertEquals("ByteRate mismatch for ($rate, $channels, $bits)", expectedByteRate, buffer.getInt(28))
            assertEquals("BlockAlign mismatch for ($rate, $channels, $bits)", expectedBlockAlign, buffer.getShort(32).toInt())
        }
    }

    @Test
    fun testWavEncoderInvalidArgumentsValidation() {
        val pcm = ByteArray(10)

        // Invalid sample rates
        val badRates = listOf(0, -1, -16000, Int.MIN_VALUE)
        for (badRate in badRates) {
            try {
                WavEncoder.encode(pcm, sampleRate = badRate)
                fail("Expected IllegalArgumentException for sampleRate: $badRate")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("sampleRate"))
            }
        }

        // Invalid channels
        val badChannels = listOf(0, -1, -2)
        for (badChannel in badChannels) {
            try {
                WavEncoder.encode(pcm, channels = badChannel)
                fail("Expected IllegalArgumentException for channels: $badChannel")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("channels"))
            }
        }

        // Invalid bit depths (must be positive multiple of 8)
        val badBits = listOf(0, -8, 7, 12, 17, 20, 25)
        for (badBit in badBits) {
            try {
                WavEncoder.encode(pcm, bitsPerSample = badBit)
                fail("Expected IllegalArgumentException for bitsPerSample: $badBit")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("bitsPerSample"))
            }
        }
    }

    // =========================================================================
    // 2. AudioPlayer Stress Tests
    // =========================================================================

    @Test
    fun testAudioPlayerMalformedBase64Strings() {
        val mockPlayer = StressMockNativeMediaPlayer()
        val player = AudioPlayer(cacheDir, playerFactory = { mockPlayer })

        val malformedInputs = listOf(
            "not-base64-at-all!@#$%",
            "====", // invalid padding
            "UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQAAAA", // truncated padding
            "UklGR!!!???@@@",
            "\t\t\t\n\n\r\r", // whitespace only
            "123" // invalid length
        )

        for (input in malformedInputs) {
            val errorReceived = AtomicBoolean(false)
            player.playBase64Wav(
                input,
                onError = { error ->
                    errorReceived.set(true)
                    assertTrue("Error must be IllegalArgumentException", error is IllegalArgumentException)
                }
            )

            assertTrue("onError must be called for malformed input: '$input'", errorReceived.get())
            assertFalse("Player must not start on malformed input", player.isPlaying)
            assertEquals("No prepare() call on malformed input", 0, mockPlayer.prepareCallCount.get())
        }
    }

    @Test
    fun testAudioPlayerCorruptedRiffHeaders() {
        val mockPlayer = StressMockNativeMediaPlayer()
        val player = AudioPlayer(cacheDir, playerFactory = { mockPlayer })

        val validWav = SarvamMockFixtures.createCanonicalWav(ByteArray(100))

        // Corrupt 'RIFF' descriptor
        val badRiff = validWav.copyOf()
        badRiff[0] = 'R'.code.toByte()
        badRiff[1] = 'I'.code.toByte()
        badRiff[2] = 'F'.code.toByte()
        badRiff[3] = 'X'.code.toByte() // 'RIFX' instead of 'RIFF'

        val errorRiff = AtomicBoolean(false)
        player.playWavBytes(badRiff, onError = { errorRiff.set(true) })
        assertTrue("Corrupted 'RIFF' must trigger error callback", errorRiff.get())

        // Corrupt 'WAVE' descriptor
        val badWave = validWav.copyOf()
        badWave[8] = 'N'.code.toByte()
        badWave[9] = 'O'.code.toByte()
        badWave[10] = 'P'.code.toByte()
        badWave[11] = 'E'.code.toByte() // 'NOPE' instead of 'WAVE'

        val errorWave = AtomicBoolean(false)
        player.playWavBytes(badWave, onError = { errorWave.set(true) })
        assertTrue("Corrupted 'WAVE' must trigger error callback", errorWave.get())

        // Truncated headers (< 44 bytes)
        val shortLengths = listOf(0, 1, 10, 20, 43)
        for (len in shortLengths) {
            val shortBytes = ByteArray(len) { 0x52 }
            val errorShort = AtomicBoolean(false)
            player.playWavBytes(shortBytes, onError = { errorShort.set(true) })
            assertTrue("Header shorter than 44 bytes ($len) must trigger error", errorShort.get())
        }
    }

    @Test
    fun testAudioPlayerZeroLengthAudioPayload() {
        val mockPlayer = StressMockNativeMediaPlayer()
        val player = AudioPlayer(cacheDir, playerFactory = { mockPlayer })

        val errorString = AtomicBoolean(false)
        player.playBase64Wav("", onError = { errorString.set(true) })
        assertTrue("Empty base64 string must trigger error", errorString.get())

        val errorWhitespace = AtomicBoolean(false)
        player.playBase64Wav("     ", onError = { errorWhitespace.set(true) })
        assertTrue("Whitespace base64 string must trigger error", errorWhitespace.get())

        val errorZeroBytes = AtomicBoolean(false)
        player.playWavBytes(ByteArray(0), onError = { errorZeroBytes.set(true) })
        assertTrue("Zero byte array must trigger error", errorZeroBytes.get())
    }

    @Test
    fun testAudioPlayerRapidRepeatedPlaybacks() {
        val playersCreated = mutableListOf<StressMockNativeMediaPlayer>()
        val player = AudioPlayer(cacheDir, playerFactory = {
            val p = StressMockNativeMediaPlayer()
            playersCreated.add(p)
            p
        })

        // Rapidly dispatch 50 consecutive playbacks
        for (i in 0 until 50) {
            player.playBase64Wav(SarvamMockFixtures.MOCK_BASE64_WAV)
        }

        assertTrue(player.isPlaying)
        assertEquals(50, playersCreated.size)

        // Only the last player's temp file should remain; earlier files should be deleted
        val activePath = playersCreated.last().dataSourcePath
        assertNotNull(activePath)
        assertTrue("Active player's temp file must exist", File(activePath!!).exists())

        // Verify previous 49 temp files were deleted
        for (i in 0 until 49) {
            val oldPath = playersCreated[i].dataSourcePath
            if (oldPath != null && oldPath != activePath) {
                assertFalse("Old temp file $i must be cleaned up", File(oldPath).exists())
            }
        }

        // Clean shutdown
        player.stopAndRelease()
        assertFalse(player.isPlaying)
        assertFalse("Active temp file must be cleaned up after stopAndRelease", File(activePath).exists())
    }

    @Test
    fun testAudioPlayerConcurrentPlayAndStopStress() {
        val player = AudioPlayer(cacheDir, playerFactory = { StressMockNativeMediaPlayer() })
        val threadCount = 20
        val iterationsPerThread = 25
        val latch = CountDownLatch(threadCount)
        val failureOccurred = AtomicBoolean(false)

        for (t in 0 until threadCount) {
            Thread {
                try {
                    for (i in 0 until iterationsPerThread) {
                        when (i % 3) {
                            0 -> player.playBase64Wav(SarvamMockFixtures.MOCK_BASE64_WAV)
                            1 -> player.playWavBytes(SarvamMockFixtures.createCanonicalWav(ByteArray(200)))
                            2 -> player.stopAndRelease()
                        }
                    }
                } catch (e: Throwable) {
                    failureOccurred.set(true)
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        assertTrue("Concurrent operations must complete within 5 seconds", latch.await(5, TimeUnit.SECONDS))
        assertFalse("No exceptions must occur during high concurrency", failureOccurred.get())

        player.stopAndRelease()
        assertFalse(player.isPlaying)
    }

    @Test
    fun testAudioPlayerTempFileCleanupOnCompletionAndError() {
        val mockPlayer = StressMockNativeMediaPlayer()
        val player = AudioPlayer(cacheDir, playerFactory = { mockPlayer })

        // Test normal completion cleanup
        player.playBase64Wav(SarvamMockFixtures.MOCK_BASE64_WAV)
        val tempPath1 = mockPlayer.dataSourcePath!!
        assertTrue(File(tempPath1).exists())
        mockPlayer.triggerCompletion()
        assertFalse("Temp file must be deleted on completion", File(tempPath1).exists())

        // Test error callback cleanup
        player.playBase64Wav(SarvamMockFixtures.MOCK_BASE64_WAV)
        val tempPath2 = mockPlayer.dataSourcePath!!
        assertTrue(File(tempPath2).exists())
        mockPlayer.triggerError(what = 1, extra = -1)
        assertFalse("Temp file must be deleted on error", File(tempPath2).exists())
    }

    // =========================================================================
    // 3. AudioRecorder Math & Buffer Limits Stress Tests
    // =========================================================================

    @Test
    fun testAudioRecorderBufferMathConstants() {
        // Verify contract: 16 kHz, Mono (1), 16-bit (2 bytes per sample) -> 32,000 bytes/sec
        assertEquals(16000, AudioRecorder.SAMPLE_RATE)
        assertEquals(1, AudioRecorder.CHANNELS)
        assertEquals(2, AudioRecorder.BYTES_PER_SAMPLE)
        assertEquals(AudioFormat.CHANNEL_IN_MONO, AudioRecorder.CHANNEL_CONFIG)
        assertEquals(AudioFormat.ENCODING_PCM_16BIT, AudioRecorder.AUDIO_FORMAT)

        val calculatedByteRate = AudioRecorder.SAMPLE_RATE * AudioRecorder.CHANNELS * AudioRecorder.BYTES_PER_SAMPLE
        assertEquals("ByteRate must equal 32,000 bytes/sec", 32000, calculatedByteRate)
        assertEquals(calculatedByteRate, AudioRecorder.BYTE_RATE)

        // Verify 30 second limit math
        assertEquals(30, AudioRecorder.MAX_RECORDING_SECONDS)
        val expectedMaxBytes = 30 * 32000 // 960,000 bytes
        assertEquals("MAX_RECORDING_BYTES must equal 960,000 bytes (~0.96 MB)", expectedMaxBytes, AudioRecorder.MAX_RECORDING_BYTES)

        // Verify buffer floor
        assertEquals(4096, AudioRecorder.MIN_BUFFER_FLOOR)
        assertTrue(AudioRecorder.calculateBufferSize() >= AudioRecorder.MIN_BUFFER_FLOOR)
    }

    @Test
    fun testAudioRecorderPeakAmplitudeMathAccuracy() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)

        // Test cases: (Sample value as Short, Expected normalized float)
        val sampleScenarios = listOf(
            Pair(Short.MAX_VALUE, 1.0f),                // 32767 -> 1.0f
            Pair(Short.MIN_VALUE, 1.0f),                // -32768 -> abs(-32768) = 32768 -> coerced to 1.0f
            Pair(16384.toShort(), 16384f / 32767f),    // ~0.5f
            Pair(0.toShort(), 0.0f),                    // 0 -> 0.0f
            Pair((-16384).toShort(), 16384f / 32767f)  // ~0.5f
        )

        for ((sampleVal, expectedNormalized) in sampleScenarios) {
            val chunk = ByteArray(4)
            chunk[0] = (sampleVal.toInt() and 0xFF).toByte()
            chunk[1] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
            chunk[2] = 0
            chunk[3] = 0

            val fakeRecord = MockNativeAudioRecord(chunksToSupply = listOf(chunk))
            val recorder = AudioRecorder(
                ioDispatcher = testDispatcher,
                recordProvider = { _, _, _, _, _ -> fakeRecord }
            )

            val flow = recorder.startRecording(this)
            testScheduler.runCurrent()

            val actualPeak = flow.value
            assertEquals(
                "Peak amplitude for sample $sampleVal must match expected $expectedNormalized",
                expectedNormalized,
                actualPeak,
                0.01f
            )

            recorder.stopRecording()
            advanceUntilIdle()
            assertEquals("Amplitude must reset to 0 after stop", 0.0f, flow.value, 0.0f)
        }
    }

    @Test
    fun testAudioRecorderReentrancyAndLifecycle() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fakeRecord = MockNativeAudioRecord(chunksToSupply = listOf(ByteArray(1000) { 0x05 }))
        val recorder = AudioRecorder(
            ioDispatcher = testDispatcher,
            recordProvider = { _, _, _, _, _ -> fakeRecord }
        )

        // Stop when not started
        val stoppedBytes = recorder.stopRecording()
        assertEquals(0, stoppedBytes.size)
        assertFalse(recorder.isRecording)

        // Start recording
        val amp1 = recorder.startRecording(this)
        assertTrue(recorder.isRecording)

        // Start recording while already recording (re-entrant call)
        val amp2 = recorder.startRecording(this)
        assertTrue("Re-entrant call must return same amplitude flow", amp1 === amp2)
        assertTrue(recorder.isRecording)

        testScheduler.runCurrent()

        // Stop recording
        val captured = recorder.stopRecording()
        advanceUntilIdle()

        assertEquals(1000, captured.size)
        assertFalse(recorder.isRecording)
    }

    @Test
    fun testAudioRecorderRapidStartStopCycles() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)

        for (cycle in 0 until 10) {
            val fakeRecord = MockNativeAudioRecord(chunksToSupply = listOf(ByteArray(100) { cycle.toByte() }))
            val recorder = AudioRecorder(
                ioDispatcher = testDispatcher,
                recordProvider = { _, _, _, _, _ -> fakeRecord }
            )

            recorder.startRecording(this)
            testScheduler.runCurrent()
            assertTrue("Cycle $cycle: must be recording", recorder.isRecording)

            val captured = recorder.stopRecording()
            advanceUntilIdle()
            assertFalse("Cycle $cycle: must be stopped", recorder.isRecording)
            assertEquals(100, captured.size)
            assertEquals(cycle.toByte(), captured[0])
        }
    }
}
