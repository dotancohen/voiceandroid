package com.dotancohen.voiceandroid.audio

import com.dotancohen.voiceandroid.util.Magic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which recordings are drawn only when the user asks.
 *
 * The user records meetings of two and a half hours and a subject sleeping
 * for eight. All of those must be kept, played and synced; only the work of
 * decoding one to draw a picture of it is worth asking about first.
 */
class LargeRecordingTest {

    private fun minutes(count: Long) = count * 60

    /** Opus at 128 kb/s, which is what the recorder writes by default. */
    private fun opusBytes(minutes: Long) = minutes * 60 * 16_000

    @Test
    fun `a voice note is drawn without asking`() {
        assertFalse(LargeRecording.isLarge(minutes(2), opusBytes(2)))
        assertFalse(LargeRecording.isLarge(30, 480_000))
    }

    @Test
    fun `a meeting of an hour is where the asking begins`() {
        assertFalse("59 minutes is still drawn at once", LargeRecording.isLarge(minutes(59), opusBytes(59)))
        assertTrue(LargeRecording.isLarge(minutes(60), opusBytes(60)))
        assertTrue(LargeRecording.isLarge(minutes(150), opusBytes(150)))
    }

    @Test
    fun `eight hours of somebody sleeping is asked about, not drawn`() {
        val eightHours = minutes(8 * 60)
        assertTrue(LargeRecording.isLarge(eightHours, opusBytes(8 * 60)))
    }

    @Test
    fun `a large file is asked about even when its length is not known yet`() {
        // The duration arrives when the player has prepared the recording; the
        // size is known immediately, and a header can lie.
        assertTrue(LargeRecording.isLarge(null, Magic.LARGE_RECORDING_BYTES))
        assertTrue(LargeRecording.isLarge(0, Magic.LARGE_RECORDING_BYTES + 1))
        assertFalse(LargeRecording.isLarge(null, Magic.LARGE_RECORDING_BYTES - 1))
    }

    @Test
    fun `a short recording in a large file is still asked about`() {
        // 16 kHz WAV is 115 MB an hour: a 55-minute recording is past the size
        // limit while still inside the time one.
        assertTrue(LargeRecording.isLarge(minutes(55), 110L * 1024 * 1024))
    }

    @Test
    fun `a long recording in a small file is asked about too`() {
        // A very quiet eight-hour recording compresses well.
        assertTrue(LargeRecording.isLarge(minutes(8 * 60), 20L * 1024 * 1024))
    }

    @Test
    fun `a duration in milliseconds is understood as well`() {
        assertTrue(LargeRecording.isLargeByMillis(minutes(70) * 1000, 1_000))
        assertFalse(LargeRecording.isLargeByMillis(minutes(10) * 1000, 1_000))
        assertFalse("a player that has not prepared yet says zero", LargeRecording.isLargeByMillis(0, 1_000))
        assertFalse(LargeRecording.isLargeByMillis(null, 1_000))
    }

    @Test
    fun `the limits are the ones the manual states`() {
        assertEquals(60 * 60, Magic.LONG_RECORDING_SECONDS)
        assertEquals(100L * 1024 * 1024, Magic.LARGE_RECORDING_BYTES)
    }

    @Test
    fun `the prompt says what it does and why it is not done already`() {
        assertEquals(
            "Click to generate waveform\nResource intensive operation on large file",
            LargeRecording.GENERATE_WAVEFORM_PROMPT
        )
    }
}
