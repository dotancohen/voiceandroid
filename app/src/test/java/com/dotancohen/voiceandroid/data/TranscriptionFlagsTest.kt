package com.dotancohen.voiceandroid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.voicecore.Stamp

/**
 * The five things that can be said about a transcription.
 *
 * They are words written into the transcription's own record and synced to
 * every device, so their spelling is not a detail of this screen: a phone
 * writing "polish" where the desktop writes "polished" would leave two
 * applications disagreeing about the same transcription for good.
 */
class TranscriptionFlagsTest {

    private fun transcription(state: String) = Transcription(
        id = "t1",
        audioFileId = "a1",
        content = "שלום עולם",
        service = "local_whisper",
        state = state,
        deviceId = "phone",
        createdAt = Stamp(at = 1_757_419_500, offset = 10800, zone = "Asia/Jerusalem"),
    )

    @Test
    fun `there are five, in the order a transcription lives through them`() {
        assertEquals(
            listOf("original", "verified", "verbatim", "cleaned", "polished"),
            TranscriptionFlags.ALL.map { it.name }
        )
    }

    @Test
    fun `each has a title and a line saying what it means`() {
        for (flag in TranscriptionFlags.ALL) {
            assertTrue(flag.name, flag.title.isNotBlank())
            assertTrue(flag.name, flag.description.isNotBlank())
            assertEquals(
                "the title is the tag with a capital",
                flag.name.replaceFirstChar { it.uppercase() },
                flag.title
            )
        }
    }

    @Test
    fun `the wording is the wording the user manuals carry`() {
        // Both manuals print this table; the desktop application prints it
        // too. Changing a line here means changing it in three places, and
        // this test is the reminder.
        val expected = mapOf(
            "original" to "Unmodified transcription from the service",
            "verified" to "User has verified the transcription is accurate",
            "verbatim" to "Transcription includes filler words, false starts, etc.",
            "cleaned" to "Transcription has been cleaned up (remove filler words)",
            "polished" to "Transcription has been edited for readability",
        )
        assertEquals(expected, TranscriptionFlags.ALL.associate { it.name to it.description })
    }

    @Test
    fun `no tag is written twice, and none carries the negation mark`() {
        val tags = TranscriptionFlags.ALL.map { it.name }
        assertEquals(tags.size, tags.toSet().size)
        for (tag in tags) {
            assertFalse("$tag must not begin with the mark that negates it", tag.startsWith("!"))
            assertFalse("$tag must hold no space", tag.contains(" "))
        }
    }

    @Test
    fun `a new transcription is original and nothing else`() {
        val fresh = transcription(TranscriptionFlags.DEFAULT_FLAGS)
        assertTrue(fresh.isOriginal)
        assertFalse(fresh.isVerified)
        assertFalse(fresh.hasFlag("verbatim"))
        assertFalse(fresh.isCleaned)
        assertFalse(fresh.isPolished)
    }

    @Test
    fun `the default state names all five, so nothing is left unsaid`() {
        val words = TranscriptionFlags.DEFAULT_FLAGS.split(" ")
        assertEquals(5, words.size)
        for (flag in TranscriptionFlags.ALL) {
            assertTrue(
                "${flag.name} is missing from the default state",
                words.any { it == flag.name || it == "!${flag.name}" }
            )
        }
    }

    @Test
    fun `every one of the five can be turned on and off again`() {
        for (flag in TranscriptionFlags.ALL) {
            var current = transcription(TranscriptionFlags.DEFAULT_FLAGS)
            val wasOn = current.hasFlag(flag.name)
            current = current.copy(state = current.toggleFlag(flag.name))
            assertEquals("toggling ${flag.name} did nothing", !wasOn, current.hasFlag(flag.name))
            current = current.copy(state = current.toggleFlag(flag.name))
            assertEquals("toggling ${flag.name} twice did not return", wasOn, current.hasFlag(flag.name))
        }
    }

    @Test
    fun `turning one on leaves the other four alone`() {
        val before = transcription(TranscriptionFlags.DEFAULT_FLAGS)
        val after = before.copy(state = before.toggleFlag("verified"))
        for (flag in TranscriptionFlags.ALL.filter { it.name != "verified" }) {
            assertEquals(
                "${flag.name} changed while verified was toggled",
                before.hasFlag(flag.name),
                after.hasFlag(flag.name)
            )
        }
    }
}
