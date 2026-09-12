package com.dotancohen.voiceandroid.audio

import com.dotancohen.voiceandroid.util.Magic

/**
 * Whether a recording is large enough that work on it should be asked for
 * rather than assumed.
 *
 * Drawing a waveform means decoding the whole recording, which costs time in
 * proportion to its length and holds one of the phone's few hardware decoders
 * while it runs. For a voice note that is imperceptible; for a recording of a
 * meeting it is a wait; for eight hours of somebody sleeping it is minutes of
 * work for a picture in which each bar is three minutes of audio.
 *
 * So: a long recording is played, kept and synced like any other, and its
 * waveform is drawn when the user asks for it. The answer is cached
 * afterwards (see [WaveformCache]), so the asking happens once.
 */
object LargeRecording {

    /**
     * What the user is offered in place of the waveform of a large recording.
     * Two lines: what pressing it does, and why it is not done already.
     */
    const val GENERATE_WAVEFORM_PROMPT =
        "Click to generate waveform\nResource intensive operation on large file"

    /**
     * Whether this recording counts as large.
     *
     * [durationSeconds] is what the player or the database says, where that is
     * known yet; [fileBytes] is always known. Either being past its limit is
     * enough: the duration is the better measure, and the size catches a
     * recording whose duration has not arrived or whose header is wrong.
     */
    fun isLarge(durationSeconds: Long?, fileBytes: Long): Boolean {
        val tooLong = (durationSeconds ?: 0L) >= Magic.LONG_RECORDING_SECONDS
        val tooBig = fileBytes >= Magic.LARGE_RECORDING_BYTES
        return tooLong || tooBig
    }

    /** The same, from a duration in milliseconds, as a player reports it. */
    fun isLargeByMillis(durationMillis: Long?, fileBytes: Long): Boolean =
        isLarge(durationMillis?.takeIf { it > 0 }?.div(1000), fileBytes)
}
