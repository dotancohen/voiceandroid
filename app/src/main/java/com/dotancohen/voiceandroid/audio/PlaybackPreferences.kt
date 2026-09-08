package com.dotancohen.voiceandroid.audio

import android.content.Context

/**
 * Playback settings kept on the device (not synced). The speed is shared by
 * every player in the app, so a speed chosen in the list applies in the
 * note as well.
 */
class PlaybackPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("voice_settings", Context.MODE_PRIVATE)

    var speed: Float
        get() = prefs.getFloat(KEY_SPEED, 1f).coerceIn(MIN_SPEED, MAX_SPEED)
        set(value) = prefs.edit().putFloat(KEY_SPEED, value.coerceIn(MIN_SPEED, MAX_SPEED)).apply()

    companion object {
        private const val KEY_SPEED = "playback_speed"
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 3.0f
        /** Speeds offered as one-tap buttons. */
        val PRESETS = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
    }
}
