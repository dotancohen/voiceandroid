package com.dotancohen.voiceandroid.util

import com.dotancohen.voiceandroid.testing.FakePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settings the phone keeps for itself: what they are before the user
 * touches them, what they accept, and what they do with a value that cannot
 * be right.
 *
 * A setting read back wrong is a bug the user meets on every screen, and a
 * setting that throws on a value written by an older version of the
 * application takes the screen down. Both are cheap to prevent here.
 */
class UiSettingsTest {

    private fun prefs(vararg values: Pair<String, Any>) =
        UiPreferences(FakePreferences(values.toMap()))

    // What a phone that has never been to Settings does.

    @Test
    fun `the defaults are the quiet ones`() {
        val settings = prefs()
        assertEquals("one line, as the list has always shown", 1, settings.notesListLines)
        assertEquals(UiPreferences.DEFAULT_SPOTLIGHT_MS, settings.spotlightDurationMs)
        assertEquals(UiPreferences.SIZE_STANDARD, settings.uiSizeMode)
        assertFalse("a note must not start talking by itself", settings.autoplayOnOpen)
        assertFalse(settings.isLargeNow)
        assertNull("no format chosen means the phone's own", settings.timeFormat)
    }

    // Writing and reading back.

    @Test
    fun `what is written is what is read`() {
        val settings = prefs()
        settings.notesListLines = 4
        settings.spotlightDurationMs = 250
        settings.uiSizeMode = UiPreferences.SIZE_LARGE
        settings.autoplayOnOpen = true
        settings.timeFormat = "yyyy-MM-dd"
        settings.timeFormatCustom = "HH:mm -N"

        assertEquals(4, settings.notesListLines)
        assertEquals(250, settings.spotlightDurationMs)
        assertEquals(UiPreferences.SIZE_LARGE, settings.uiSizeMode)
        assertTrue(settings.autoplayOnOpen)
        assertEquals("yyyy-MM-dd", settings.timeFormat)
        assertEquals("HH:mm -N", settings.timeFormatCustom)
    }

    // Values that cannot be right.

    @Test
    fun `the number of lines is held between one and six`() {
        val settings = prefs()
        settings.notesListLines = 0
        assertEquals("a row of no lines is not a row", 1, settings.notesListLines)
        settings.notesListLines = -3
        assertEquals(1, settings.notesListLines)
        settings.notesListLines = 99
        assertEquals(UiPreferences.MAX_LIST_LINES, settings.notesListLines)
    }

    @Test
    fun `a stored number of lines from another version is brought into range`() {
        // The value in the file is not written by this code, so it is not
        // trusted on the way out either.
        assertEquals(UiPreferences.MAX_LIST_LINES, prefs("notes_list_lines" to 40).notesListLines)
        assertEquals(1, prefs("notes_list_lines" to 0).notesListLines)
    }

    @Test
    fun `the spotlight is held between off and one second`() {
        val settings = prefs()
        settings.spotlightDurationMs = -100
        assertEquals("off is zero, never less", 0, settings.spotlightDurationMs)
        settings.spotlightDurationMs = 10_000
        assertEquals(UiPreferences.MAX_SPOTLIGHT_MS, settings.spotlightDurationMs)
    }

    @Test
    fun `an interface size nobody has heard of reads as standard`() {
        val settings = prefs("ui_size_mode" to "enormous")
        assertEquals(UiPreferences.SIZE_STANDARD, settings.uiSizeMode)
        assertFalse(settings.isLargeNow)

        settings.uiSizeMode = "gigantic"
        assertEquals("and writing one stores standard", UiPreferences.SIZE_STANDARD, settings.uiSizeMode)
    }

    // The switch in the toolbar.

    @Test
    fun `the switch is only consulted in the mode that offers it`() {
        val settings = prefs()
        settings.toggledLarge = true

        settings.uiSizeMode = UiPreferences.SIZE_STANDARD
        assertFalse("Standard means standard", settings.isLargeNow)

        settings.uiSizeMode = UiPreferences.SIZE_TOGGLE
        assertTrue("now the switch decides", settings.isLargeNow)

        settings.toggledLarge = false
        assertFalse(settings.isLargeNow)

        settings.uiSizeMode = UiPreferences.SIZE_LARGE
        assertTrue("Large means large whatever the switch says", settings.isLargeNow)
    }

