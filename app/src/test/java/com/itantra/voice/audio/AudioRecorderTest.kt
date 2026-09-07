package com.itantra.voice.audio

import android.media.AudioFormat
import android.media.AudioRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioRecorderTest {

    private class FakeNativeAudioRecord(
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

        fun hasMoreData(): Boolean = chunkIndex < chunksToSupply.size

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
            if (readErrorCode != null) {
                return readErrorCode
            }
            if (chunkIndex >= chunksToSupply.size) {
                return 0
            }
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

    // Tier 1 Tests: Nominal Feature Coverage for F1 (Native PTT Audio Recording)

    @Test
    fun testRecordingInitializationConfigures16khzMono16bitPcm() = runTest {
        var configuredSource = -1
        var configuredRate = -1
        var configuredChannel = -1
        var configuredFormat = -1
        var configuredBufferSize = -1

        val fake = FakeNativeAudioRecord()
        val recorder = AudioRecorder(
            ioDispatcher = StandardTestDispatcher(testScheduler),
            recordProvider = { source, rate, channel, format, size ->
                configuredSource = source
                configuredRate = rate
                configuredChannel = channel
                configuredFormat = format
                configuredBufferSize = size
                fake
            }
        )

        recorder.startRecording(this)
        testScheduler.runCurrent()

        assertEquals(16000, configuredRate)
        assertEquals(AudioFormat.CHANNEL_IN_MONO, configuredChannel)
        assertEquals(AudioFormat.ENCODING_PCM_16BIT, configuredFormat)
        assertTrue("Buffer size must be at least 4096", configuredBufferSize >= 4096)
        assertTrue(fake.startRecordingCalled)
        assertTrue(recorder.isRecording)

        recorder.stopRecording()
        advanceUntilIdle()
    }

    @Test
    fun testRecordingStreamsPcmChunksToOutputBuffer() = runTest {
        val chunk1 = ByteArray(1000) { 1 }
        val chunk2 = ByteArray(1000) { 2 }
        val fake = FakeNativeAudioRecord(chunksToSupply = listOf(chunk1, chunk2))

        val recorder = AudioRecorder(
            ioDispatcher = StandardTestDispatcher(testScheduler),
            recordProvider = { _, _, _, _, _ -> fake }
        )

        recorder.startRecording(this)
        testScheduler.runCurrent()

        val captured = recorder.stopRecording()
        advanceUntilIdle()

        assertEquals(2000, captured.size)
        assertEquals(1.toByte(), captured[0])
        assertEquals(2.toByte(), captured[1500])
        assertFalse(recorder.isRecording)
    }

    @Test
    fun testStopRecordingReturnsCapturedPcmByteArray() = runTest {
        val data = ByteArray(3200) { (it % 100).toByte() }
        val fake = FakeNativeAudioRecord(chunksToSupply = listOf(data))

        val recorder = AudioRecorder(
            ioDispatcher = StandardTestDispatcher(testScheduler),
            recordProvider = { _, _, _, _, _ -> fake }
        )

        recorder.startRecording(this)
        testScheduler.runCurrent()

        val captured = recorder.stopRecording()
        advanceUntilIdle()

        assertEquals(3200, captured.size)
        assertEquals(data[50], captured[50])
        assertEquals(data[2500], captured[2500])
        assertFalse(recorder.isRecording)
    }

    @Test
    fun testAudioAmplitudeFlowEmitsDynamicPeakValues() = runTest {
        // Construct 16-bit PCM sample with peak amplitude 16384 (half of 32767 -> ~0.5)
        val sampleVal: Short = 16384
        val chunk = ByteArray(4)
        chunk[0] = (sampleVal.toInt() and 0xFF).toByte()
        chunk[1] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
        chunk[2] = 0
        chunk[3] = 0

        val fake = FakeNativeAudioRecord(chunksToSupply = listOf(chunk))
        val recorder = AudioRecorder(
            ioDispatcher = StandardTestDispatcher(testScheduler),
            recordProvider = { _, _, _, _, _ -> fake }
        )

        val amplitudeFlow = recorder.startRecording(this)
        testScheduler.runCurrent()

        val peak = amplitudeFlow.value
        assertTrue("Normalized amplitude should be around 0.5, was: $peak", peak in 0.49f..0.51f)

        recorder.stopRecording()
        advanceUntilIdle()
        assertEquals("Amplitude resets to 0 after stop", 0f, amplitudeFlow.value)
    }

    @Test
    fun testRecordingResourceReleaseStopsHardwareCleanly() = runTest {
        val fake = FakeNativeAudioRecord()
        val recorder = AudioRecorder(
            ioDispatcher = StandardTestDispatcher(testScheduler),
            recordProvider = { _, _, _, _, _ -> fake }
        )

        recorder.startRecording(this)
        testScheduler.runCurrent()

        recorder.release()
        advanceUntilIdle()

        assertTrue("stop() must be called on record", fake.stopCalled)
        assertTrue("release() must be called on record", fake.releaseCalled)
        assertFalse(recorder.isRecording)
    }

    // Tier 2 Tests: Boundary & Corner Cases (F1 Audio Capture Boundaries)

    @Test
    fun testZeroByteRecordingHandlesEmptyBufferWithoutCrash() = runTest {
        val fake = FakeNativeAudioRecord(chunksToSupply = emptyList())
        val recorder = AudioRecorder(
            ioDispatcher = StandardTestDispatcher(testScheduler),
            recordProvider = { _, _, _, _, _ -> fake }
        )

        recorder.startRecording(this)
        val captured = recorder.stopRecording()
        advanceUntilIdle()

        assertNotNull(captured)
        assertEquals(0, captured.size)
        assertFalse(recorder.isRecording)
    }

    @Test
    fun testMaxDurationRecordingAutoStopsAt30Seconds() = runTest {
        // AudioRecorder.MAX_RECORDING_BYTES = 960,000 bytes
        // Supply large buffer (1,000,000 bytes)
        val largeBuffer = ByteArray(1_000_000)
        val fake = FakeNativeAudioRecord(chunksToSupply = listOf(largeBuffer))

        val recorder = AudioRecorder(
            ioDispatcher = StandardTestDispatcher(testScheduler),
            recordProvider = { _, _, _, _, _ -> fake }
        )

        recorder.startRecording(this)
        testScheduler.runCurrent()

        // Loop auto-stopped because MAX_RECORDING_BYTES was reached
        assertTrue("stop() must be called when 30s auto-stop triggers", fake.stopCalled)
        val captured = recorder.stopRecording()
        advanceUntilIdle()
        assertTrue("Captured bytes must be at least 960,000: ${captured.size}", captured.size >= 960_000)
        assertTrue("Captured bytes should not exceed threshold significantly: ${captured.size}", captured.size <= 963_000)
    }

    @Test
    fun testUninitializedAudioRecordTransitionsToError() = runTest {
        val fake = FakeNativeAudioRecord(state = AudioRecord.STATE_UNINITIALIZED)
        val recorder = AudioRecorder(
            ioDispatcher = StandardTestDispatcher(testScheduler),
            recordProvider = { _, _, _, _, _ -> fake }
        )

        try {
            recorder.startRecording(this)
            fail("Expected IllegalStateException for uninitialized AudioRecord")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("uninitialized"))
            assertFalse(recorder.isRecording)
        }
    }

    @Test
    fun testMicrophoneInUseByOtherAppHandledGracefully() = runTest {
        val fake = FakeNativeAudioRecord(readErrorCode = AudioRecord.ERROR_INVALID_OPERATION)
        val recorder = AudioRecorder(
            ioDispatcher = StandardTestDispatcher(testScheduler),
            recordProvider = { _, _, _, _, _ -> fake }
        )

        recorder.startRecording(this)
        testScheduler.runCurrent()

        // Read error should break the loop cleanly without throwing unhandled exception
        val captured = recorder.stopRecording()
        advanceUntilIdle()
        assertEquals(0, captured.size)
        assertFalse(recorder.isRecording)
    }
}
