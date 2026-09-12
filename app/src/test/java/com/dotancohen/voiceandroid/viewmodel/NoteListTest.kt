package com.dotancohen.voiceandroid.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The two pieces of the notes list that have corners worth checking: how
 * many lines of a note are shown, and which note a step lands on.
 */
class NoteListTest {

    // How much of a note's text a row shows.

    @Test
    fun `the first line is taken when one line is asked for`() {
        val text = "הפגישה נדחתה ליום רביעי\nלהביא את המסמכים\nולהתקשר למשרד"
        assertEquals(listOf("הפגישה נדחתה ליום רביעי"), openingLines(text, 1))
    }

    @Test
    fun `more lines are taken in order`() {
        val text = "הפגישה נדחתה ליום רביעי\nלהביא את המסמכים\nולהתקשר למשרד"
        assertEquals(
            listOf("הפגישה נדחתה ליום רביעי", "להביא את המסמכים"),
            openingLines(text, 2)
        )
    }

    @Test
    fun `empty lines are passed over rather than counted`() {
        // A note that starts with a blank line, or has one between
        // paragraphs, must not spend its rows on nothing.
        val text = "\n\nשורה ראשונה\n\n   \nשורה שנייה\n"
        assertEquals(listOf("שורה ראשונה", "שורה שנייה"), openingLines(text, 2))
    }

    @Test
    fun `lines are trimmed`() {
        assertEquals(listOf("שלום"), openingLines("   שלום   ", 1))
    }

    @Test
    fun `asking for more lines than there are gives what there is`() {
        assertEquals(listOf("שורה יחידה"), openingLines("שורה יחידה", 6))
    }

    @Test
    fun `a note with no text gives nothing`() {
        assertEquals(emptyList<String>(), openingLines("", 3))
        assertEquals(emptyList<String>(), openingLines(null, 3))
        assertEquals(emptyList<String>(), openingLines("\n \n\n", 3))
    }

    @Test
    fun `a long transcription is cut to the lines a row can ever need`() {
        val text = (1..100).joinToString("\n") { "שורה $it" }
        assertEquals(TRANSCRIPTION_PREVIEW_LINES, openingLines(text).size)
    }

    // Which note Previous and Next lead to.

    private val ids = listOf("aaa", "bbb", "ccc")

    @Test
    fun `a note in the middle has one on either side`() {
        val n = neighboursIn(ids, "bbb")
        assertEquals("aaa", n.previous)
        assertEquals("ccc", n.next)
        assertEquals(2, n.position)
        assertEquals(3, n.total)
    }

    @Test
    fun `the first note has nothing before it`() {
        val n = neighboursIn(ids, "aaa")
        assertNull(n.previous)
        assertEquals("bbb", n.next)
        assertEquals(1, n.position)
    }

    @Test
    fun `the last note has nothing after it`() {
        val n = neighboursIn(ids, "ccc")
        assertEquals("bbb", n.previous)
        assertNull(n.next)
        assertEquals(3, n.position)
    }

    @Test
    fun `the only note has neither`() {
        val n = neighboursIn(listOf("aaa"), "aaa")
        assertNull(n.previous)
        assertNull(n.next)
        assertEquals(1, n.position)
        assertEquals(1, n.total)
    }

    @Test
    fun `a note that is not in the list has neither`() {
        // Deleted while open, or filtered out by a search: the buttons go
        // grey rather than leading somewhere arbitrary.
        val n = neighboursIn(ids, "zzz")
        assertNull(n.previous)
        assertNull(n.next)
        assertEquals(0, n.position)
        assertEquals(0, n.total)
    }

    @Test
    fun `an empty list leaves nowhere to step`() {
        val n = neighboursIn(emptyList(), "aaa")
        assertNull(n.previous)
        assertNull(n.next)
    }
}
