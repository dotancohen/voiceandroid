package com.dotancohen.voiceandroid.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.voicecore.Stamp

/**
 * Which transcriptions count as finished, and which are still being waited
 * for.
 *
 * The waiting ones are drawn with a clock over the transcribe mark, in the
 * notes list and in the note, so the two questions have to have one answer
 * each and the same answer everywhere.
 */
class TranscriptionStateTest {

    private fun transcription(content: String) = Transcription(
        id = "01a085ce8ee07f80b36bfb105cffc7d6",
        audioFileId = "01a085ce8ee07f80b36bfb105cffc7d7",
        content = content,
        service = "local_whisper",
        state = "original !verified !verbatim !cleaned !polished",
        deviceId = "01a085ce8ee07f80b36bfb105cffc7d8",
        createdAt = Stamp(at = 1_757_000_000, offset = 10800, zone = "Asia/Jerusalem"),
    )

    @Test
    fun `a transcription with text is finished and not pending`() {
        val t = transcription("שלום, זו ההקלטה מהטלפון")
        assertTrue(t.isFinished)
        assertFalse(t.isPending)
    }

    @Test
    fun `a row that is still being worked on is pending`() {
        val t = transcription("Pending... (2026-09-09 15:20:00+03:00)")
        assertTrue(t.isPending)
        assertFalse(t.isFinished)
    }

    @Test
    fun `a failure is neither finished nor pending`() {
        // Waiting will not turn it into a transcription, so it must not be
        // shown with a clock; the user needs to read what went wrong.
        val t = transcription("Error: the app was closed before the transcription finished")
        assertFalse(t.isFinished)
        assertFalse(t.isPending)
    }

    @Test
    fun `a Hebrew transcription that happens to mention an error is finished`() {
        // The rule looks at how the row starts, not at what the speaker said.
        val t = transcription("הייתה שגיאה במערכת אתמול")
        assertTrue(t.isFinished)
        assertFalse(t.isPending)
    }

    @Test
    fun `an empty transcription is finished, not pending`() {
        // A recording of silence transcribes to nothing; there is nothing
        // left to wait for.
        val t = transcription("")
        assertTrue(t.isFinished)
        assertFalse(t.isPending)
    }
}