    @Test
    fun `the switch survives being left in either position`() {
        val settings = prefs()
        settings.toggledLarge = true
        assertTrue(settings.toggledLarge)
        settings.toggledLarge = false
        assertFalse(settings.toggledLarge)
    }

    // The custom time format.

    @Test
    fun `the custom pattern has a default before anything is typed`() {
        assertEquals(TimeFormat.DEFAULT_CUSTOM, prefs().timeFormatCustom)
    }

    @Test
    fun `settings live in one file, so every screen sees the same values`() {
        // Two settings classes, one file: a value written by the recorder
        // screen must be visible to the notes screen.
        val shared = FakePreferences()
        UiPreferences(shared).notesListLines = 5
        assertEquals(5, UiPreferences(shared).notesListLines)
    }

    // The size of the marks on a note, which is set apart from the size of
    // the interface as a whole.

    @Test
    fun `the marks start at the size they have always been`() {
        val ui = UiPreferences(FakePreferences())
        assertEquals(UiPreferences.ICONS_SMALL, ui.iconSize)
        assertEquals(1f, ui.iconScale, 0f)
    }

    @Test
    fun `each step up is half as large again as the one before`() {
        assertEquals(1f, UiPreferences.iconScaleFor(UiPreferences.ICONS_SMALL), 0f)
        assertEquals(1.5f, UiPreferences.iconScaleFor(UiPreferences.ICONS_MEDIUM), 0f)
        assertEquals("large is medium again by half", 2.25f, UiPreferences.iconScaleFor(UiPreferences.ICONS_LARGE), 0.0001f)
    }

    @Test
    fun `a chosen mark size is remembered, and gives its own scale`() {
        val ui = UiPreferences(FakePreferences())
        ui.iconSize = UiPreferences.ICONS_MEDIUM
        assertEquals(UiPreferences.ICONS_MEDIUM, ui.iconSize)
        assertEquals(1.5f, ui.iconScale, 0f)
        ui.iconSize = UiPreferences.ICONS_LARGE
        assertEquals(2.25f, ui.iconScale, 0.0001f)
    }

    @Test
    fun `a mark size nobody offers falls back to small`() {
        val prefs = FakePreferences()
        prefs.edit().putString("icon_size", "enormous").apply()
        val ui = UiPreferences(prefs)
        assertEquals(UiPreferences.ICONS_SMALL, ui.iconSize)
        assertEquals(1f, ui.iconScale, 0f)
        ui.iconSize = "enormous"
        assertEquals("and writing one is refused too", UiPreferences.ICONS_SMALL, ui.iconSize)
    }

    @Test
    fun `an unknown mark size scales by one rather than by nothing`() {
        assertEquals(1f, UiPreferences.iconScaleFor("enormous"), 0f)
        assertEquals(1f, UiPreferences.iconScaleFor(""), 0f)
    }

    @Test
    fun `every mark size has a name and a description of its own`() {
        val titles = UiPreferences.ICON_SIZES.map { UiPreferences.iconSizeTitle(it) }
        assertEquals(listOf("Small", "Medium", "Large"), titles)
        val descriptions = UiPreferences.ICON_SIZES.map { UiPreferences.iconSizeDescription(it) }
        assertEquals(descriptions.size, descriptions.toSet().size)
        assertTrue(descriptions.all { it.isNotBlank() })
    }

    @Test
    fun `the mark size and the interface size are separate settings`() {
        // A person who reads the text well may still want the marks big.
        val ui = UiPreferences(FakePreferences())
        ui.iconSize = UiPreferences.ICONS_LARGE
        assertEquals(UiPreferences.SIZE_STANDARD, ui.uiSizeMode)
        ui.uiSizeMode = UiPreferences.SIZE_LARGE
        assertEquals(UiPreferences.ICONS_LARGE, ui.iconSize)
    }
}
