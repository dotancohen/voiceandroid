package com.dotancohen.voiceandroid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.voicecore.Stamp

/**
 * A transcription's `state` is a space-separated list of tags, where a leading
 * `!` means "not". The user toggles these from the note screen, and the result
 * is written back to the database, so a toggle that loses a tag or leaves a
 * stray space corrupts the record for every device that syncs it.
 */
class TranscriptionFlagWordsTest {

    private fun stamp() = Stamp(at = 1_757_419_500, offset = 10800, zone = "Asia/Jerusalem")

    private fun transcription(state: String, content: String = "שלום עולם") = Transcription(
        id = "t1",
        audioFileId = "a1",
        content = content,
        service = "whisper",
        state = state,
        deviceId = "phone",
        createdAt = stamp(),
    )

    @Test
    fun `a tag that is present is true, and its negation is not`() {
        val t = transcription("original !verified cleaned")
        assertTrue(t.isOriginal)
        assertTrue(t.isCleaned)
        assertFalse(t.isVerified)
        assertFalse(t.isPolished)
    }

    @Test
    fun `a tag absent from the state is false`() {
        val t = transcription("original")
        assertFalse("nothing said about verified, so it is not verified", t.isVerified)
        assertFalse(t.hasFlag("verified"))
        assertFalse(t.hasFlag("!verified"))
    }

    @Test
    fun `the negation is a tag of its own`() {
        val t = transcription("!verified")
        assertTrue(t.hasFlag("!verified"))
        assertFalse(t.hasFlag("verified"))
    }

    @Test
    fun `a tag is not matched by a longer tag that starts with it`() {
        // "verbatim" must not answer for "verb", and "verified_by_me" must not
        // answer for "verified".
        val t = transcription("verified_by_me verbatim")
        assertFalse(t.isVerified)
        assertFalse(t.hasFlag("verb"))
        assertTrue(t.hasFlag("verbatim"))
    }

    @Test
    fun `toggling a true tag makes it false`() {
        val next = transcription("original verified").toggleFlag("verified")
        assertTrue(transcription(next).hasFlag("!verified"))
        assertFalse(transcription(next).isVerified)
        assertTrue("the other tags survive", transcription(next).isOriginal)
    }

    @Test
    fun `toggling a false tag makes it true`() {
        val next = transcription("original !verified").toggleFlag("verified")
        assertTrue(transcription(next).isVerified)
        assertFalse(transcription(next).hasFlag("!verified"))
        assertTrue(transcription(next).isOriginal)
    }

    @Test
    fun `toggling a tag that was never mentioned adds it as true`() {
        val next = transcription("original").toggleFlag("polished")
        assertTrue(transcription(next).isPolished)
        assertTrue(transcription(next).isOriginal)
    }

    @Test
    fun `toggling twice returns to the meaning it started with`() {
        for (start in listOf("original verified", "original !verified", "original")) {
            val once = transcription(start).toggleFlag("verified")
            val twice = transcription(once).toggleFlag("verified")
            assertEquals(
                "starting from '$start', two toggles must agree with one round trip",
                transcription(once).isVerified,
                !transcription(twice).isVerified
            )
            assertEquals(
                "a third toggle must match the first",
                once.split(" ").toSet(),
                transcription(twice).toggleFlag("verified").split(" ").toSet()
            )
        }
    }

    @Test
    fun `a toggle never leaves a doubled or trailing space`() {
        for (start in listOf("original verified", "original", "!verified", "")) {
            val next = transcription(start).toggleFlag("verified")
            assertFalse("'$next' has a doubled space", next.contains("  "))
            assertEquals("'$next' has an outer space", next.trim(), next)
        }
    }

    @Test
    fun `toggling on an empty state produces just that tag`() {
        assertEquals("verified", transcription("").toggleFlag("verified"))
    }

    @Test
    fun `a state written with sloppy spacing is still read correctly`() {
        val t = transcription("  original   !verified ")
        assertTrue(t.isOriginal)
        assertFalse(t.isVerified)
        assertEquals("original verified", t.toggleFlag("verified"))
    }

    @Test
    fun `a toggle keeps every tag it did not touch`() {
        val start = "original !verified verbatim !cleaned polished"
        val next = transcription(start).toggleFlag("cleaned")
        val before = start.split(" ").filterNot { it.endsWith("cleaned") }.toSet()
        val after = next.split(" ").filterNot { it.endsWith("cleaned") }.toSet()
        assertEquals(before, after)
        assertTrue(transcription(next).isCleaned)
    }

    @Test
    fun `a toggle does not change the Hebrew text it describes`() {
        val hebrew = "זוהי תמלול של הקלטה בעברית"
        val t = transcription("original !verified", content = hebrew)
        val next = t.copy(state = t.toggleFlag("verified"))
        assertEquals(hebrew, next.content)
        assertEquals(t.id, next.id)
        assertEquals(t.audioFileId, next.audioFileId)
    }
}
