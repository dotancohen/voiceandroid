package com.dotancohen.voiceandroid.util

import com.dotancohen.voiceandroid.data.AudioFile
import com.dotancohen.voiceandroid.data.Note
import com.dotancohen.voiceandroid.data.Transcription
import com.dotancohen.voiceandroid.data.TranscriptionFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.voicecore.Stamp

/**
 * What a Note offers to share, and what is actually sent.
 *
 * The rule that matters: a Note with one thing in it is sent without asking,
 * because there is nothing to decide. Everything else is a tree, so it is
 * clear which Transcription belongs to which recording.
 */
class NoteSharingTest {

    private fun stamp() = Stamp(at = 1_757_419_500, offset = 10800, zone = "Asia/Jerusalem")

    private fun note(content: String) = Note(id = "n1", content = content, createdAt = stamp())

    private fun audioFile(id: String) =
        AudioFile(id = id, importedAt = stamp(), filename = "$id.opus", deviceId = "phone")

    private fun transcription(id: String, audioFileId: String, content: String, service: String = "local_whisper") =
        Transcription(
            id = id,
            audioFileId = audioFileId,
            content = content,
            service = service,
            state = TranscriptionFlags.DEFAULT_FLAGS,
            deviceId = "phone",
            createdAt = stamp(),
        )

    @Test
    fun `a Note with text alone offers exactly one thing`() {
        val items = NoteSharing.itemsFor(note("פגישה עם הצוות"), emptyList(), emptyMap())
        assertEquals(1, items.size)
        assertEquals(NoteSharing.NOTE_CONTENT_ID, items.first().id)
        assertEquals("פגישה עם הצוות", items.first().detail)
    }

    @Test
    fun `a Note with one recording and no text offers exactly one thing`() {
        val items = NoteSharing.itemsFor(note(""), listOf(audioFile("a1")), emptyMap())
        assertEquals(1, items.size)
        assertEquals("a1", items.first().id)
    }

    @Test
    fun `a Note that holds nothing offers nothing`() {
        assertEquals(emptyList<Any>(), NoteSharing.itemsFor(note("   "), emptyList(), emptyMap()))
        assertEquals(emptyList<Any>(), NoteSharing.itemsFor(null, emptyList(), emptyMap()))
    }

    @Test
    fun `a Transcription is listed under the recording it belongs to`() {
        val items = NoteSharing.itemsFor(
            note("פגישה"),
            listOf(audioFile("a1"), audioFile("a2")),
            mapOf(
                "a1" to listOf(transcription("t1", "a1", "שלום")),
                "a2" to listOf(transcription("t2", "a2", "להתראות")),
            )
        )
        assertEquals(
            listOf(NoteSharing.NOTE_CONTENT_ID, "a1", "t1", "a2", "t2"),
            items.map { it.id }
        )
        assertEquals(listOf(0, 0, 1, 0, 1), items.map { it.depth })
    }

    @Test
    fun `a Transcription still being worked on is not offered`() {
        val items = NoteSharing.itemsFor(
            note(""),
            listOf(audioFile("a1")),
            mapOf("a1" to listOf(
                transcription("t1", "a1", "Pending... (2026-09-10 10:00:00+03:00)"),
                transcription("t2", "a1", "Error: the app was closed before the transcription finished"),
            ))
        )
        assertEquals(listOf("a1"), items.map { it.id })
    }

    @Test
    fun `a recording that is not on this phone is not offered`() {
        val items = NoteSharing.itemsFor(
            note(""),
            listOf(audioFile("here"), audioFile("elsewhere")),
            mapOf("elsewhere" to listOf(transcription("t1", "elsewhere", "שלום"))),
            isLocal = { it == "here" }
        )
        // The recording is not offered, but its Transcription still is: the
        // words are here even when the audio is on another device.
        assertEquals(listOf("here", "t1"), items.map { it.id })
    }

    @Test
    fun `the text sent is the pieces chosen, in the order they are shown`() {
        val audioFiles = listOf(audioFile("a1"), audioFile("a2"))
        val transcriptions = mapOf(
            "a1" to listOf(transcription("t1", "a1", "ראשון")),
            "a2" to listOf(transcription("t2", "a2", "שני")),
        )
        val text = NoteSharing.textFor(
            setOf(NoteSharing.NOTE_CONTENT_ID, "t2", "t1"),
            note("כותרת"),
            audioFiles,
            transcriptions
        )
        assertEquals("כותרת\n\nראשון\n\nשני", text)
    }

    @Test
    fun `nothing chosen sends nothing`() {
        assertEquals("", NoteSharing.textFor(emptySet(), note("כותרת"), emptyList(), emptyMap()))
    }

    @Test
    fun `only the chosen recordings are attached`() {
        val audioFiles = listOf(audioFile("a1"), audioFile("a2"), audioFile("a3"))
        val chosen = NoteSharing.audioFilesFor(setOf("a1", "a3"), audioFiles)
        assertEquals(listOf("a1", "a3"), chosen.map { it.id })
    }

    @Test
    fun `sharing a Transcription does not drag its recording along`() {
        val audioFiles = listOf(audioFile("a1"))
        val transcriptions = mapOf("a1" to listOf(transcription("t1", "a1", "שלום")))
        val chosen = setOf("t1")
        assertEquals("שלום", NoteSharing.textFor(chosen, note(""), audioFiles, transcriptions))
        assertTrue(NoteSharing.audioFilesFor(chosen, audioFiles).isEmpty())
    }

    @Test
    fun `the detail line is the first line that says something`() {
        val items = NoteSharing.itemsFor(note("\n\n  \nפגישה עם הצוות\nשורה שנייה"), emptyList(), emptyMap())
        assertEquals("פגישה עם הצוות", items.first().detail)
    }
}
