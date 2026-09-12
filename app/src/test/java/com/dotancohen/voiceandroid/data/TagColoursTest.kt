package com.dotancohen.voiceandroid.data

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The colour of a Tag.
 *
 * Without a choice it is the first six characters of the MD5 of the Tag's
 * name, which means every device and every application arrives at the same
 * colour for the same Tag without storing or syncing anything. That only
 * holds while the calculation is exactly this one, so it is pinned here.
 */
class TagColoursTest {

    private fun md5(text: String): String =
        MessageDigest.getInstance("MD5")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    @Test
    fun `the calculated colour is the first six characters of the MD5 of the name`() {
        for (name in listOf("עבודה", "Work", "Trips 2026", "a", "")) {
            assertEquals(md5(name).take(6), TagColours.calculatedFor(name))
        }
    }

    @Test
    fun `a known name gives a known colour, so nothing drifts`() {
        // md5("Work") = 0f9263536b9fc61ada745644735bfd8f
        assertEquals("0f9263", TagColours.calculatedFor("Work"))
    }

    @Test
    fun `a Hebrew name is hashed as its own bytes`() {
        assertEquals(md5("עבודה").take(6), TagColours.calculatedFor("עבודה"))
        assertEquals(6, TagColours.calculatedFor("עבודה").length)
    }

    @Test
    fun `the same name always gives the same colour, and different names rarely do`() {
        assertEquals(TagColours.calculatedFor("Work"), TagColours.calculatedFor("Work"))
        assertTrue(TagColours.calculatedFor("Work") != TagColours.calculatedFor("work"))
        assertTrue(TagColours.calculatedFor("עבודה") != TagColours.calculatedFor("נסיעות"))
    }

    @Test
    fun `a chosen colour is used instead of the calculated one`() {
        assertEquals("ff0000", TagColours.colourFor("Work", "ff0000"))
        assertEquals("ff0000", TagColours.colourFor("Work", "#FF0000"))
        assertEquals("ff0000", TagColours.colourFor("Work", " FF0000 "))
    }

    @Test
    fun `no choice, an empty choice or a broken one falls back to the calculated colour`() {
        val calculated = TagColours.calculatedFor("Work")
        assertEquals(calculated, TagColours.colourFor("Work", null))
        assertEquals(calculated, TagColours.colourFor("Work", ""))
        assertEquals(calculated, TagColours.colourFor("Work", "red"))
        assertEquals(calculated, TagColours.colourFor("Work", "ff00"))
        assertEquals(calculated, TagColours.colourFor("Work", "gggggg"))
        assertEquals(calculated, TagColours.colourFor("Work", "ff0000ff"))
    }

    @Test
    fun `the colour is given to Compose as an opaque ARGB value`() {
        assertEquals(0xFFFF0000.toInt(), TagColours.argbFor("Work", "ff0000"))
        assertEquals(0xFF000000.toInt(), TagColours.argbFor("Work", "000000"))
        assertEquals(0xFFFFFFFF.toInt(), TagColours.argbFor("Work", "ffffff"))
    }

    @Test
    fun `a pale colour takes black text and a dark one takes white`() {
        assertTrue("white", TagColours.prefersDarkText("ffffff"))
        assertTrue("pale yellow", TagColours.prefersDarkText("fff59d"))
        assertFalse("black", TagColours.prefersDarkText("000000"))
        assertFalse("navy", TagColours.prefersDarkText("1a237e"))
        assertFalse("a broken value is treated as dark", TagColours.prefersDarkText("nonsense"))
    }

    @Test
    fun `green looks brighter than blue at the same value, as the eye sees it`() {
        assertTrue(TagColours.prefersDarkText("00ff00"))
        assertFalse(TagColours.prefersDarkText("0000ff"))
    }

    @Test
    fun `the setting key is the name, so the desktop finds the same one`() {
        assertEquals("tag_color.עבודה", TagColours.settingKey("עבודה"))
        assertEquals("tag_color.Work", TagColours.settingKey("Work"))
    }
}
