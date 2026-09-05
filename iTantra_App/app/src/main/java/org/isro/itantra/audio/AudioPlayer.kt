package org.isro.itantra.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.isro.itantra.tts.GeneratedAudio
import org.isro.itantra.tts.TtsAudioAdapter

/**
 * Low-latency Dynamic PCM Audio Player using AudioTrack.
 * Features:
 * - Dynamic sample rate switching (16 kHz mic playback, 22.05 kHz TTS voice synthesis)
 * - Direct playback of GeneratedAudio from neural TTS
 * - Pre-buffering to prevent start pops and drain detection to prevent tail truncation
 * - AudioAttributes priority routing (USAGE_MEDIA vs USAGE_ALARM)
 */
class AudioPlayer(
    private val onPlaybackStarted: (() -> Unit)? = null,
    private val onPlaybackFinished: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "AudioPlayer"
    }

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null

    @Volatile
    var isPlaying: Boolean = false
        private set

    /**
     * Plays a GeneratedAudio instance directly from Milestone 2 TTS.
     */
    fun playGeneratedAudio(
        scope: CoroutineScope,
        audio: GeneratedAudio,
        isAlarmPriority: Boolean = false
    ): Boolean {
        if (audio.samples.isEmpty()) return false
        val pcmBytes = TtsAudioAdapter.floatsToPcm16(audio.samples)
        return playPcm(
            scope = scope,
            pcmData = pcmBytes,
            sampleRate = audio.sampleRate,
            isAlarmPriority = isAlarmPriority
        )
    }

    fun playPcm(
        scope: CoroutineScope,
        pcmData: ByteArray,
        sampleRate: Int = AudioConfig.SAMPLE_RATE,
        isAlarmPriority: Boolean = false
    ): Boolean {
        if (isPlaying || pcmData.isEmpty()) return false

        val minBufSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioConfig.CHANNEL_OUT,
            AudioConfig.AUDIO_ENCODING
        )
        if (minBufSize <= 0) {
            Log.e(TAG, "AudioTrack parameter error: minBufferSize = $minBufSize @ $sampleRate Hz")
            return false
        }

        val frameSizeBytes = ((sampleRate * AudioConfig.FRAME_DURATION_MS) / 1000) * AudioConfig.BYTES_PER_SAMPLE
        val internalBufferSize = maxOf(minBufSize * 2, frameSizeBytes * 4)

        val attributes = AudioAttributes.Builder().apply {
            if (isAlarmPriority) {
                setUsage(AudioAttributes.USAGE_ALARM)
                setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
            } else {
                setUsage(AudioAttributes.USAGE_MEDIA)
                setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            }
        }.build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioConfig.AUDIO_ENCODING)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioConfig.CHANNEL_OUT)
            .build()

        val builder = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(internalBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }

        val track = try {
            builder.build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build AudioTrack", e)
            return false
        }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "Failed to initialize AudioTrack")
            track.release()
            return false
        }

        audioTrack = track
        isPlaying = true

        playbackJob = scope.launch(Dispatchers.IO) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            try {
                val chunkSize = frameSizeBytes
                val totalBytes = pcmData.size
                var offset = 0
                var startedPlaying = false
                val totalFrames = totalBytes / AudioConfig.BYTES_PER_SAMPLE

                while (isActive && isPlaying && offset < totalBytes) {
                    val bytesToWrite = minOf(chunkSize, totalBytes - offset)
                    val written = track.write(pcmData, offset, bytesToWrite, AudioTrack.WRITE_BLOCKING)

                    if (written > 0) {
                        offset += written

                        // Pre-buffering: start playback after priming buffer
                        if (!startedPlaying && (offset >= chunkSize * 2 || offset >= totalBytes)) {
                            track.play()
                            startedPlaying = true
                            withContext(Dispatchers.Main) {
                                onPlaybackStarted?.invoke()
                            }
                        }
                    } else if (written < 0) {
                        Log.e(TAG, "AudioTrack.write error: $written")
                        break
                    }
                }

                if (startedPlaying && isPlaying) {
                    // Drain buffer so speech tail is not truncated
                    track.stop()
                    val maxWaitMs = (totalFrames * 1000L / sampleRate) + 500L
                    val drainStart = System.currentTimeMillis()
                    while (isActive && isPlaying && track.playbackHeadPosition < totalFrames && (System.currentTimeMillis() - drainStart < maxWaitMs)) {
                        kotlinx.coroutines.delay(10)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during PCM playback", e)
            } finally {
                withContext(kotlinx.coroutines.NonCancellable) {
                    releaseTrack()
                    withContext(Dispatchers.Main) {
                        onPlaybackFinished?.invoke()
                    }
                }
            }
        }
        return true
    }

    fun stop() {
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null
        releaseTrack()
    }

    private fun releaseTrack() {
        try {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.pause()
                    track.flush()
                    track.stop()
                    track.release()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioTrack", e)
        } finally {
            audioTrack = null
            isPlaying = false
        }
    }
}
