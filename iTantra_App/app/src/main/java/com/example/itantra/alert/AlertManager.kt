package com.example.itantra.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.example.itantra.audio.AudioTrackPlayer
import com.example.itantra.transport.TransportMessage
import com.example.itantra.transport.TransportMessageType
import com.example.itantra.tts.AudioData
import com.example.itantra.util.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Result details from executing a high-priority emergency alert playback.
 * Provides transparent, honest telemetry without false promises of non-dismissibility.
 */
data class AlertPlaybackResult(
    val success: Boolean,
    val focusGranted: Boolean,
    val appliedVolume: Int,
    val maxPermittedVolume: Int,
    val durationMs: Long,
    val errorMessage: String? = null
) {
    fun formattedSummary(): String =
        "Alert Playback [Success: $success | Focus: ${if (focusGranted) "GRANTED (EXCLUSIVE)" else "DENIED"} | Vol: $appliedVolume/$maxPermittedVolume | Dur: ${durationMs}ms]"
}

/**
 * Contract for managing emergency mode and high-priority alarm-class audio routing.
 */
interface AlertManager {
    /**
     * Inspects an incoming message and determines if it qualifies as an ALERT.
     */
    fun isAlert(message: TransportMessage): Boolean

    /**
     * Returns whether an emergency alert audio playback is currently taking over the audio channel.
     */
    fun isAlertInProgress(): Boolean

    /**
     * Executes the given block under the alert lock, ensuring normal transceiver messages
     * and normal PTT requests are gated until the high-priority broadcast finishes.
     */
    suspend fun <T> withAlertLock(block: suspend () -> T): T

    /**
     * Executes the complete emergency audio flow:
     * 1. Acquires AudioFocus (AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE with USAGE_ALARM).
     * 2. Elevates STREAM_ALARM to the system-permitted maximum (respecting system/DND policies).
     * 3. Plays full generated neural TTS through AudioTrackPlayer with USAGE_ALARM & FLAG_AUDIBILITY_ENFORCED.
     * 4. Drains playback, restores stream volume, and releases audio focus.
     */
    suspend fun playEmergencyAlert(
        text: String,
        audioData: AudioData,
        onPlaybackStarted: (() -> Unit)? = null
    ): AlertPlaybackResult
}

/**
 * Production implementation of [AlertManager] interacting directly with Android's [AudioManager].
 */
