package com.dotancohen.voiceandroid.audio

import com.dotancohen.voiceandroid.testing.FakePreferences
import com.dotancohen.voiceandroid.ui.components.SPEED_MARKS
import com.dotancohen.voiceandroid.ui.components.formatSpeed
import com.dotancohen.voiceandroid.ui.components.snapSpeed
import com.dotancohen.voiceandroid.ui.components.valueAtX
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The playback speed: the slider, the number beside it, and the setting that
 * outlives the player.
 *
 * A speed that reads 1× and plays at 1.05 is the sort of fault that is felt
 * rather than seen, so the marks are checked to the exact value.
 */
class PlaybackSpeedTest {

    private fun prefs() = PlaybackPreferences(FakePreferences())

    @Test
    fun `a fresh phone plays at the speed the recording was made at`() {
        assertEquals(1f, prefs().speed, 0f)
    }

    @Test
    fun `a chosen speed is remembered`() {
        val p = prefs()
        p.speed = 1.5f
        assertEquals(1.5f, p.speed, 0f)
    }

    @Test
    fun `a speed outside the range is brought back into it, both ways`() {
        val p = prefs()
        p.speed = 9f
        assertEquals(PlaybackPreferences.MAX_SPEED, p.speed, 0f)
        p.speed = 0.01f
        assertEquals(PlaybackPreferences.MIN_SPEED, p.speed, 0f)
        p.speed = -1f
        assertEquals(PlaybackPreferences.MIN_SPEED, p.speed, 0f)
    }

    @Test
    fun `a stored speed from an older version is clamped when read`() {
        val stored = FakePreferences()
        stored.edit().putFloat("playback_speed", 12f).apply()
        assertEquals(PlaybackPreferences.MAX_SPEED, PlaybackPreferences(stored).speed, 0f)
    }

    @Test
    fun `the range runs from half speed to three times`() {
        assertEquals(0.5f, PlaybackPreferences.MIN_SPEED, 0f)
        assertEquals(3.0f, PlaybackPreferences.MAX_SPEED, 0f)
    }

    @Test
    fun `every one-tap speed is one the player will accept`() {
        for (preset in PlaybackPreferences.PRESETS) {
            assertTrue(
                "$preset is outside the range",
                preset >= PlaybackPreferences.MIN_SPEED && preset <= PlaybackPreferences.MAX_SPEED
            )
        }
        assertEquals(
            "the presets are in order, without repeats",
            PlaybackPreferences.PRESETS.sorted().distinct(),
            PlaybackPreferences.PRESETS
        )
        assertTrue("normal speed must be one tap away", 1.0f in PlaybackPreferences.PRESETS)
    }

    @Test
    fun `a touch near a mark lands exactly on it`() {
        for ((mark, _) in SPEED_MARKS) {
            assertEquals(mark, snapSpeed(mark + 0.1f), 0f)
            assertEquals(mark, snapSpeed(mark - 0.1f), 0f)
            assertEquals(mark, snapSpeed(mark), 0f)
        }
    }

    @Test
    fun `a touch away from every mark keeps the speed it points at`() {
        assertEquals(1.25f, snapSpeed(1.25f), 0f)
        assertEquals(1.75f, snapSpeed(1.75f), 0f)
        assertEquals(2.5f, snapSpeed(2.5f), 0f)
    }

    @Test
    fun `speeds are rounded to a twentieth, which the label can show`() {
        assertEquals(1.25f, snapSpeed(1.263f), 0f)
        assertEquals(1.3f, snapSpeed(1.2876f), 0f)
        assertEquals(2.55f, snapSpeed(2.5432f), 0f)
    }

    @Test
    fun `the slider cannot be dragged out of the range`() {
        assertEquals(PlaybackPreferences.MIN_SPEED, snapSpeed(-4f), 0f)
        assertEquals(PlaybackPreferences.MAX_SPEED, snapSpeed(99f), 0f)
    }

    @Test
    fun `a touch at either end of the track is the end of the range`() {
        val width = 300f
        val pad = 10f
        val min = PlaybackPreferences.MIN_SPEED
        val max = PlaybackPreferences.MAX_SPEED
        assertEquals(min, valueAtX(pad, width, pad, min, max), 0.0001f)
        assertEquals(max, valueAtX(width - pad, width, pad, min, max), 0.0001f)
        assertEquals("the middle of the track is the middle of the range",
            (min + max) / 2, valueAtX(width / 2, width, pad, min, max), 0.0001f)
    }

    @Test
    fun `a touch outside the track is the nearest end, not a wilder number`() {
        val width = 300f
        val pad = 10f
        val min = PlaybackPreferences.MIN_SPEED
        val max = PlaybackPreferences.MAX_SPEED
        assertEquals(min, valueAtX(-50f, width, pad, min, max), 0.0001f)
        assertEquals(max, valueAtX(500f, width, pad, min, max), 0.0001f)
    }

    @Test
    fun `the label drops the digits that say nothing`() {
        assertEquals("1×", formatSpeed(1f))
        assertEquals("1.5×", formatSpeed(1.5f))
        assertEquals("1.25×", formatSpeed(1.25f))
        assertEquals("0.5×", formatSpeed(0.5f))
        assertEquals("3×", formatSpeed(3f))
    }

    @Test
    fun `the label is the same in every locale`() {
        // A comma for the decimal point would not match the marks under it.
        val original = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("1.5×", formatSpeed(1.5f))
        } finally {
            java.util.Locale.setDefault(original)
        }
    }
}
