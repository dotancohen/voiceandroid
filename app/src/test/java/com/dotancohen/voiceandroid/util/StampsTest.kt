package com.dotancohen.voiceandroid.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import uniffi.voicecore.Stamp
import java.util.TimeZone

/**
 * The part of showing a time that does not need Android: which clock a stamp
 * is drawn on. The drawing itself uses the phone's locale and its 12 or
 * 24-hour setting, so it is checked by hand on a device.
 */
class StampsTest {

    /** 2026-09-08 12:20:00 UTC, a summer afternoon in Jerusalem. */
    private val noon = 1_788_870_000L

    @Test
    fun `an offset of zero is a recorded zone, not a missing one`() {
        // A stamp from a device in London must be drawn in UTC, not in the
        // reader's timezone. Anything treating zero as absent breaks this.
        assertEquals(0, Stamp(noon, 0, "Etc/UTC").timeZone().rawOffset)
        assertEquals(TimeZone.getDefault(), Stamp(noon, null, null).timeZone())
    }

    @Test
    fun `offsets that are not whole hours survive`() {
        assertEquals(19_800_000, Stamp(noon, 5 * 3600 + 1800, "Asia/Kolkata").timeZone().rawOffset)
        assertEquals(20_700_000, Stamp(noon, 5 * 3600 + 2700, "Asia/Kathmandu").timeZone().rawOffset)
        assertEquals(45_900_000, Stamp(noon, 12 * 3600 + 2700, "Pacific/Chatham").timeZone().rawOffset)
    }

    @Test
    fun `the far ends of the map`() {
        assertEquals(50_400_000, Stamp(noon, 14 * 3600, "Pacific/Kiritimati").timeZone().rawOffset)
        assertEquals(-43_200_000, Stamp(noon, -12 * 3600, null).timeZone().rawOffset)
    }

    @Test
    fun `west of Greenwich is not east of it`() {
        val jerusalem = Stamp(noon, 3 * 3600, "Asia/Jerusalem")
        val newYork = Stamp(noon, -4 * 3600, "America/New_York")
        assertNotEquals(jerusalem.timeZone().rawOffset, newYork.timeZone().rawOffset)
        assertEquals(-14_400_000, newYork.timeZone().rawOffset)
    }

    @Test
    fun `an impossible offset falls back instead of throwing`() {
        // A corrupted row must not take the screen down. Java refuses offsets
        // beyond eighteen hours, so those are treated as unknown.
        assertEquals(TimeZone.getDefault(), Stamp(noon, 100 * 3600, "Nowhere/Real").timeZone())
        assertEquals(TimeZone.getDefault(), Stamp(noon, -100 * 3600, null).timeZone())
        // while the real edges still work
        assertEquals(50_400_000, Stamp(noon, 14 * 3600, null).timeZone().rawOffset)
    }

    @Test
    fun `the instant is the same everywhere`() {
        assertEquals(noon * 1000L, Stamp(noon, 3 * 3600, "Asia/Jerusalem").epochMillis)
        assertEquals(noon * 1000L, Stamp(noon, -4 * 3600, "America/New_York").epochMillis)
        // and before the epoch
        assertEquals(-14_182_940_000L, Stamp(-14_182_940L, 0, null).epochMillis)
    }
}
