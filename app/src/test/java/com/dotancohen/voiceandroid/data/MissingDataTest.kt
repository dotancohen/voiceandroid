package com.dotancohen.voiceandroid.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.voicecore.Stamp
import java.io.File
import java.util.Calendar
import java.util.TimeZone

/**
 * Calculating data that was never calculated.
 *
 * A recording imported before lengths were recorded has none; a note written
 * before the display caches existed has none. Neither is lost data — it can be
 * read off the file or recomputed. What this must never do is guess: a
 * recording's timezone cannot be derived from the file, and writing this
 * phone's offset would state something false about where the user was.
 *
 * The counts and the wording must match the desktop's `src/core/missing_data.py`,
 * so a report from either application reads the same.
 */
class MissingDataTest {

    /** A store that remembers what it was asked to write, and reads no real files. */
    private class FakeStore(
        var recordings: MutableList<AudioFile> = mutableListOf(),
        var notes: MutableList<Note> = mutableListOf(),
        /** Ids of recordings whose file is not on this phone. */
        val elsewhere: MutableSet<String> = mutableSetOf(),
        /** What the file says, by recording id; absent means unreadable. */
        val lengths: MutableMap<String, Long> = mutableMapOf(),
        val modified: MutableMap<String, Long> = mutableMapOf(),
    ) : MissingData.Store {
        val savedLengths = mutableMapOf<String, Long>()
        val savedDates = mutableMapOf<String, Long>()
        val rebuilt = mutableListOf<String>()
        var rebuildFails = false

        /** Which recording a File stands for, since no file is really opened. */
        private val byFile = mutableMapOf<String, String>()

        override suspend fun recordings(): List<AudioFile> = recordings
        override suspend fun notes(): List<Note> = notes

        override suspend fun fileFor(recording: AudioFile): File? {
            if (recording.id in elsewhere) return null
            val file = File("/not/really/${recording.id}.opus")
            byFile[file.path] = recording.id
            return file
        }

        override fun lengthSeconds(file: File): Long? = lengths[byFile[file.path]]

        override fun madeAt(file: File, recordedName: String): Long? =
            MissingData.madeAtFrom(modified[byFile[file.path]], recordedName)

        override suspend fun saveLength(recordingId: String, seconds: Long): Boolean {
            savedLengths[recordingId] = seconds
            recordings = recordings.map {
                if (it.id == recordingId) it.copy(durationSeconds = seconds) else it
            }.toMutableList()
            return true
        }

        override suspend fun saveMadeAt(recordingId: String, at: Long): Boolean {
            savedDates[recordingId] = at
            recordings = recordings.map {
                if (it.id == recordingId) it.copy(fileCreatedAt = Stamp(at, null, null)) else it
            }.toMutableList()
            return true
        }

        override suspend fun rebuildCaches(noteId: String): Boolean {
            if (rebuildFails) return false
            rebuilt.add(noteId)
            notes = notes.map {
                if (it.id == noteId) it.copy(listDisplayCache = """{"content_preview":"x"}""") else it
            }.toMutableList()
            return true
        }
    }

    private fun recording(
        id: String,
        filename: String = "הקלטה.opus",
        duration: Long? = null,
        madeAt: Long? = null,
        importedOffset: Int? = 10800,
        deletedAt: Stamp? = null,
    ) = AudioFile(
        id = id,
        importedAt = Stamp(at = 1_757_419_500, offset = importedOffset, zone = "Asia/Jerusalem"),
        filename = filename,
        fileCreatedAt = madeAt?.let { Stamp(it, null, null) },
        durationSeconds = duration,
        deviceId = "phone",
        deletedAt = deletedAt,
    )

    private fun note(id: String, cache: String? = """{"content_preview":"פגישה"}""", deletedAt: Stamp? = null) =
        Note(
            id = id,
            content = "פגישה עם הצוות",
            createdAt = Stamp(at = 1_757_419_500, offset = 10800, zone = "Asia/Jerusalem"),
            deletedAt = deletedAt,
            listDisplayCache = cache,
        )

    // ---------------------------------------------------------------- survey

    @Test
    fun `a complete database is nothing missing`() = runBlocking {
        val store = FakeStore(
            recordings = mutableListOf(recording("a1", duration = 90, madeAt = 1_757_000_000)),
            notes = mutableListOf(note("n1")),
        )
        val survey = MissingData.survey(store)
        assertEquals(0, survey.totalCalculable)
        assertFalse(survey.anythingMissing)
        assertEquals("Nothing is missing.", survey.summary())
    }