class AndroidAlertManager(
    private val context: Context?,
    private val audioTrackPlayer: AudioTrackPlayer,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AlertManager {

    private val TAG = "AlertManager"
    private val alertLock = Mutex()
    private val _isAlertInProgress = AtomicBoolean(false)

    override fun isAlert(message: TransportMessage): Boolean {
        return message.messageType == TransportMessageType.ALERT || message.priority >= 1
    }

    override fun isAlertInProgress(): Boolean = _isAlertInProgress.get()

    override suspend fun <T> withAlertLock(block: suspend () -> T): T {
        return alertLock.withLock {
            block()
        }
    }

    override suspend fun playEmergencyAlert(
        text: String,
        audioData: AudioData,
        onPlaybackStarted: (() -> Unit)?
    ): AlertPlaybackResult = withContext(ioDispatcher) {
        if (audioData.isEmpty) {
            Logger.w(TAG, "Empty audio data provided for alert playback: \"$text\"")
            return@withContext AlertPlaybackResult(
                success = false,
                focusGranted = false,
                appliedVolume = 0,
                maxPermittedVolume = 0,
                durationMs = 0L,
                errorMessage = "AudioData is empty"
            )
        }

        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        val alarmAttributes = try {
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                .build()
        } catch (t: Throwable) {
            null
        }

        var focusRequest: AudioFocusRequest? = null
        var focusGranted = false

        // 1. Request Exclusive Audio Focus
        try {
            if (audioManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && alarmAttributes != null) {
                    val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                        .setAudioAttributes(alarmAttributes)
                        .setAcceptsDelayedFocusGain(false)
                        .setOnAudioFocusChangeListener { focusChange ->
                            Logger.d(TAG, "Alert AudioFocus changed: $focusChange")
                        }
                        .build()
                    focusRequest = request
                    val res = audioManager.requestAudioFocus(request)
                    focusGranted = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                } else {
                    @Suppress("DEPRECATION")
                    val res = audioManager.requestAudioFocus(
                        null,
                        AudioManager.STREAM_ALARM,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                    )
                    focusGranted = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                }
            } else {
                focusGranted = true
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Could not acquire AudioFocus: ${e.message}")
            focusGranted = false
        }

        // 2. Query and Elevate Stream Volume (respecting system policies)
        val previousVolume = try {
            audioManager?.getStreamVolume(AudioManager.STREAM_ALARM) ?: -1
        } catch (e: Exception) {
            Logger.w(TAG, "Error querying STREAM_ALARM volume: ${e.message}")
            -1
        }

        val maxVolume = try {
            audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: -1
        } catch (e: Exception) {
            Logger.w(TAG, "Error querying max STREAM_ALARM volume: ${e.message}")
            -1
        }

        var appliedVolume = previousVolume
        if (audioManager != null && maxVolume > 0) {
            try {
                // Safely adjust volume up to system-permitted maximum
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
                appliedVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                Logger.i(TAG, "STREAM_ALARM elevated to $appliedVolume / $maxVolume for emergency playback.")
            } catch (se: SecurityException) {
                Logger.w(TAG, "System DND / Security policy restricted volume adjustment: ${se.message}")
            } catch (e: Exception) {
                Logger.w(TAG, "Failed setting STREAM_ALARM volume: ${e.message}")
            }
        }

        _isAlertInProgress.set(true)
        val startTime = System.currentTimeMillis()

        try {
            onPlaybackStarted?.invoke()
            Logger.i(TAG, "Starting loud alarm-class playback: \"$text\" (${audioData.durationMs}ms, ${audioData.sampleRate}Hz)")
            audioTrackPlayer.playAlarm(audioData.rawPcm, audioData.sampleRate)
            val durationMs = System.currentTimeMillis() - startTime
            Logger.i(TAG, "Alarm-class audio playback finished cleanly after ${durationMs}ms.")

            AlertPlaybackResult(
                success = true,
                focusGranted = focusGranted,
                appliedVolume = appliedVolume,
                maxPermittedVolume = maxVolume,
                durationMs = durationMs
            )
        } catch (t: Throwable) {
            val durationMs = System.currentTimeMillis() - startTime
            Logger.e(TAG, "Alarm-class audio playback error: ${t.message}", t)
            AlertPlaybackResult(
                success = false,
                focusGranted = focusGranted,
                appliedVolume = appliedVolume,
                maxPermittedVolume = maxVolume,
                durationMs = durationMs,
                errorMessage = t.message
            )
        } finally {
            // 3. Restore previous volume
            if (audioManager != null && previousVolume >= 0) {
                try {
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousVolume, 0)
                    Logger.d(TAG, "Restored STREAM_ALARM volume to $previousVolume")
                } catch (e: Exception) {
                    Logger.w(TAG, "Failed restoring previous STREAM_ALARM volume: ${e.message}")
                }
            }

            // 4. Abandon Audio Focus
            if (audioManager != null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                        audioManager.abandonAudioFocusRequest(focusRequest)
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.abandonAudioFocus(null)
                    }
                } catch (e: Exception) {
                    Logger.w(TAG, "Failed abandoning AudioFocus: ${e.message}")
                }
            }

            _isAlertInProgress.set(false)
        }
    }
}

/**
 * Mock implementation of [AlertManager] for testing and headless JVM runs.
 */
class MockAlertManager(
    private val audioTrackPlayer: AudioTrackPlayer? = null
) : AlertManager {

    private val alertLock = Mutex()
    private val _isAlertInProgress = AtomicBoolean(false)

    var lastPlayedText: String? = null
    var lastAudioData: AudioData? = null
    var mockFocusGranted: Boolean = true
    var mockAppliedVolume: Int = 15
    var mockMaxVolume: Int = 15
    var isEmergencyOverride: Boolean? = null

    override fun isAlert(message: TransportMessage): Boolean {
        return isEmergencyOverride ?: (
                message.messageType == TransportMessageType.ALERT || message.priority >= 1
                )
    }

    override fun isAlertInProgress(): Boolean = _isAlertInProgress.get()

    override suspend fun <T> withAlertLock(block: suspend () -> T): T {
        return alertLock.withLock {
            block()
        }
    }

    override suspend fun playEmergencyAlert(
        text: String,
        audioData: AudioData,
        onPlaybackStarted: (() -> Unit)?
    ): AlertPlaybackResult {
        _isAlertInProgress.set(true)
        return try {
            lastPlayedText = text
            lastAudioData = audioData
            onPlaybackStarted?.invoke()
            try {
                if (audioTrackPlayer != null && !audioData.isEmpty) {
                    audioTrackPlayer.playAlarm(audioData.rawPcm, audioData.sampleRate)
                }
            } catch (t: Throwable) {
                // Ignore Android framework stubs in JVM test environment
            }
            AlertPlaybackResult(
                success = true,
                focusGranted = mockFocusGranted,
                appliedVolume = mockAppliedVolume,
                maxPermittedVolume = mockMaxVolume,
                durationMs = audioData.durationMs
            )
        } finally {
            _isAlertInProgress.set(false)
        }
    }
}
