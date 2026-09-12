package com.dotancohen.voiceandroid.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The clock readings on the notes list and under the player. A recording that
 * reads 1:05 when it is 65 minutes long, or 0:00 when it is three seconds
 * long, makes the user think the recording was lost.
 */
class DurationsTest {

    @Test
    fun `seconds below a minute keep a zero minute`() {
        assertEquals("0:00", Durations.ofSeconds(0))
        assertEquals("0:01", Durations.ofSeconds(1))
        assertEquals("0:09", Durations.ofSeconds(9))
        assertEquals("0:59", Durations.ofSeconds(59))
    }

    @Test
    fun `the seconds are always two digits`() {
        assertEquals("1:00", Durations.ofSeconds(60))
        assertEquals("1:05", Durations.ofSeconds(65))
        assertEquals("2:00", Durations.ofSeconds(120))
    }

    @Test
    fun `an hour brings a third field, and the minutes are then padded`() {
        assertEquals("1:00:00", Durations.ofSeconds(3600))
        assertEquals("1:00:01", Durations.ofSeconds(3601))
        assertEquals("1:05:05", Durations.ofSeconds(3905))
        assertEquals("59:59", Durations.ofSeconds(3599))
    }

    @Test
    fun `a long lecture is still one number of hours`() {
        assertEquals("3:20:15", Durations.ofSeconds(3 * 3600 + 20 * 60 + 15))
        assertEquals("25:00:00", Durations.ofSeconds(25 * 3600))
    }

    @Test
    fun `a negative length reads as nothing, not as a negative clock`() {
        assertEquals("0:00", Durations.ofSeconds(-1))
        assertEquals("0:00", Durations.ofSeconds(-3600))
    }

    @Test
    fun `a playback position pads the minutes, so the seek bar does not shift`() {
        assertEquals("00:00", Durations.ofMillis(0))
        assertEquals("00:01", Durations.ofMillis(1000))
        assertEquals("01:05", Durations.ofMillis(65_000))
        assertEquals("59:59", Durations.ofMillis(3_599_000))
    }

    @Test
    fun `a position under a second reads as zero, not as one`() {
        assertEquals("00:00", Durations.ofMillis(1))
        assertEquals("00:00", Durations.ofMillis(999))
        assertEquals("00:01", Durations.ofMillis(1_001))
        assertEquals("00:01", Durations.ofMillis(1_999))
    }

    @Test
    fun `a position before the start reads as zero`() {
        assertEquals("00:00", Durations.ofMillis(-1))
        assertEquals("00:00", Durations.ofMillis(-60_000))
    }

    @Test
    fun `a position past an hour gains the hour field`() {
        assertEquals("1:00:00", Durations.ofMillis(3_600_000))
        assertEquals("2:03:04", Durations.ofMillis((2 * 3600 + 3 * 60 + 4) * 1000L))
    }

    @Test
    fun `a very long recording does not overflow`() {
        // Ten hours of audio, in milliseconds, is past the range of an Int.
        assertEquals("10:00:00", Durations.ofMillis(10L * 3_600_000))
    }

    @Test
    fun `the two readings agree on the same length`() {
        for (seconds in listOf(0, 1, 59, 60, 61, 3599, 3600, 7325)) {
            val fromMillis = Durations.ofMillis(seconds * 1000L)
            val fromSeconds = Durations.ofSeconds(seconds)
            assertEquals(
                "$seconds s: the list and the player must not disagree",
                fromSeconds.trimStart('0').ifEmpty { "0" },
                fromMillis.trimStart('0').ifEmpty { "0" }
            )
        }
    }

    @Test
    fun `the digits are Western even under a locale that would use others`() {
        val original = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"))
            assertEquals("1:05", Durations.ofSeconds(65))
            assertEquals("01:05", Durations.ofMillis(65_000))
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("he-IL"))
            assertEquals("1:05", Durations.ofSeconds(65))
        } finally {
            java.util.Locale.setDefault(original)
        }
    }
}