    @Test
    fun `a recording with no length is counted`() = runBlocking {
        val store = FakeStore(recordings = mutableListOf(recording("a1", madeAt = 1_757_000_000)))
        val gaps = MissingData.survey(store).gaps.associate { it.key to it.count }
        assertEquals(1, gaps["duration"])
    }

    @Test
    fun `a length of zero counts as no length`() = runBlocking {
        val store = FakeStore(recordings = mutableListOf(recording("a1", duration = 0)))
        val gaps = MissingData.survey(store).gaps.associate { it.key to it.count }
        assertEquals(1, gaps["duration"])
    }

    @Test
    fun `a note with no cache is counted`() = runBlocking {
        val store = FakeStore(notes = mutableListOf(note("n1", cache = null), note("n2")))
        val gaps = MissingData.survey(store).gaps.associate { it.key to it.count }
        assertEquals(1, gaps["note_cache"])
    }

    @Test
    fun `a deleted recording and a deleted note are not counted`() = runBlocking {
        val gone = Stamp(at = 1_757_419_600, offset = null, zone = null)
        val store = FakeStore(
            recordings = mutableListOf(recording("a1", deletedAt = gone)),
            notes = mutableListOf(note("n1", cache = null, deletedAt = gone)),
        )
        val gaps = MissingData.survey(store).gaps.associate { it.key to it.count }
        assertEquals(0, gaps["duration"])
        assertEquals(0, gaps["note_cache"])
    }

    @Test
    fun `a recording whose file is on another device is counted but not calculable`() = runBlocking {
        val store = FakeStore(recordings = mutableListOf(recording("a1")))
        store.elsewhere.add("a1")
        val absent = MissingData.survey(store).gaps.first { it.key == "absent_file" }
        assertEquals(1, absent.count)
        assertFalse(absent.calculable)
    }

    @Test
    fun `a missing timezone is reported as uncalculable`() = runBlocking {
        val store = FakeStore(recordings = mutableListOf(recording("a1", importedOffset = null)))
        val zone = MissingData.survey(store).gaps.first { it.key == "timezone" }
        assertEquals(1, zone.count)
        assertFalse("guessing a timezone would state something false", zone.calculable)
        assertTrue(zone.note.contains("guessing"))
    }

    @Test
    fun `the summary reads as lines a person can read`() = runBlocking {
        val store = FakeStore(
            recordings = mutableListOf(recording("a1")),
            notes = mutableListOf(note("n1", cache = null)),
        )
        val summary = MissingData.survey(store).summary()
        assertTrue(summary.contains("Recordings with no length recorded"))
        assertTrue(summary.contains("Notes with no display cache"))
    }

    // --------------------------------------------------------- calculating

    @Test
    fun `a length is read off the file`() = runBlocking {
        val store = FakeStore(recordings = mutableListOf(recording("a1", madeAt = 1_757_000_000)))
        store.lengths["a1"] = 137

        val report = MissingData.calculate(store, caches = false)

        assertEquals(1, report.calculated["duration"])
        assertEquals(137L, store.savedLengths["a1"])
    }

    @Test
    fun `a length already known is left alone`() = runBlocking {
        val store = FakeStore(
            recordings = mutableListOf(recording("a1", duration = 90, madeAt = 1_757_000_000))
        )
        store.lengths["a1"] = 137

        val report = MissingData.calculate(store, caches = false)

        assertEquals(0, report.totalCalculated)
        assertNull(store.savedLengths["a1"])
    }

    @Test
    fun `a file whose length cannot be read is reported, not guessed`() = runBlocking {
        val store = FakeStore(recordings = mutableListOf(recording("a1", madeAt = 1_757_000_000)))
        // Nothing in lengths: the header said nothing, or the file is truncated

        val report = MissingData.calculate(store, caches = false)

        assertEquals(1, report.failed["duration"])
        assertEquals(0, report.totalCalculated)
        assertTrue(store.savedLengths.isEmpty())
    }

    @Test
    fun `a recording whose file is on another device is reported, not invented`() = runBlocking {
        val store = FakeStore(recordings = mutableListOf(recording("a1")))
        store.elsewhere.add("a1")

        val report = MissingData.calculate(store, caches = false)

        assertEquals(1, report.failed["absent_file"])
        assertEquals(0, report.totalCalculated)
    }

