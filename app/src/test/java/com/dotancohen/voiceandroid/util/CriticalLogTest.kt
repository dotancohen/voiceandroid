package com.dotancohen.voiceandroid.util

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The log of things the user needs to know about: an import that failed, a
 * sync that did not go through.
 *
 * It is written on a phone with no room to spare, and it is the only record
 * of a recording that never made it into the database, so it must both keep
 * its newest entries and refuse to grow without end.
 */
class CriticalLogTest {

    private lateinit var file: File

    @Before
    fun setUp() {
        file = File.createTempFile("critical", ".log")
        file.delete()
        CriticalLog.useFile(file)
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `a fresh log is empty and says so`() {
        assertFalse(CriticalLog.hasEntries())
        assertEquals(0, CriticalLog.getEntryCount())
        assertEquals("", CriticalLog.getLogContents())
    }

    @Test
    fun `an entry is written with its category and message`() {
        CriticalLog.log("IMPORT_FAILED", "Failed to import: הקלטה.opus")
        val text = CriticalLog.getLogContents()
        assertTrue(text, text.contains("[IMPORT_FAILED]"))
        assertTrue("the Hebrew filename must survive", text.contains("הקלטה.opus"))
        assertTrue(CriticalLog.hasEntries())
        assertEquals(1, CriticalLog.getEntryCount())
    }

    @Test
    fun `an entry carries a timestamp that can be read back`() {
        CriticalLog.log("SYNC_ERROR", "Sync operation failed: upload")
        val line = CriticalLog.getLogContents().lineSequence().first()
        assertTrue(line, Regex("""^\[\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}] \[SYNC_ERROR] .*""").matches(line))
    }

    @Test
    fun `details are written under the entry, not counted as one`() {
        CriticalLog.logImportFailure("הקלטה.opus", "File not found")
        val text = CriticalLog.getLogContents()
        assertTrue(text.contains("Details: File not found"))
        assertEquals("the detail line is part of the same entry", 1, CriticalLog.getEntryCount())
    }

    @Test
    fun `a failed import names the file and the reason`() {
        CriticalLog.logImportFailure("שיחה.m4a", "The file is not audio")
        val text = CriticalLog.getLogContents()
        assertTrue(text.contains("IMPORT_FAILED"))
        assertTrue(text.contains("שיחה.m4a"))
        assertTrue(text.contains("The file is not audio"))
    }

    @Test
    fun `a failed sync names the operation and the reason`() {
        CriticalLog.logSyncError("upload", "The network is down")
        val text = CriticalLog.getLogContents()
        assertTrue(text.contains("SYNC_ERROR"))
        assertTrue(text.contains("upload"))
        assertTrue(text.contains("The network is down"))
    }

    @Test
    fun `entries are kept in the order they happened`() {
        CriticalLog.log("A", "first")
        CriticalLog.log("B", "second")
        CriticalLog.log("C", "third")
        val lines = CriticalLog.getLogContents().lines()
        assertTrue(lines[0].contains("first"))
        assertTrue(lines[1].contains("second"))
        assertTrue(lines[2].contains("third"))
        assertEquals(3, CriticalLog.getEntryCount())
    }

    @Test
    fun `reading asks for the newest entries, not the oldest`() {
        for (i in 1..10) CriticalLog.log("N", "entry $i")
        val text = CriticalLog.getLogContents(maxLines = 3)
        assertEquals(3, text.lines().size)
        assertTrue(text.contains("entry 10"))
        assertFalse(text.contains("entry 1 "))
    }

    @Test
    fun `a log that has grown past its limit is cut down, keeping the newest`() {
        // A megabyte of old entries, then one more write, which rotates.
        val filler = (1..30_000).joinToString("\n") { "[2026-01-01 00:00:00] [OLD] entry $it" }
        file.writeText(filler + "\n")
        assertTrue("the fixture must exceed the limit", file.length() > CriticalLog.maxSizeBytes)

        CriticalLog.log("NEW", "the newest entry")

        assertTrue("the file must have shrunk", file.length() < CriticalLog.maxSizeBytes)
        val lines = file.readLines()
        assertEquals(CriticalLog.linesKeptOnRotation + 1, lines.size)
        assertTrue("the newest entry must survive its own rotation", lines.last().contains("the newest entry"))
        assertTrue("the entries kept must be the last of the old ones", lines.first().contains("entry 29"))
    }

    @Test
    fun `a log under the limit is not rotated`() {
        CriticalLog.log("A", "keep me")
        CriticalLog.log("B", "and me")
        assertEquals(2, CriticalLog.getEntryCount())
        assertTrue(CriticalLog.getLogContents().contains("keep me"))
    }

    @Test
    fun `clearing empties the log without losing the file`() {
        CriticalLog.log("A", "something")
        CriticalLog.clearLog()
        assertFalse(CriticalLog.hasEntries())
        assertEquals(0, CriticalLog.getEntryCount())
        assertTrue("the file itself stays, ready for the next entry", file.exists())
    }

    @Test
    fun `the path is reported, so the user can be told where to look`() {
        assertEquals(file.absolutePath, CriticalLog.getLogFilePath())
    }

    @Test
    fun `a log that cannot be written does not take the app down`() {
        // The directory is gone, as when the file was on a card that was removed.
        CriticalLog.useFile(File("/no/such/directory/critical.log"))
        CriticalLog.log("A", "nowhere to write this")
        assertEquals(0, CriticalLog.getEntryCount())
        assertFalse(CriticalLog.hasEntries())
        assertEquals("", CriticalLog.getLogContents())
    }
}
