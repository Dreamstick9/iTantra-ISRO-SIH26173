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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.isro.itantra.tts.GeneratedAudio
import org.isro.itantra.tts.TtsAudioAdapter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Production Low-Latency PCM AudioTrack Playback Engine.
 *
 * Specifications:
 * - Format: 16 kHz Mono 16-bit Linear PCM (configurable to 22.05 kHz for neural TTS)
 * - Routing: AudioAttributes.USAGE_MEDIA / FLAG_LOW_LATENCY / CONTENT_TYPE_SPEECH
 * - Mode: AudioTrack.MODE_STREAM with PERFORMANCE_MODE_LOW_LATENCY
 * - Synchronization: Mutex/State guard preventing native JNI release/write race conditions
 * - Pre-buffering: 40 ms priming threshold to eliminate DAC startup clicks/pops
 * - Non-blocking UI dispatch: Prevents UI frame drops from stalling the audio write loop
 * - Tail-drain: Frame-accurate playbackHeadPosition tracking with dynamic timeout & stall detection
 */
class AudioPlayer(
    private val onPlaybackStarted: (() -> Unit)? = null,
    private val onPlaybackFinished: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "AudioPlayer"

        /** 40 ms priming pre-buffer threshold (2 speech frames @ 20ms) */
        private const val PRE_BUFFER_DURATION_MS = 40

        /** Safety margin added to dynamic drain timeout (ms) */
        private const val DRAIN_SAFETY_MARGIN_MS = 300L

        /** Stall detection threshold: max consecutive unchanged checks before aborting drain */
        private const val STALL_CHECK_LIMIT = 15 // 15 * 10ms = 150ms
    }

    private val trackLock = Any()
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null

    private val _isPlaying = AtomicBoolean(false)
    val isPlaying: Boolean
        get() = _isPlaying.get()

    /**
     * Plays a GeneratedAudio instance from on-device neural TTS.
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

    /**
     * Plays raw 16-bit Linear PCM audio data with low-latency AudioTrack configuration.
     */
    fun playPcm(
        scope: CoroutineScope,
        pcmData: ByteArray,
        sampleRate: Int = AudioConfig.SAMPLE_RATE,
        isAlarmPriority: Boolean = false
    ): Boolean {
        if (pcmData.isEmpty()) return false

        // Atomically ensure only one playback session runs at a time
        if (!_isPlaying.compareAndSet(false, true)) {
            Log.w(TAG, "playPcm rejected: Playback already in progress")
            return false
        }

        // 1. Calculate buffer constraints
        val minBufSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufSize <= 0) {
            Log.e(TAG, "AudioTrack parameter error: minBufferSize = $minBufSize @ $sampleRate Hz")
            _isPlaying.set(false)
            return false
        }

        val frameSizeBytes = ((sampleRate * AudioConfig.FRAME_DURATION_MS) / 1000) * AudioConfig.BYTES_PER_SAMPLE
        val internalBufferSize = maxOf(minBufSize * 2, frameSizeBytes * 4)

        // 2. Build AudioAttributes with low-latency speech profile
        val attributes = AudioAttributes.Builder().apply {
            if (isAlarmPriority) {
                setUsage(AudioAttributes.USAGE_ALARM)
                setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
            } else {
                setUsage(AudioAttributes.USAGE_MEDIA)
                setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                @Suppress("DEPRECATION")
                setFlags(AudioAttributes.FLAG_LOW_LATENCY)
            }
        }.build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
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
            _isPlaying.set(false)
            return false
        }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "Failed to initialize AudioTrack (state != STATE_INITIALIZED)")
            track.release()
            _isPlaying.set(false)
            return false
        }

        // Optimize active latency buffer on API 24+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val targetFrames = (internalBufferSize / AudioConfig.BYTES_PER_SAMPLE) / 2
            val setFrames = track.setBufferSizeInFrames(targetFrames)
            Log.d(TAG, "Configured bufferSizeInFrames: target=$targetFrames, actual=$setFrames")
        }

        synchronized(trackLock) {
            audioTrack = track
        }

        // 3. Launch dedicated audio writing coroutine on Dispatchers.IO
        playbackJob = scope.launch(Dispatchers.IO) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

            var totalBytesWritten = 0
            val totalBytes = pcmData.size
            val chunkSize = frameSizeBytes // 640 bytes = 20ms slice
            val preBufferByteThreshold = minOf(
                totalBytes,
                maxOf((sampleRate * PRE_BUFFER_DURATION_MS / 1000) * AudioConfig.BYTES_PER_SAMPLE, chunkSize * 2)
            )
            var startedPlaying = false

            try {
                while (isActive && _isPlaying.get() && totalBytesWritten < totalBytes) {
                    val bytesToWrite = minOf(chunkSize, totalBytes - totalBytesWritten)

                    val written = synchronized(trackLock) {
                        if (audioTrack == null || track.state != AudioTrack.STATE_INITIALIZED) {
                            -1
                        } else {
                            track.write(pcmData, totalBytesWritten, bytesToWrite, AudioTrack.WRITE_BLOCKING)
                        }
                    }

                    if (written > 0) {
                        totalBytesWritten += written

                        // Pre-buffering: Prime hardware pipeline before starting DAC clock
                        if (!startedPlaying && totalBytesWritten >= preBufferByteThreshold) {
                            synchronized(trackLock) {
                                if (track.state == AudioTrack.STATE_INITIALIZED) {
                                    track.play()
                                    startedPlaying = true
                                }
                            }
                            if (startedPlaying) {
                                // Dispatch to UI asynchronously (never block/suspend audio thread)
                                scope.launch(Dispatchers.Main) {
                                    onPlaybackStarted?.invoke()
                                }
                            }
                        }
                    } else if (written == 0) {
                        // Buffer full or zero written: yield briefly to prevent tight CPU spin
                        delay(5)
                    } else {
                        // Error handling
                        when (written) {
                            AudioTrack.ERROR_DEAD_OBJECT -> Log.e(TAG, "AudioTrack write error: ERROR_DEAD_OBJECT (AudioFlinger died)")
                            AudioTrack.ERROR_INVALID_OPERATION -> Log.e(TAG, "AudioTrack write error: ERROR_INVALID_OPERATION")
                            AudioTrack.ERROR_BAD_VALUE -> Log.e(TAG, "AudioTrack write error: ERROR_BAD_VALUE")
                            else -> Log.e(TAG, "AudioTrack write error: $written")
                        }
                        break
                    }
                }

                // If audio was shorter than preBufferByteThreshold, start playback now
                if (!startedPlaying && totalBytesWritten > 0 && _isPlaying.get()) {
                    synchronized(trackLock) {
                        if (track.state == AudioTrack.STATE_INITIALIZED) {
                            track.play()
                            startedPlaying = true
                        }
                    }
                    if (startedPlaying) {
                        scope.launch(Dispatchers.Main) {
                            onPlaybackStarted?.invoke()
                        }
                    }
                }

                // 4. Tail Drain: Wait for remaining buffer frames to render through hardware
                if (startedPlaying && _isPlaying.get() && totalBytesWritten > 0) {
                    val totalFramesWritten = totalBytesWritten / AudioConfig.BYTES_PER_SAMPLE

                    synchronized(trackLock) {
                        if (track.state == AudioTrack.STATE_INITIALIZED && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            track.stop() // MODE_STREAM: Drain pending buffers, then stop
                        }
                    }

                    val initialHead = (track.playbackHeadPosition.toLong() and 0xFFFFFFFFL)
                    val remainingFrames = maxOf(0L, totalFramesWritten - initialHead)
                    val dynamicTimeoutMs = (remainingFrames * 1000L / sampleRate) + DRAIN_SAFETY_MARGIN_MS
                    val drainStartTime = System.currentTimeMillis()

                    var lastHead = initialHead
                    var stallCounter = 0

                    while (isActive && _isPlaying.get()) {
                        val currentHead = (track.playbackHeadPosition.toLong() and 0xFFFFFFFFL)
                        val isStopped = (track.playState == AudioTrack.PLAYSTATE_STOPPED)

                        if (currentHead >= totalFramesWritten || isStopped) {
                            break // Buffer fully drained
                        }

                        if (System.currentTimeMillis() - drainStartTime > dynamicTimeoutMs) {
                            Log.w(TAG, "Tail drain timeout reached (${dynamicTimeoutMs}ms). Exiting drain loop.")
                            break
                        }

                        if (currentHead == lastHead) {
                            stallCounter++
                            if (stallCounter >= STALL_CHECK_LIMIT) {
                                Log.w(TAG, "Audio hardware stall detected during tail drain. Aborting.")
                                break
                            }
                        } else {
                            stallCounter = 0
                            lastHead = currentHead
                        }

                        delay(10)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during PCM playback", e)
            } finally {
                withContext(kotlinx.coroutines.NonCancellable) {
                    internalCleanUp()
                    scope.launch(Dispatchers.Main) {
                        onPlaybackFinished?.invoke()
                    }
                }
            }
        }
        return true
    }

    /**
     * Immediately stops playback, flushes queued buffers, and safely releases resources.
     */
    fun stop() {
        if (!_isPlaying.getAndSet(false)) return

        // Abort audio coroutine
        playbackJob?.cancel()
        playbackJob = null

        // Immediately flush and release on caller thread under synchronization
        internalCleanUp()
    }

    private fun internalCleanUp() {
        synchronized(trackLock) {
            val track = audioTrack ?: return
            try {
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    try {
                        if (track.playState != AudioTrack.PLAYSTATE_STOPPED) {
                            track.pause()
                            track.flush()
                            track.stop()
                        }
                    } catch (e: IllegalStateException) {
                        Log.w(TAG, "Non-critical state exception during track stop/flush", e)
                    }
                    track.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing AudioTrack", e)
            } finally {
                audioTrack = null
                _isPlaying.set(false)
            }
        }
    }
}