    @Test
    fun `a missing cache is rebuilt`() = runBlocking {
        val store = FakeStore(notes = mutableListOf(note("n1", cache = null), note("n2")))

        val report = MissingData.calculate(store, durations = false, fileDates = false)

        assertEquals(1, report.calculated["note_cache"])
        assertEquals(listOf("n1"), store.rebuilt)
    }

    @Test
    fun `a cache that will not rebuild is reported`() = runBlocking {
        val store = FakeStore(notes = mutableListOf(note("n1", cache = null)))
        store.rebuildFails = true

        val report = MissingData.calculate(store, durations = false, fileDates = false)

        assertEquals(1, report.failed["note_cache"])
        assertEquals(0, report.totalCalculated)
    }

    @Test
    fun `a run can be limited`() = runBlocking {
        val store = FakeStore(
            recordings = (1..4).map { recording("a$it", madeAt = 1_757_000_000) }.toMutableList()
        )
        (1..4).forEach { store.lengths["a$it"] = 60L * it }

        val report = MissingData.calculate(store, caches = false, limit = 2)

        assertEquals(2, report.calculated["duration"])
    }

    @Test
    fun `each repair can be left out`() = runBlocking {
        val store = FakeStore(
            recordings = mutableListOf(recording("a1")),
            notes = mutableListOf(note("n1", cache = null)),
        )
        store.lengths["a1"] = 42

        val report = MissingData.calculate(
            store, durations = false, fileDates = false, caches = false
        )

        assertEquals(0, report.totalCalculated)
    }

    @Test
    fun `progress is reported as it goes`() = runBlocking {
        val store = FakeStore(recordings = mutableListOf(recording("a1", madeAt = 1_757_000_000)))
        store.lengths["a1"] = 42
        val lines = mutableListOf<String>()

        MissingData.calculate(store, caches = false) { lines.add(it) }

        assertTrue(lines.any { it.contains("הקלטה.opus") })
    }

    @Test
    fun `running it twice changes nothing the second time`() = runBlocking {
        val store = FakeStore(
            recordings = mutableListOf(recording("a1")),
            notes = mutableListOf(note("n1", cache = null)),
        )
        store.lengths["a1"] = 42
        store.modified["a1"] = 1_757_000_000

        val first = MissingData.calculate(store)
        val second = MissingData.calculate(store)

        assertTrue(first.totalCalculated > 0)
        assertEquals(0, second.totalCalculated)
    }

    // -------------------------------------------------- the date in the name

    @Test
    fun `the shapes recorders write are read`() {
        val expected = at(2026, 9, 8, 14, 53, 14)
        assertEquals(expected, MissingData.dateInName("Recording 2026-09-08 14-53-14.opus"))
        assertEquals(expected, MissingData.dateInName("2026-09-08T14:53:14.m4a"))
        assertEquals(expected, MissingData.dateInName("2026-09-08_14.53.14.wav"))
        assertEquals(expected, MissingData.dateInName("REC_20260908_145314.mp3"))
    }

    @Test
    fun `a name with no time in it is not a date`() {
        assertNull(MissingData.dateInName("פגישה 2026-09-08.opus"))
        assertNull(MissingData.dateInName("הקלטה.opus"))
    }

    @Test
    fun `digits in the right shape that are not a real date are ignored`() {
        assertNull(MissingData.dateInName("2026-99-99 99-99-99.opus"))
    }

    @Test
    fun `the filesystem is believed when it is close to the name`() {
        val name = at(2026, 9, 8, 14, 53, 14)
        val fileSystem = name + 3600  // an hour later: the file was simply written then
        assertEquals(fileSystem, MissingData.madeAtFrom(fileSystem, "2026-09-08 14-53-14.opus"))
    }

    @Test
    fun `the name is believed when the filesystem is much later`() {
        val name = at(2019, 3, 4, 10, 20, 30)
        val fileSystem = name + MissingData.NAME_DATE_MARGIN_SECONDS + 1
        assertEquals(
            "a copy made without its dates looks exactly like this",
            name,
            MissingData.madeAtFrom(fileSystem, "Recording 2019-03-04 10-20-30 פגישה.opus")
        )
    }

    @Test
    fun `with no date in the name the filesystem is all there is`() {
        assertEquals(1_757_000_000L, MissingData.madeAtFrom(1_757_000_000L, "הקלטה.opus"))
        assertNull(MissingData.madeAtFrom(null, "הקלטה.opus"))
    }

    /** A local moment, as Unix seconds, the way a name's date is read. */
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int): Long {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.clear()
        calendar.set(y, mo - 1, d, h, mi, s)
        return calendar.timeInMillis / 1000
    }
}
