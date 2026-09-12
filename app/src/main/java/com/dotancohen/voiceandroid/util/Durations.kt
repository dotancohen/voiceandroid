package com.dotancohen.voiceandroid.util

import java.util.Locale

/**
 * Clock readings for recordings: the length shown on a note in the list, and
 * the position shown while a recording plays.
 *
 * Both pin [Locale.US] for the digits. The device's own locale would render
 * Arabic-Indic digits under an Arabic locale, which turns "02:15" into
 * something the surrounding Latin interface does not line up with, and the
 * separator is a colon in every locale anyway.
 */
object Durations {

    /**
     * A recording's length, from a count of seconds: `m:ss`, or `h:mm:ss` once
     * it passes an hour. Used where the number sits inside a line of text, so
     * the minutes are not padded.
     */
    fun ofSeconds(seconds: Int): String {
        val safe = if (seconds < 0) 0 else seconds
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val secs = safe % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, secs)
        }
    }

    /**
     * A playback position, from a count of milliseconds: `mm:ss`, or `h:mm:ss`
     * once it passes an hour. Used on either side of a seek bar, where the
     * padded minutes stop the bar from shifting as the number grows.
     */
    fun ofMillis(millis: Long): String {
        if (millis <= 0) return "00:00"
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
