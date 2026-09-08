package com.itantra.voice.audio

import android.media.AudioAttributes
import com.itantra.voice.fixtures.SarvamMockFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AudioPlayerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeNativeMediaPlayer : NativeMediaPlayer {
        override var onCompletionListener: (() -> Unit)? = null
        override var onErrorListener: ((what: Int, extra: Int) -> Boolean)? = null

        var contentType: Int = -1
        var usage: Int = -1
        var dataSourcePath: String? = null
        var prepareCalled = false
        var startCalled = false
        var stopCalled = false
        var resetCalled = false
        var releaseCalled = false
        private var playing = false

        override fun setAudioAttributes(contentType: Int, usage: Int) {
            this.contentType = contentType
            this.usage = usage
        }

        override fun setDataSource(path: String) {
            this.dataSourcePath = path
        }

        override fun prepare() {
            prepareCalled = true
        }

        override fun start() {
            startCalled = true
            playing = true
        }

        override fun stop() {
            stopCalled = true
            playing = false
        }

        override fun reset() {
            resetCalled = true
            playing = false
        }

        override fun release() {
            releaseCalled = true
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

    private lateinit var cacheDir: File
    private lateinit var fakePlayer: FakeNativeMediaPlayer
    private lateinit var audioPlayer: AudioPlayer

    @Before
    fun setUp() {
        cacheDir = tempFolder.newFolder("audio_cache")
        fakePlayer = FakeNativeMediaPlayer()
        audioPlayer = AudioPlayer(cacheDir, playerFactory = { fakePlayer })
    }

    // Tier 1 Tests: Nominal Feature Coverage (F7 Native WAV Audio Player)

    @Test
    fun testAudioPlayerDecodesBase64WavToBytesAndPlays() {
        val validBase64 = SarvamMockFixtures.MOCK_BASE64_WAV
        audioPlayer.playBase64Wav(validBase64)

        assertTrue("prepare() must be called on player", fakePlayer.prepareCalled)
        assertTrue("start() must be called on player", fakePlayer.startCalled)
        assertTrue(audioPlayer.isPlaying)
        assertNotNull(fakePlayer.dataSourcePath)
        assertTrue("Temp cache file must exist", File(fakePlayer.dataSourcePath!!).exists())
    }

    @Test
    fun testAudioPlayerInitializesSpeechAudioAttributes() {
        val validBase64 = SarvamMockFixtures.MOCK_BASE64_WAV
        audioPlayer.playBase64Wav(validBase64, isEmergency = false)

        assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, fakePlayer.contentType)
        // USAGE_MEDIA: a walkie-talkie belongs on the media stream. The previous
        // USAGE_ASSISTANCE_ACCESSIBILITY is the screen-reader stream and is ducked
        // against other audio differently.
        assertEquals(AudioAttributes.USAGE_MEDIA, fakePlayer.usage)
    }

    @Test
    fun testAudioPlayerTriggersCompletionCallbackOnFinish() {
        val completed = AtomicBoolean(false)
        audioPlayer.playBase64Wav(
            SarvamMockFixtures.MOCK_BASE64_WAV,
            onComplete = { completed.set(true) }
        )

        assertTrue(audioPlayer.isPlaying)
        val tempFilePath = fakePlayer.dataSourcePath!!

        fakePlayer.triggerCompletion()

        assertTrue("onComplete callback must be invoked", completed.get())
        assertFalse(audioPlayer.isPlaying)
        assertTrue("MediaPlayer must be released", fakePlayer.releaseCalled)
        assertFalse("Temp file must be cleaned up on completion", File(tempFilePath).exists())
    }

    @Test
    fun testAudioPlayerTriggersErrorCallbackOnPlaybackFailure() {
        val errorReported = AtomicBoolean(false)
        audioPlayer.playBase64Wav(
            SarvamMockFixtures.MOCK_BASE64_WAV,
            onError = { errorReported.set(true) }
        )

        val tempFilePath = fakePlayer.dataSourcePath!!
        fakePlayer.triggerError(what = 1, extra = -1004)

        assertTrue("onError callback must be invoked", errorReported.get())
        assertFalse(audioPlayer.isPlaying)
        assertFalse("Temp file must be cleaned up on error", File(tempFilePath).exists())
    }

    @Test
    fun testAudioPlayerCleansUpCacheFileAndReleasesMediaPlayer() {
        audioPlayer.playBase64Wav(SarvamMockFixtures.MOCK_BASE64_WAV)
        val tempFilePath = fakePlayer.dataSourcePath!!
        assertTrue(File(tempFilePath).exists())

        audioPlayer.stopAndRelease()

        assertFalse(audioPlayer.isPlaying)
        assertTrue(fakePlayer.stopCalled)
        assertTrue(fakePlayer.releaseCalled)
        assertFalse("Temp file must be deleted on stopAndRelease", File(tempFilePath).exists())
    }

    // Tier 1 Tests: Feature Coverage for F18 (Emergency Audio Route)

    @Test
    fun testEmergencyAudioAttributesSetUsageAlarm() {
        audioPlayer.playBase64Wav(
            SarvamMockFixtures.MOCK_BASE64_WAV,
            isEmergency = true
        )

        assertEquals("Usage must be USAGE_ALARM for emergency broadcast", AudioAttributes.USAGE_ALARM, fakePlayer.usage)
        assertEquals("Content type must be CONTENT_TYPE_SPEECH", AudioAttributes.CONTENT_TYPE_SPEECH, fakePlayer.contentType)
    }

    // Tier 2 Tests: Boundary & Corner Cases (F7 Audio Player Boundaries)

    @Test
    fun testPlayerCorruptWavHeaderTriggersErrorCallback() {
        val errorCalled = AtomicBoolean(false)
        val corruptedBytes = ByteArray(50) { 0 } // No 'RIFF' header

        audioPlayer.playWavBytes(
            corruptedBytes,
            onError = { error ->
                errorCalled.set(true)
                assertTrue(error is IllegalArgumentException)
                assertTrue(error.message!!.contains("RIFF/WAVE"))
            }
        )

        assertTrue("Error callback must be triggered for corrupted header", errorCalled.get())
        assertFalse(fakePlayer.startCalled)
    }

    @Test
    fun testPlayerPayloadTooShortTriggersErrorCallback() {
        val errorCalled = AtomicBoolean(false)
        val shortBytes = ByteArray(20)

        audioPlayer.playWavBytes(
            shortBytes,
            onError = { error ->
                errorCalled.set(true)
                assertTrue(error is IllegalArgumentException)
                assertTrue(error.message!!.contains("too short"))
            }
        )

        assertTrue(errorCalled.get())
    }

    @Test
    fun testInvalidBase64StringHandledGracefully() {
        val errorCalled = AtomicBoolean(false)

        audioPlayer.playBase64Wav(
            "this_is_not_valid_base64_!@#$%",
            onError = { error ->
                errorCalled.set(true)
                assertTrue(error is IllegalArgumentException)
            }
        )

        assertTrue(errorCalled.get())
    }

    @Test
    fun testBlankBase64StringHandledGracefully() {
        val errorCalled = AtomicBoolean(false)

        audioPlayer.playBase64Wav(
            "   ",
            onError = { error ->
                errorCalled.set(true)
                assertTrue(error is IllegalArgumentException)
            }
        )

        assertTrue(errorCalled.get())
    }

    @Test
    fun testPlayerInterruptedByNewPlaybackStopsPreviousImmediately() {
        audioPlayer.playBase64Wav(SarvamMockFixtures.MOCK_BASE64_WAV)
        val firstFile = File(fakePlayer.dataSourcePath!!)
        assertTrue(firstFile.exists())

        val secondPlayer = FakeNativeMediaPlayer()
        val multiPlayer = AudioPlayer(cacheDir, playerFactory = { secondPlayer })
        multiPlayer.playBase64Wav(SarvamMockFixtures.MOCK_BASE64_WAV)

        // Starting new playback on same AudioPlayer replaces active player
        audioPlayer.playBase64Wav(SarvamMockFixtures.MOCK_BASE64_WAV)
        assertFalse("First temp file should be deleted when starting next playback", firstFile.exists())
    }

    @Test
    fun testConcurrentStartStopCallsSynchronized() {
        val latch = CountDownLatch(10)
        for (i in 0 until 10) {
            Thread {
                if (i % 2 == 0) {
                    audioPlayer.playBase64Wav(SarvamMockFixtures.MOCK_BASE64_WAV)
                } else {
                    audioPlayer.stopAndRelease()
                }
                latch.countDown()
            }.start()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        audioPlayer.stopAndRelease()
        assertFalse(audioPlayer.isPlaying)
    }
}
