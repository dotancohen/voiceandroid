package com.dotancohen.voiceandroid.viewmodel

import com.dotancohen.voiceandroid.data.AudioFile
import com.dotancohen.voiceandroid.data.Note
import com.dotancohen.voiceandroid.data.Transcription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.voicecore.Stamp

/**
 * Three decisions the note screen makes: whether leaving a note deletes it,
 * which transcription is shown first, and which recording a note opens on.
 *
 * The first is the dangerous one. It deletes a note the user has just made,
 * so a mistake here loses a recording that was never anywhere else.
 */
class NoteDetailDecisionsTest {

    private fun stamp() = Stamp(at = 1_757_419_500, offset = 10800, zone = "Asia/Jerusalem")

    private fun note(content: String) =
        Note(id = "n1", content = content, createdAt = stamp())

    private fun audioFile(id: String) =
        AudioFile(id = id, importedAt = stamp(), filename = "$id.opus", deviceId = "phone")

    private fun transcription(id: String, content: String = "שלום") = Transcription(
        id = id,
        audioFileId = "a1",
        content = content,
        service = "whisper",
        state = "original",
        deviceId = "phone",
        createdAt = stamp(),
    )

    @Test
    fun `an empty note opened only to record is discarded`() {
        assertTrue(shouldDiscardEmptyNote(true, false, note(""), emptyList()))
        assertTrue("whitespace is not content", shouldDiscardEmptyNote(true, false, note("   \n "), emptyList()))
    }

    @Test
    fun `a note that has a recording in it is kept`() {
        assertFalse(shouldDiscardEmptyNote(true, false, note(""), listOf(audioFile("a1"))))
    }

    @Test
    fun `a note the user typed into is kept`() {
        assertFalse(shouldDiscardEmptyNote(true, false, note("פגישה מחר"), emptyList()))
    }

    @Test
    fun `a note being recorded into is kept, even while the user is elsewhere`() {
        // The recording carries on in the background; deleting its note would
        // throw away audio that is still being written.
        assertFalse(shouldDiscardEmptyNote(true, true, note(""), emptyList()))
    }

    @Test
    fun `a note that is still loading is never deleted`() {
        // Until the note arrives its content reads as empty, which must not
        // be mistaken for a note that was never written.
        assertFalse(shouldDiscardEmptyNote(true, false, null, emptyList()))
    }

    @Test
    fun `a note opened from the list is never deleted by leaving it`() {
        // Only a note opened in order to record is a candidate; an old empty
        // note the user opened out of curiosity stays.
        assertFalse(shouldDiscardEmptyNote(false, false, note(""), emptyList()))
        assertFalse(shouldDiscardEmptyNote(false, true, note(""), emptyList()))
        assertFalse(shouldDiscardEmptyNote(false, false, note(""), listOf(audioFile("a1"))))
    }

    @Test
    fun `the chosen transcription is shown first`() {
        val rows = listOf(transcription("t1"), transcription("t2"), transcription("t3"))
        assertEquals(
            listOf("t2", "t1", "t3"),
            transcriptionsInDisplayOrder(rows, "t2").map { it.id }
        )
    }

    @Test
    fun `with no choice made, the transcriptions keep the order they arrived in`() {
        val rows = listOf(transcription("t1"), transcription("t2"), transcription("t3"))
        assertEquals(listOf("t1", "t2", "t3"), transcriptionsInDisplayOrder(rows, null).map { it.id })
        assertEquals(
            "a choice that is not among them changes nothing",
            listOf("t1", "t2", "t3"),
            transcriptionsInDisplayOrder(rows, "gone").map { it.id }
        )
    }

    @Test
    fun `the transcriptions after the chosen one keep their own order`() {
        val rows = (1..6).map { transcription("t$it") }
        assertEquals(
            listOf("t5", "t1", "t2", "t3", "t4", "t6"),
            transcriptionsInDisplayOrder(rows, "t5").map { it.id }
        )
    }

    @Test
    fun `ordering a single transcription, or none, is not a special case`() {
        assertEquals(emptyList<Transcription>(), transcriptionsInDisplayOrder(emptyList(), "t1"))
        assertEquals(listOf("t1"), transcriptionsInDisplayOrder(listOf(transcription("t1")), "t1").map { it.id })
    }

    @Test
    fun `a note opens on the recording the user marked`() {
        val files = listOf(audioFile("a1"), audioFile("a2"), audioFile("a3"))
        assertEquals(1, indexOfPrimaryAudioFile(files, "a2"))
        assertEquals(2, indexOfPrimaryAudioFile(files, "a3"))
    }

    @Test
    fun `a note with no marked recording opens on the oldest`() {
        val files = listOf(audioFile("a1"), audioFile("a2"))
        assertEquals(0, indexOfPrimaryAudioFile(files, null))
        assertEquals("a recording that is not on this note", 0, indexOfPrimaryAudioFile(files, "elsewhere"))
        assertEquals(0, indexOfPrimaryAudioFile(emptyList(), "a1"))
    }
}

