package com.dotancohen.voiceandroid.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The recorder's clock.
 *
 * It counts up in hours, minutes and seconds from the moment recording
 * starts, and it must keep counting past an hour: a lecture or a meeting is
 * one recording, and a clock that rolled over at 59:59 would say a two-hour
 * recording was two minutes long.
 */
class AudioRecorderWidgetTest {

    @Test
    fun `a recording that has not started reads zero`() {
        assertEquals("00:00:00", formatElapsed(0))
    }

    @Test
    fun `seconds and minutes are both padded to two digits`() {
        assertEquals("00:00:07", formatElapsed(7))
        assertEquals("00:01:00", formatElapsed(60))
        assertEquals("00:09:05", formatElapsed(545))
    }

    @Test
    fun `the last second before an hour is still minutes`() {
        assertEquals("00:59:59", formatElapsed(3599))
    }

    @Test
    fun `an hour and beyond is counted in hours`() {
        assertEquals("01:00:00", formatElapsed(3600))
        assertEquals("02:13:20", formatElapsed(8000))
    }

    // Which note shows the recorder. There is one recorder, so this decides
    // where it appears and where the user is told it is busy.

    private val thisNote = "01a085ce8ee07f80b36bfb105cffc7d6"
    private val otherNote = "01a085ce9e447333a2e408b1fd5b08c5"

    @Test
    fun `asking for a recorder in a quiet app shows it here`() {
        val p = recorderPlacement(thisNote, recordingNoteId = null, recording = false, asked = true)
        assertTrue(p.show)
        assertFalse(p.busyElsewhere)
    }

    @Test
    fun `a recording made in this note is shown here even without asking`() {
        // The user started it here, walked away and came back: the recorder
        // is still theirs, and the screen finds it again as it was.
        val p = recorderPlacement(thisNote, recordingNoteId = thisNote, recording = true, asked = false)
        assertTrue(p.show)
        assertFalse(p.busyElsewhere)
    }

    @Test
    fun `a recording made in another note is not shown here`() {
        // Showing it would put that note's time and waveform on this screen,
        // and Save would file the recording under the other note.
        val p = recorderPlacement(thisNote, recordingNoteId = otherNote, recording = true, asked = false)
        assertFalse(p.show)
        assertTrue(p.busyElsewhere)
    }

    @Test
    fun `asking while another note is recording says so instead`() {
        val p = recorderPlacement(thisNote, recordingNoteId = otherNote, recording = true, asked = true)
        assertFalse(p.show)
        assertTrue(p.busyElsewhere)
    }

    @Test
    fun `a finished recording in another note does not block this one`() {
        // The recorder is idle: whatever note it last worked on is history.
        val p = recorderPlacement(thisNote, recordingNoteId = otherNote, recording = false, asked = true)
        assertTrue(p.show)
        assertFalse(p.busyElsewhere)
    }

    @Test
    fun `a note nobody asked about shows nothing`() {
        val p = recorderPlacement(thisNote, recordingNoteId = null, recording = false, asked = false)
        assertFalse(p.show)
        assertFalse(p.busyElsewhere)
    }

    @Test
    fun `a recording longer than a day keeps counting hours`() {
        // Nothing stops the recorder at midnight, so the hours must not wrap.
        assertEquals("25:00:00", formatElapsed(90_000))
    }
}
