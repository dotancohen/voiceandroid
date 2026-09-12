package com.dotancohen.voiceandroid.util

import com.dotancohen.voiceandroid.testing.FakePreferences
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the date ends and the time of day begins.
 *
 * A timestamp is drawn with its time a little smaller than its date, so the
 * two have to be told apart — in whatever order and with whatever
 * punctuation the chosen format puts them. Getting this wrong draws half a
 * date at the wrong size, which is worse than not doing it at all.
 */
class TimeFormatPartsTest {

    /** 2026-09-08 12:20:00 UTC. */
    private val noon = 1_788_870_000_000L
    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private fun prefsWith(pattern: String) = FakePreferences().also {
        it.edit().putString(TimeFormat.KEY_FORMAT, pattern).apply()
    }

    @Test
    fun `the pieces put back together are exactly what is shown`() {
        for ((_, pattern) in TimeFormat.PRESETS) {
            if (pattern == TimeFormat.CUSTOM) continue
            val prefs = prefsWith(pattern)
            assertEquals(
                pattern,
                TimeFormat.format(prefs, noon, utc),
                TimeFormat.parts(prefs, noon, utc).joinToString("") { it.text }
            )
        }
    }

    @Test
    fun `a format with both gives a date and a time`() {
        val parts = TimeFormat.parts(prefsWith("yyyy-MM-dd HH:mm"), noon, utc)
        assertEquals(2, parts.size)
        assertEquals("2026-09-08 ", parts[0].text)
        assertEquals(false, parts[0].isSmall)
        assertEquals("12:20", parts[1].text)
        assertEquals(true, parts[1].isSmall)
    }

    @Test
    fun `the separator stays with the date, so the time begins at its digits`() {
        val parts = TimeFormat.parts(prefsWith("d MMM yyyy, HH:mm"), noon, utc)
        assertTrue(parts[0].text.endsWith(", "))
        assertEquals("12:20", parts.last().text)
    }

    @Test
    fun `a format that leads with the time says so`() {
        val parts = TimeFormat.parts(prefsWith("h:mm a, MMM d"), noon, utc)
        assertEquals(2, parts.size)
        assertTrue("the time comes first here", parts[0].isSmall)
        assertTrue(parts[0].text.startsWith("12:20"))
        assertEquals(false, parts[1].isSmall)
    }

    @Test
    fun `a date with no time at all is all date`() {
        val parts = TimeFormat.parts(prefsWith("yyyy-MM-dd"), noon, utc)
        assertEquals(1, parts.size)
        assertEquals(false, parts[0].isSmall)
    }

    @Test
    fun `a time with no date at all is all time`() {
        val parts = TimeFormat.parts(prefsWith("HH:mm"), noon, utc)
        assertEquals(1, parts.size)
        assertTrue(parts[0].isSmall)
    }

    @Test
    fun `the meridiem belongs to the time it follows`() {
        val parts = TimeFormat.parts(prefsWith("MM/dd/yyyy h:mm a"), noon, utc)
        assertTrue(parts.last().isSmall)
        assertTrue(parts.last().text.lowercase().endsWith("pm"))
    }

    @Test
    fun `the weekday and the days-ago count are drawn small, like the time`() {
        val prefs = FakePreferences().also {
            it.edit()
                .putString(TimeFormat.KEY_FORMAT, TimeFormat.CUSTOM)
                .putString(TimeFormat.KEY_CUSTOM, TimeFormat.DEFAULT_CUSTOM)
                .apply()
        }
        // yyyy-MM-dd HH:mm EEE -N: the date at full size, then the time, the
        // weekday and the day count, all of them small.
        val parts = TimeFormat.parts(prefs, System.currentTimeMillis(), utc)
        assertEquals(2, parts.size)
        assertEquals(false, parts[0].isSmall)
        assertEquals(true, parts[1].isSmall)
        assertTrue("the day count is drawn small", parts[1].text.contains("-0"))
    }

    @Test
    fun `quoted text is not mistaken for a field`() {
        val parts = TimeFormat.patternParts("d MMM 'at' HH:mm")
        assertEquals(2, parts.size)
        assertTrue("the quoted word goes with the date before it", parts[0].first.contains("'at'"))
        assertEquals("HH:mm", parts[1].first)
    }

    @Test
    fun `a pattern that leads with a literal keeps it`() {
        val parts = TimeFormat.patternParts("'made ' d MMM")
        assertEquals(1, parts.size)
        assertTrue(parts[0].first.startsWith("'made '"))
    }

    @Test
    fun `a broken pattern still gives pieces, from the default`() {
        val prefs = FakePreferences().also {
            it.edit()
                .putString(TimeFormat.KEY_FORMAT, TimeFormat.CUSTOM)
                .putString(TimeFormat.KEY_CUSTOM, "yyyy QQQQQQ ZZZZZZZ %%%")
                .apply()
        }
        val parts = TimeFormat.parts(prefs, noon, utc)
        assertTrue(parts.isNotEmpty())
        assertTrue("a fallback is still a real date", parts.joinToString("") { it.text }.contains("2026"))
    }

    @Test
    fun `every preset divides into pieces that rejoin to the whole`() {
        for ((_, pattern) in TimeFormat.PRESETS) {
            if (pattern == TimeFormat.CUSTOM) continue
            val parts = TimeFormat.patternParts(pattern)
            assertTrue("$pattern gave nothing", parts.isNotEmpty())
            assertEquals("$pattern lost or gained characters", pattern, parts.joinToString("") { it.first })
            assertTrue("$pattern has an empty piece", parts.none { it.first.isEmpty() })
            assertTrue(
                "$pattern has two pieces of the same kind side by side",
                parts.zipWithNext().none { (a, b) -> a.second == b.second }
            )
        }
    }

    @Test
    fun `a weekday in front of the date is small, and the date after it is not`() {
        val parts = TimeFormat.patternParts("EEE d MMM, HH:mm")
        assertEquals(listOf(true, false, true), parts.map { it.second })
        assertEquals("EEE ", parts[0].first)
        assertEquals("d MMM, ", parts[1].first)
        assertEquals("HH:mm", parts[2].first)
    }
}
