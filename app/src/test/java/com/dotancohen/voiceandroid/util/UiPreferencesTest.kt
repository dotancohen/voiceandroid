package com.dotancohen.voiceandroid.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the interface-size setting and the toolbar switch decide what the user
 * sees.
 */
class UiPreferencesTest {

    @Test
    fun `standard is standard whatever the switch was left at`() {
        // Choosing Standard in Settings must not be undone by a switch the
        // user pressed days ago in the other mode.
        assertFalse(UiPreferences.isLargeFor(UiPreferences.SIZE_STANDARD, toggled = false))
        assertFalse(UiPreferences.isLargeFor(UiPreferences.SIZE_STANDARD, toggled = true))
    }

    @Test
    fun `large is large whatever the switch was left at`() {
        assertTrue(UiPreferences.isLargeFor(UiPreferences.SIZE_LARGE, toggled = false))
        assertTrue(UiPreferences.isLargeFor(UiPreferences.SIZE_LARGE, toggled = true))
    }

    @Test
    fun `with the switch offered the user decides`() {
        assertFalse(UiPreferences.isLargeFor(UiPreferences.SIZE_TOGGLE, toggled = false))
        assertTrue(UiPreferences.isLargeFor(UiPreferences.SIZE_TOGGLE, toggled = true))
    }

    @Test
    fun `an unknown setting is treated as standard`() {
        // A value from a newer version, or a broken preferences file: the
        // safe answer is the size the application has always had.
        assertFalse(UiPreferences.isLargeFor("enormous", toggled = false))
    }

    @Test
    fun `every size has a title and an explanation`() {
        for (mode in UiPreferences.UI_SIZES) {
            assertTrue(UiPreferences.uiSizeTitle(mode).isNotBlank())
            assertTrue(UiPreferences.uiSizeDescription(mode).isNotBlank())
        }
    }

    @Test
    fun `large is half again as large, not double`() {
        // Double left a phone holding two or three words to a line.
        assertEquals(1.5f, UiPreferences.LARGE_SCALE, 0.0001f)
    }
}
