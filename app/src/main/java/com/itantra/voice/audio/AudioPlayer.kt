package com.itantra.voice.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import java.io.File
import java.util.Base64

/**
 * Abstraction layer for Android MediaPlayer to enable real device playback and
 * deterministic JVM unit testing.
 */
interface NativeMediaPlayer {
    var onCompletionListener: (() -> Unit)?
    var onErrorListener: ((what: Int, extra: Int) -> Boolean)?
    fun setAudioAttributes(contentType: Int, usage: Int)
    fun setDataSource(path: String)
    fun prepare()
    fun start()
    fun stop()
    fun reset()
    fun release()
    val isPlaying: Boolean
}

/**
 * Production implementation of NativeMediaPlayer wrapping android.media.MediaPlayer.
 */
class DefaultNativeMediaPlayer : NativeMediaPlayer {
    private val player = MediaPlayer()

    override var onCompletionListener: (() -> Unit)? = null
        set(value) {
            field = value
            player.setOnCompletionListener { value?.invoke() }
        }

    override var onErrorListener: ((what: Int, extra: Int) -> Boolean)? = null
        set(value) {
            field = value
            player.setOnErrorListener { _, what, extra ->
                value?.invoke(what, extra) ?: false
            }
        }

    override fun setAudioAttributes(contentType: Int, usage: Int) {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(contentType)
                .setUsage(usage)
                .build()
        )
    }

    override fun setDataSource(path: String) = player.setDataSource(path)
    override fun prepare() = player.prepare()
    override fun start() = player.start()
    override fun stop() = player.stop()
    override fun reset() = player.reset()
    override fun release() = player.release()
    override val isPlaying: Boolean
        get() = try { player.isPlaying } catch (e: Exception) { false }
}

/**
 * Raises the alarm stream to maximum for emergency announcements.
 *
 * Abstracted so the player stays constructible (and testable) without an Android
 * Context; the production implementation is [SystemAlarmVolumeController].
 */
interface AlarmVolumeController {
    fun raiseToMax()
}

/** No-op controller used when the player was built without a Context. */
object NoOpAlarmVolumeController : AlarmVolumeController {
    override fun raiseToMax() = Unit
}

/**
 * Sets STREAM_ALARM to its maximum so an emergency announcement is audible even when
 * the handset's media volume is turned down, per the SIH26173 alert requirement.
 */
class SystemAlarmVolumeController(private val context: Context) : AlarmVolumeController {
    override fun raiseToMax() {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(
                AudioManager.STREAM_ALARM,
                am.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )
        }
    }
}

/**
 * Native audio player for synthesised WAV speech.
 * Streams playback via [MediaPlayer] without pops or resource leaks, writing to an
 * app-private temporary cache file and atomically deleting it upon playback completion.
 */
class AudioPlayer(
    private val cacheDir: File,
    private val playerFactory: () -> NativeMediaPlayer = { DefaultNativeMediaPlayer() },
    private val alarmVolumeController: AlarmVolumeController = NoOpAlarmVolumeController
) {

    constructor(context: Context) : this(
        cacheDir = context.cacheDir,
        alarmVolumeController = SystemAlarmVolumeController(context)
    )

    private val playerLock = Any()
    private var activePlayer: NativeMediaPlayer? = null
    private var currentTempFile: File? = null

    val isPlaying: Boolean
        get() = synchronized(playerLock) {
            activePlayer?.isPlaying == true
        }

    /**
     * Decodes Base64 WAV string and plays it through device audio routing.
     *
     * @param base64Wav Base64-encoded WAV audio string
     * @param isEmergency Whether to use USAGE_ALARM for priority broadcast
     * @param onComplete Callback invoked when playback finishes normally
     * @param onError Callback invoked when decoding or playback encounters an error
     */
    fun playBase64Wav(
        base64Wav: String,
        isEmergency: Boolean = false,
        onComplete: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        val wavBytes = try {
            val clean = base64Wav.trim().replace("\n", "").replace("\r", "").replace(" ", "")
            if (clean.isBlank()) {
                throw IllegalArgumentException("Base64 audio string is blank")
            }
            Base64.getDecoder().decode(clean)
        } catch (e: Exception) {
            onError(IllegalArgumentException("Failed to decode Base64 WAV audio: ${e.message}", e))
            return
        }

        playWavBytes(wavBytes, isEmergency, onComplete, onError)
    }

    /**
     * Plays raw WAV byte array through device audio routing.
     */
    fun playWavBytes(
        wavBytes: ByteArray,
        isEmergency: Boolean = false,
        onComplete: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        synchronized(playerLock) {
            stopAndRelease()

            if (wavBytes.size < 44) {
                onError(IllegalArgumentException("Audio payload too short to be valid WAV: ${wavBytes.size} bytes"))
                return
            }

            val riff = String(wavBytes.copyOfRange(0, 4), Charsets.US_ASCII)
            val wave = String(wavBytes.copyOfRange(8, 12), Charsets.US_ASCII)
            if (riff != "RIFF" || wave != "WAVE") {
                onError(IllegalArgumentException("Audio data does not contain valid RIFF/WAVE header"))
                return
            }

            try {
                val tempFile = File.createTempFile("tts_playback_", ".wav", cacheDir)
                tempFile.deleteOnExit()
                tempFile.writeBytes(wavBytes)
                currentTempFile = tempFile

                val player = playerFactory()
                // SIH26173: alert traffic must be announced at the highest volume and
                // must not be silenced by the media stream being turned down, so it is
                // routed to USAGE_ALARM. Ordinary speech uses the media stream.
                val usage = if (isEmergency) {
                    AudioAttributes.USAGE_ALARM
                } else {
                    AudioAttributes.USAGE_MEDIA
                }

                if (isEmergency) alarmVolumeController.raiseToMax()

                player.setAudioAttributes(AudioAttributes.CONTENT_TYPE_SPEECH, usage)
                player.setDataSource(tempFile.absolutePath)

                player.onCompletionListener = {
                    synchronized(playerLock) {
                        stopAndRelease()
                    }
                    onComplete()
                }

                player.onErrorListener = { what, extra ->
                    synchronized(playerLock) {
                        stopAndRelease()
                    }
                    onError(IllegalStateException("MediaPlayer error ($what, $extra)"))
                    true
                }

                player.prepare()
                // Publish before start(): playback of a very short clip can complete
                // synchronously, and the completion listener nulls activePlayer. Assigning
                // after start() would resurrect an already-released player.
                activePlayer = player
                player.start()
            } catch (e: Exception) {
                stopAndRelease()
                onError(IllegalStateException("Failed to initiate audio playback: ${e.message}", e))
            }
        }
    }

    /**
     * Immediately stops active playback and atomically cleans up all resources and temp files.
     */
    fun stopAndRelease() {
        synchronized(playerLock) {
            try {
                activePlayer?.apply {
                    if (isPlaying) stop()
                    reset()
                    release()
                }
            } catch (ignored: Exception) {
            } finally {
                activePlayer = null
            }

            try {
                currentTempFile?.let {
                    if (it.exists()) it.delete()
                }
            } catch (ignored: Exception) {
            } finally {
                currentTempFile = null
            }
        }
    }

    /**
     * Releases player resources.
     */
    fun release() {
        stopAndRelease()
    }
}
