package com.dotancohen.voiceandroid.util

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The list of date formats offered under Settings → Advanced.
 *
 * Each choice is shown as an example rather than as a pattern, so the twelve
 * examples must all depict the same instant — otherwise they cannot be
 * compared at a glance — and each must be what its pattern actually produces.
 * The thirteenth is the sentinel that opens the free-text field.
 */
class TimeFormatPresetsTest {

    /** Wednesday 3 September 2026, 14:05, the instant every label depicts. */
    private val sample = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .parse("2026-09-03 14:05")!!

    private val presets = TimeFormat.PRESETS.filter { it.second != TimeFormat.CUSTOM }

    @Test
    fun `there are twelve formats and the custom sentinel`() {
        assertEquals(13, TimeFormat.PRESETS.size)
        assertEquals(12, presets.size)
    }

    @Test
    fun `the custom sentinel is last, where the user looks for it`() {
        assertEquals(TimeFormat.CUSTOM, TimeFormat.PRESETS.last().second)
        assertTrue(TimeFormat.PRESETS.last().first.contains("Custom"))
    }

    @Test
    fun `no format is offered twice`() {
        val patterns = TimeFormat.PRESETS.map { it.second }
        assertEquals(patterns.size, patterns.toSet().size)
        val labels = TimeFormat.PRESETS.map { it.first }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun `every label is what its pattern actually produces`() {
        val formatter = { pattern: String ->
            SimpleDateFormat(pattern, Locale.UK)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(sample)
        }
        for ((label, pattern) in presets) {
            // Two differences here are the JVM's rather than the app's: it
            // writes "Sept" where Android writes "Sep", and "pm" where
            // Android writes "PM". Everything else must match the label.
            fun comparable(text: String) = text
                .replace("Sep ", "Sept ")
                .replace("Sep,", "Sept,")
                .lowercase()
            assertEquals(
                "the label for \"$pattern\" does not match what it renders",
                comparable(label),
                comparable(formatter(pattern))
            )
        }
    }

    @Test
    fun `every pattern is one SimpleDateFormat accepts`() {
        for ((_, pattern) in presets) {
            SimpleDateFormat(pattern, Locale.US).format(sample)
        }
    }

    @Test
    fun `the default format is one of the choices`() {
        assertTrue(
            "the default must be selectable, or the dropdown opens on nothing",
            TimeFormat.DEFAULT_PATTERN in presets.map { it.second }
        )
    }

    @Test
    fun `no preset uses the days-ago token, which belongs to the custom pattern`() {
        for ((_, pattern) in presets) {
            assertFalse(pattern, pattern.contains("-N"))
        }
        assertTrue(TimeFormat.DEFAULT_CUSTOM.contains("-N"))
    }

    @Test
    fun `the keys are the ones the specification names`() {
        // Another application writes the same file; the names must not drift.
        assertEquals("time_format", TimeFormat.KEY_FORMAT)
        assertEquals("time_format_custom", TimeFormat.KEY_CUSTOM)
        assertEquals("custom", TimeFormat.CUSTOM)
        assertEquals("d MMM yyyy, HH:mm", TimeFormat.DEFAULT_PATTERN)
        assertEquals("yyyy-MM-dd HH:mm EEE -N", TimeFormat.DEFAULT_CUSTOM)
    }
}
