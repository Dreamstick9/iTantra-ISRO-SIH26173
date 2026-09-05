package org.isro.itantra.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Low-latency 16 kHz Mono 16-bit PCM Audio Recorder.
 * Manages AudioRecord on Dispatchers.IO with real-time thread priority.
 */
class AudioRecorder(
    private val onAmplitudeChanged: ((Float) -> Unit)? = null,
    private val onFirstChunkCaptured: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "AudioRecorder"
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val recordedDataStream = ByteArrayOutputStream()

    @Volatile
    var isRecording: Boolean = false
        private set

    @SuppressLint("MissingPermission")
    fun startRecording(
        scope: CoroutineScope,
        onAudioChunk: ((ByteArray, Int) -> Unit)? = null
    ): Boolean {
        if (isRecording) return false

        val minBufSize = AudioRecord.getMinBufferSize(
            AudioConfig.SAMPLE_RATE,
            AudioConfig.CHANNEL_IN,
            AudioConfig.AUDIO_ENCODING
        )
        if (minBufSize <= 0) {
            Log.e(TAG, "AudioRecord parameter error: minBufferSize = $minBufSize")
            return false
        }

        val internalBufferSize = maxOf(minBufSize * 2, AudioConfig.FRAME_SIZE_BYTES * 4)

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioConfig.AUDIO_ENCODING)
            .setSampleRate(AudioConfig.SAMPLE_RATE)
            .setChannelMask(AudioConfig.CHANNEL_IN)
            .build()

        val record = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(internalBufferSize)
            .build()

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "Failed to initialize AudioRecord")
            record.release()
            return false
        }

        audioRecord = record
        recordedDataStream.reset()
        isRecording = true

        recordingJob = scope.launch(Dispatchers.IO) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            try {
                record.startRecording()
                Log.d(TAG, "AudioRecord started recording with source=MIC, sampleRate=${AudioConfig.SAMPLE_RATE}")

                val buffer = ByteArray(AudioConfig.FRAME_SIZE_BYTES)
                var isFirstFrame = true
                var chunkCount = 0

                while (isActive && isRecording) {
                    val bytesRead = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)

                    if (bytesRead > 0) {
                        if (isFirstFrame) {
                            isFirstFrame = false
                            onFirstChunkCaptured?.invoke()
                        }

                        // Accumulate for loopback playback
                        recordedDataStream.write(buffer, 0, bytesRead)

                        // Compute RMS amplitude for UI visualization
                        val rms = AudioUtils.calculateRmsFromPcm16(buffer, 0, bytesRead)
                        val level = AudioUtils.calculateVisualizerLevel(rms)
                        onAmplitudeChanged?.invoke(level)

                        var peak = 0
                        for (i in 0 until bytesRead step 2) {
                            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort().toInt()
                            val absS = kotlin.math.abs(sample)
                            if (absS > peak) peak = absS
                        }

                        if (chunkCount++ % 10 == 0 || peak > 100) {
                            Log.d(TAG, "PCM chunk #$chunkCount: bytesRead=$bytesRead, peak=$peak, rms=$rms, level=$level")
                        }

                        // Emit chunk to optional subscriber
                        onAudioChunk?.invoke(buffer.copyOf(bytesRead), bytesRead)
                    } else if (bytesRead < 0) {
                        Log.e(TAG, "AudioRecord.read error: $bytesRead")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in AudioRecord loop", e)
            } finally {
                withContext(kotlinx.coroutines.NonCancellable) {
                    releaseRecord()
                }
            }
        }
        return true
    }

    fun stopRecording(): ByteArray {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        releaseRecord()
        return recordedDataStream.toByteArray()
    }

    private fun releaseRecord() {
        try {
            audioRecord?.let { record ->
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord", e)
        } finally {
            audioRecord = null
            isRecording = false
            onAmplitudeChanged?.invoke(0.0f)
        }
    }
}
