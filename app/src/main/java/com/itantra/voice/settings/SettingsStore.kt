package com.itantra.voice.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * App-private settings that must survive a restart.
 *
 * The Sarvam key lives here as well as in `BuildConfig`, so a single distributed APK works
 * for an operator who has a key but no offline voice pack, without them having to rebuild.
 * The runtime value takes precedence when set; the build-time value remains the default so
 * a developer's `local.properties` still works untouched.
 *
 * Stored in app-private preferences. This is not a secret store — it is not encrypted and
 * offers no protection against an attacker with physical access to an unlocked, rooted
 * device. It is appropriate for a demo credential and nothing more.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Runtime-entered Sarvam key, or empty when none has been set. */
    var sarvamKey: String
        get() = prefs.getString(KEY_SARVAM, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_SARVAM, value.trim()).apply()
        }

    /** Runtime-entered ElevenLabs key, or empty when none has been set. */
    var elevenLabsKey: String
        get() = prefs.getString(KEY_ELEVENLABS, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_ELEVENLABS, value.trim()).apply()
        }

    /**
     * Optional ElevenLabs voice id. Blank means "use the first voice on the account",
     * which avoids hardcoding an id that may not exist for a given user.
     */
    var elevenLabsVoiceId: String
        get() = prefs.getString(KEY_ELEVENLABS_VOICE, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_ELEVENLABS_VOICE, value.trim()).apply()
        }

    /**
     * Pins the offline engine regardless of any configured key.
     *
     * Distinct from `BuildConfig.FORCE_OFFLINE`: that is a compile-time guarantee for a
     * submission build (the cloud client is never constructed), whereas this is an
     * operator-facing switch for demonstrating both modes from one APK.
     */
    var preferOffline: Boolean
        get() = prefs.getBoolean(KEY_PREFER_OFFLINE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PREFER_OFFLINE, value).apply()
        }

    fun clearSarvamKey() {
        prefs.edit().remove(KEY_SARVAM).apply()
    }

    private companion object {
        const val PREFS_NAME = "itantra_settings"
        const val KEY_SARVAM = "sarvam_api_key"
        const val KEY_ELEVENLABS = "elevenlabs_api_key"
        const val KEY_ELEVENLABS_VOICE = "elevenlabs_voice_id"
        const val KEY_PREFER_OFFLINE = "prefer_offline"
    }
}
