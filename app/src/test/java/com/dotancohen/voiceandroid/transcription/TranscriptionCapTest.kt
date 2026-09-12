package com.dotancohen.voiceandroid.transcription

import com.dotancohen.voiceandroid.util.Magic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The longest recording this phone will transcribe.
 *
 * Whisper holds the whole recording in memory as it works, so a long one
 * either fails or takes the phone out of use for a quarter of an hour. Ten
 * minutes covers the voice notes the application is for; anything longer is
 * transcribed on the desktop once the two have synced.
 */
class TranscriptionCapTest {

    @Test
    fun `the cap is ten minutes`() {
        assertEquals(10 * 60, Magic.TRANSCRIBE_MAX_SECONDS_ON_PHONE)
    }

    @Test
    fun `the message names the length, the limit and where to do the work`() {
        val message = OnDeviceTranscriber.tooLongForThisPhone(22 * 60)
        assertTrue(message, message.contains("22:00"))
        assertTrue(message, message.contains("10 minutes"))
        assertTrue(message, message.contains("desktop"))
    }

    @Test
    fun `a nine-minute recording is inside the cap`() {
        // The user's own notes run to eight or nine minutes; those must be
        // transcribed on the phone without a word about it.
        assertTrue(9 * 60 <= Magic.TRANSCRIBE_MAX_SECONDS_ON_PHONE)
        assertTrue(10 * 60 <= Magic.TRANSCRIBE_MAX_SECONDS_ON_PHONE)
    }

    @Test
    fun `a recording past the cap is past it by a second`() {
        assertTrue(10 * 60 + 1 > Magic.TRANSCRIBE_MAX_SECONDS_ON_PHONE)
    }

    @Test
    fun `the length is read from the converted audio exactly`() {
        // 16 kHz, mono, 16-bit is 32 000 bytes a second; the header is not
        // audio. This is the arithmetic the job uses as its backstop when the
        // database did not know the length.
        val tenMinutes = 10L * 60 * 32_000
        val wavBytes = tenMinutes + com.dotancohen.voiceandroid.audio.WavRecorder.HEADER_SIZE
        val seconds = (wavBytes - com.dotancohen.voiceandroid.audio.WavRecorder.HEADER_SIZE) / 32_000
        assertEquals(600L, seconds)
        assertTrue(seconds <= Magic.TRANSCRIBE_MAX_SECONDS_ON_PHONE)
    }

    @Test
    fun `the cap is well inside what a phone can hold`() {
        // Whisper needs about 4 bytes per sample at 16 kHz: 64 kB a second.
        val bytesNeeded = Magic.TRANSCRIBE_MAX_SECONDS_ON_PHONE * 64L * 1024
        assertTrue(
            "ten minutes is ${bytesNeeded / 1024 / 1024} MB of audio in memory",
            bytesNeeded < 64L * 1024 * 1024
        )
    }
}