/**
 * Which recording a note stands on.
 *
 * The row in the notes list shows one recording's transcription, and opening
 * the note plays one recording. Both are the one the user starred, and the
 * oldest when nobody has starred one — but the star itself is only drawn as
 * set when it really was set, or there would be nothing left to press.
 */
class PrimaryAudioFileTest {

    private fun stamp() = Stamp(at = 1_757_419_500, offset = 10800, zone = "Asia/Jerusalem")

    private fun audioFile(id: String) =
        AudioFile(id = id, importedAt = stamp(), filename = "$id.opus", deviceId = "phone")

    private val files = listOf(audioFile("a1"), audioFile("a2"), audioFile("a3"))

    /** The same choice the notes list makes when it builds a row. */
    private fun chosen(primaryId: String?) = files.firstOrNull { it.id == primaryId }
    private fun lead(primaryId: String?) = chosen(primaryId) ?: files.firstOrNull()

    @Test
    fun `a starred recording is the one the note stands on`() {
        assertEquals("a2", lead("a2")?.id)
        assertEquals("a2", chosen("a2")?.id)
    }

    @Test
    fun `with nothing starred, the note stands on its oldest recording`() {
        assertEquals("a1", lead(null)?.id)
        assertNull("but no star is drawn as set", chosen(null))
    }

    @Test
    fun `a star pointing at a recording that is no longer on the note is ignored`() {
        // The recording was deleted on another phone; the mark outlived it.
        assertEquals("a1", lead("gone")?.id)
        assertNull(chosen("gone"))
    }

    @Test
    fun `a note with no recordings stands on nothing`() {
        val empty = emptyList<AudioFile>()
        assertNull(empty.firstOrNull { it.id == "a1" } ?: empty.firstOrNull())
    }

    @Test
    fun `pressing the star of the recording that has it takes the mark off`() {
        // The view models write null in that case, which is the note going
        // back to "whichever is oldest" — a choice of its own.
        val current = "a2"
        val pressed = "a2"
        val target = if (current == pressed) null else pressed
        assertNull(target)
    }

    @Test
    fun `pressing the star of another recording moves the mark`() {
        val current = "a2"
        val pressed = "a3"
        val target = if (current == pressed) null else pressed
        assertEquals("a3", target)
    }
}

/**
 * The order a note's recordings are shown and played in.
 *
 * Oldest first: that is the order they were made in, the order they are
 * numbered in the list, and — with no star on any of them — the one that
 * stands for the note is therefore the earliest.
 */
class AudioFileOrderTest {

    private fun stamp(at: Long) = Stamp(at = at, offset = 10800, zone = "Asia/Jerusalem")

    private fun audioFile(id: String, made: Long, imported: Long = 0L) = AudioFile(
        id = id,
        importedAt = stamp(if (imported == 0L) made else imported),
        filename = "$id.opus",
        fileCreatedAt = if (imported == 0L) null else stamp(made),
        deviceId = "phone",
    )

    @Test
    fun `recordings are shown oldest first`() {
        val later = audioFile("b", 2_000)
        val earlier = audioFile("a", 1_000)
        assertEquals(listOf("a", "b"), audioFilesOldestFirst(listOf(later, earlier)).map { it.id })
    }

    @Test
    fun `the earliest is the one the note stands on when none is starred`() {
        val files = audioFilesOldestFirst(
            listOf(audioFile("c", 3_000), audioFile("a", 1_000), audioFile("b", 2_000))
        )
        assertEquals("a", files.first().id)
        assertEquals(0, indexOfPrimaryAudioFile(files, null))
    }

    @Test
    fun `a file imported from elsewhere keeps the date the audio was made`() {
        // Recorded last year, copied onto the phone today: it belongs before
        // a recording made on the phone this morning.
        val imported = audioFile("old", made = 1_000, imported = 9_000)
        val recorded = audioFile("new", made = 5_000)
        assertEquals(listOf("old", "new"), audioFilesOldestFirst(listOf(recorded, imported)).map { it.id })
    }

    @Test
    fun `two recordings made in the same second keep a settled order`() {
        val a = audioFile("a", 1_000)
        val b = audioFile("b", 1_000)
        assertEquals(
            audioFilesOldestFirst(listOf(a, b)).map { it.id },
            audioFilesOldestFirst(listOf(b, a)).map { it.id }
        )
    }

    @Test
    fun `a note with one recording, or none, is not a special case`() {
        assertEquals(emptyList<String>(), audioFilesOldestFirst(emptyList()).map { it.id })
        assertEquals(listOf("a"), audioFilesOldestFirst(listOf(audioFile("a", 1_000))).map { it.id })
    }
}
