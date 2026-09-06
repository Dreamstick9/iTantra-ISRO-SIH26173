package com.example.itantra.tts

enum class TtsPlaybackState(val displayName: String) {
    IDLE("Idle"),
    INITIALIZING("Initializing Model"),
    SYNTHESIZING("Synthesizing Speech"),
    PLAYING("Playing Audio"),
    STOPPED("Playback Stopped"),
    ERROR("Error");

    val isBusy: Boolean get() = this == SYNTHESIZING || this == PLAYING || this == INITIALIZING
    val isPlaying: Boolean get() = this == PLAYING
}
