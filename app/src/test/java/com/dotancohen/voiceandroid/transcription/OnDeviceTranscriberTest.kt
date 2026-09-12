package com.dotancohen.voiceandroid.transcription

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which transcriptions are cleared away once a recording has really been
 * transcribed.
 *
 * A recording whose transcription was cut short keeps a row saying so. When
 * the recording is transcribed properly afterwards, that row is only noise:
 * the note then shows an error beside a perfectly good Hebrew transcription
 * and reads as though something failed. So it goes. Everything else stays,
 * including failures the user needs to see.
 */
class OnDeviceTranscriberTest {

    private val whisper = OnDeviceTranscriber.SERVICE_NAME

    @Test
    fun `the row left by an app that was closed is a placeholder`() {
        assertTrue(
            OnDeviceTranscriber.isInterruptedPlaceholder(
                whisper,
                "Error: the app was closed before the transcription finished",
                null,
            )
        )
    }

    @Test
    fun `a row still saying pending is a placeholder`() {
        assertTrue(
            OnDeviceTranscriber.isInterruptedPlaceholder(
                whisper,
                "Pending... (2026-09-08 02:00:00+03:00)",
                null,
            )
        )
    }

    @Test
    fun `a real Hebrew transcription is kept`() {
        assertFalse(
            OnDeviceTranscriber.isInterruptedPlaceholder(
                whisper,
                "שלום, זו הקלטה מהטלפון של יום שלישי",
                null,
            )
        )
    }

    @Test
    fun `a failure the user needs to see is kept`() {
        // No model, an unreadable file: the user has to be told why, so this
        // is not swept away with the placeholders.
        assertFalse(
            OnDeviceTranscriber.isInterruptedPlaceholder(
                whisper,
                "Error: המודל לא נטען",
                null,
            )
        )
    }

    @Test
    fun `a placeholder from another service is left alone`() {
        // Only this phone's own interrupted runs are ours to remove.
        assertFalse(
            OnDeviceTranscriber.isInterruptedPlaceholder(
                "assemblyai",
                "Error: the app was closed before the transcription finished",
                null,
            )
        )
    }

    @Test
    fun `a placeholder that is already deleted is not deleted again`() {
        assertFalse(
            OnDeviceTranscriber.isInterruptedPlaceholder(
                whisper,
                "Error: the app was closed before the transcription finished",
                1_757_000_000L,
            )
        )
    }
}
