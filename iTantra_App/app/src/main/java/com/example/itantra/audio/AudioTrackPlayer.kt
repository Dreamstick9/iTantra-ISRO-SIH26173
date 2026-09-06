package com.example.itantra.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.itantra.tts.TtsPlaybackState
import com.example.itantra.util.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thread-safe background audio player utilizing Android AudioTrack for raw PCM16 playback.
 * Guaranteed to run strictly off the main UI thread with safe lifecycle management and clean interruption.
 */
class AudioTrackPlayer(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val TAG = "AudioTrackPlayer"

    private val playerScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val playMutex = Mutex()

    private val _playbackState = MutableStateFlow(TtsPlaybackState.IDLE)
    val playbackState: StateFlow<TtsPlaybackState> = _playbackState.asStateFlow()

    @Volatile
    private var activeTrack: AudioTrack? = null

    @Volatile
    private var currentPlayJob: Job? = null

    private val isInterrupted = AtomicBoolean(false)

    /**
     * Plays the given 16-bit linear PCM audio byte array on Dispatchers.IO.
     * Non-blocking from UI perspective, but suspends until playback finishes or is cancelled.
     */
    suspend fun play(pcmData: ByteArray, sampleRate: Int) = withContext(ioDispatcher) {
        if (pcmData.isEmpty()) {
            Logger.d(TAG, "PCM data is empty, skipping playback.")
            _playbackState.value = TtsPlaybackState.IDLE
            return@withContext
        }

        playMutex.withLock {
            // Stop any previous active track
            stopInternal()
            isInterrupted.set(false)

            currentPlayJob = coroutineContext[Job]

            var audioTrack: AudioTrack? = null
            try {
                _playbackState.value = TtsPlaybackState.PLAYING

                val channelConfig = AudioFormat.CHANNEL_OUT_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                val bufferSize = maxOf(minBufferSize * 2, 8192)

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val format = AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                activeTrack = audioTrack

                if (audioTrack.state != AudioTrack.STATE_INITIALIZED) {
                    Logger.e(TAG, "AudioTrack failed to initialize (state: ${audioTrack.state})")
                    _playbackState.value = TtsPlaybackState.ERROR
                    return@withLock
                }

                audioTrack.setVolume(1.0f)
                audioTrack.play()

                val totalFrames = pcmData.size / 2
                val totalDurationMs = (totalFrames * 1000L) / sampleRate
                val playStartTime = System.currentTimeMillis()
                Logger.d(TAG, "AudioTrack started playback: ${pcmData.size} bytes ($totalFrames frames, ~${totalDurationMs}ms) at $sampleRate Hz")

                val chunkSize = 4096
                var offset = 0

                while (offset < pcmData.size && !isInterrupted.get() && coroutineContext.isActive) {
                    val bytesToWrite = minOf(chunkSize, pcmData.size - offset)
                    val written = audioTrack.write(pcmData, offset, bytesToWrite, AudioTrack.WRITE_BLOCKING)

                    if (written < 0) {
                        Logger.e(TAG, "AudioTrack write error: $written")
                        break
                    }
                    offset += written
                }

                // If not interrupted, wait for the track to finish rendering all frames
                if (!isInterrupted.get() && coroutineContext.isActive) {
                    val maxWaitMs = totalDurationMs + 800L
                    while (coroutineContext.isActive && !isInterrupted.get()) {
                        val head = audioTrack.playbackHeadPosition
                        val elapsed = System.currentTimeMillis() - playStartTime
                        if (head >= totalFrames) {
                            Logger.d(TAG, "Playback completed: all $totalFrames frames played out (head=$head, elapsed=${elapsed}ms)")
                            break
                        }
                        if (elapsed >= maxWaitMs) {
                            Logger.d(TAG, "Playback drain completed by duration (elapsed=${elapsed}ms, head=$head/$totalFrames)")
                            break
                        }
                        delay(25L)
                    }
                }

                if (isInterrupted.get()) {
                    Logger.i(TAG, "Playback was interrupted by user stop.")
                    _playbackState.value = TtsPlaybackState.STOPPED
                } else {
                    Logger.i(TAG, "Playback completed successfully.")
                    _playbackState.value = TtsPlaybackState.IDLE
                }

            } catch (ce: CancellationException) {
                Logger.i(TAG, "Playback coroutine cancelled.")
                _playbackState.value = TtsPlaybackState.STOPPED
                throw ce
            } catch (t: Throwable) {
                Logger.e(TAG, "Playback error: ${t.message}", t)
                _playbackState.value = TtsPlaybackState.ERROR
            } finally {
                safeReleaseTrack(audioTrack, isInterrupted.get())
                activeTrack = null
                currentPlayJob = null
            }
        }
    }

    /**
     * Immediately interrupts active AudioTrack playback safely.
     */
    suspend fun stop() = withContext(ioDispatcher) {
        stopInternal()
    }

    private fun stopInternal() {
        isInterrupted.set(true)
        currentPlayJob?.cancel()

        activeTrack?.let { track ->
            try {
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.pause()
                        track.flush()
                        track.stop()
                    }
                }
            } catch (e: Exception) {
                Logger.w(TAG, "Error stopping active track: ${e.message}")
            }
        }
        _playbackState.value = TtsPlaybackState.STOPPED
    }

    private fun safeReleaseTrack(track: AudioTrack?, wasInterrupted: Boolean = false) {
        if (track == null) return
        try {
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                if (wasInterrupted) {
                    track.pause()
                    track.flush()
                }
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Error releasing AudioTrack: ${e.message}")
        }
    }

    fun release() {
        playerScope.cancel()
        stopInternal()
        activeTrack?.let { safeReleaseTrack(it) }
        activeTrack = null
    }
}
