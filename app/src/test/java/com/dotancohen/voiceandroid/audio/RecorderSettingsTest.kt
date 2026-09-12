package com.dotancohen.voiceandroid.audio

import com.dotancohen.voiceandroid.testing.FakePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The recorder's settings: the format a recording is written in, what
 * happens when the telephone rings, and what the New and + buttons do.
 *
 * A wrong answer here is not cosmetic: the format decides the file's
 * extension and how the file is written, and the call behaviour decides
 * whether a recording keeps going while somebody is talking to the user.
 */
class RecorderSettingsTest {

    private fun settings(vararg values: Pair<String, Any>) =
        RecorderPreferences(FakePreferences(values.toMap()), null)

    // Defaults

    @Test
    fun `a phone that has never been to Settings records Opus and pauses for calls`() {
        val prefs = settings()
        assertEquals(RecorderPreferences.FORMAT_OPUS, prefs.recordingFormat)
        assertEquals(RecorderPreferences.CALL_PAUSE, prefs.duringCall)
        assertEquals(RecorderPreferences.ACTION_NOTE, prefs.defaultNewAction)
        assertFalse(prefs.startRecordingImmediately)
        assertNull("no microphone chosen means the system's own", prefs.selectedMicKey)
    }

    // Values that cannot be right

    @Test
    fun `a format nobody has heard of reads as Opus`() {
        assertEquals(RecorderPreferences.FORMAT_OPUS, settings("recording_format" to "flac").recordingFormat)
        val prefs = settings()
        prefs.recordingFormat = "flac"
        assertEquals("and writing one stores Opus", RecorderPreferences.FORMAT_OPUS, prefs.recordingFormat)
    }

    @Test
    fun `a call behaviour nobody has heard of pauses`() {
        // Pausing is the safe answer: the recording waits rather than
        // filling minutes with silence the user did not ask for.
        assertEquals(RecorderPreferences.CALL_PAUSE, settings("recording_during_call" to "ignore").duringCall)
        val prefs = settings()
        prefs.duringCall = "ignore"
        assertEquals(RecorderPreferences.CALL_PAUSE, prefs.duringCall)
    }

    // The three formats

    @Test
    fun `every format has an extension, a title and an explanation`() {
        for (format in RecorderPreferences.FORMATS) {
            assertTrue(format, RecorderPreferences.formatExtension(format).isNotBlank())
            assertTrue(format, RecorderPreferences.formatTitle(format).isNotBlank())
            assertTrue(format, RecorderPreferences.formatDescription(format).isNotBlank())
        }
    }

    @Test
    fun `the extension matches the container each format writes`() {
        assertEquals("ogg", RecorderPreferences.formatExtension(RecorderPreferences.FORMAT_OPUS))
        assertEquals("m4a", RecorderPreferences.formatExtension(RecorderPreferences.FORMAT_AAC))
        assertEquals("wav", RecorderPreferences.formatExtension(RecorderPreferences.FORMAT_WAV16))
    }

    @Test
    fun `an unknown format still gets a playable extension`() {
        // The file has to be called something; an empty extension would
        // produce a file named "01a08.".
        assertTrue(RecorderPreferences.formatExtension("nonsense").isNotBlank())
    }

    @Test
    fun `each format's extension is its own`() {
        val extensions = RecorderPreferences.FORMATS.map { RecorderPreferences.formatExtension(it) }
        assertEquals(
            "two formats writing the same extension cannot be told apart",
            extensions.size, extensions.toSet().size
        )
    }

    // What the buttons do

    @Test
    fun `the New button follows the setting`() {
        val prefs = settings()
        assertEquals(RecorderPreferences.ACTION_NOTE, prefs.defaultNewAction)
        prefs.defaultNewAction = RecorderPreferences.ACTION_RECORDING
        assertEquals(RecorderPreferences.ACTION_RECORDING, prefs.defaultNewAction)
    }

    @Test
    fun `the plus inside a note always adds a recording, whatever the New button does`() {
        val prefs = settings()
        prefs.defaultNewAction = RecorderPreferences.ACTION_NOTE
        assertEquals(RecorderPreferences.ATTACHMENT_RECORDING, prefs.defaultAttachmentKind)
        prefs.defaultNewAction = RecorderPreferences.ACTION_RECORDING
        assertEquals(RecorderPreferences.ATTACHMENT_RECORDING, prefs.defaultAttachmentKind)
    }

    @Test
    fun `every kind the plus offers has a title, and the default is one of them`() {
        for (kind in RecorderPreferences.ATTACHMENT_KINDS) {
            assertTrue(kind, RecorderPreferences.attachmentKindTitle(kind).isNotBlank())
        }
        assertTrue(settings().defaultAttachmentKind in RecorderPreferences.ATTACHMENT_KINDS)
    }

    // Behaviour during a call

    @Test
    fun `both call behaviours are explained to the user`() {
        for (behaviour in RecorderPreferences.CALL_BEHAVIOURS) {
            assertTrue(behaviour, RecorderPreferences.callBehaviourTitle(behaviour).isNotBlank())
        }
        assertEquals(2, RecorderPreferences.CALL_BEHAVIOURS.size)
    }

    @Test
    fun `keeping the silence and pausing are different answers`() {
        assertTrue(
            RecorderPreferences.callBehaviourTitle(RecorderPreferences.CALL_SILENCE) !=
                RecorderPreferences.callBehaviourTitle(RecorderPreferences.CALL_PAUSE)
        )
    }

    @Test
    fun `starting by itself is remembered`() {
        val prefs = settings()
        prefs.startRecordingImmediately = true
        assertTrue(prefs.startRecordingImmediately)
        prefs.startRecordingImmediately = false
        assertFalse(prefs.startRecordingImmediately)
    }

    @Test
    fun `a phone with no microphones reports none rather than throwing`() {
        assertEquals(emptyList<Any>(), settings().microphones())
        assertNull(settings().selectedMic())
    }
}
