package com.dotancohen.voiceandroid.util

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

/**
 * The user's chosen way of writing a date and time.
 *
 * The pattern comes from Settings → Advanced, and the extra token `-N` means
 * "days ago", which SimpleDateFormat knows nothing about.
 */
class TimeFormatTest {

    /** Just enough SharedPreferences to answer getString. */
    private fun prefs(vararg values: Pair<String, String>): SharedPreferences {
        val map = values.toMap()
        return object : SharedPreferences {
            override fun getString(key: String?, defValue: String?): String? = map[key] ?: defValue
            override fun getAll(): MutableMap<String, *> = map.toMutableMap()
            override fun getStringSet(key: String?, defValues: MutableSet<String>?) = defValues
            override fun getInt(key: String?, defValue: Int) = defValue
            override fun getLong(key: String?, defValue: Long) = defValue
            override fun getFloat(key: String?, defValue: Float) = defValue
            override fun getBoolean(key: String?, defValue: Boolean) = defValue
            override fun contains(key: String?) = map.containsKey(key)
            override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException()
            override fun registerOnSharedPreferenceChangeListener(
                listener: SharedPreferences.OnSharedPreferenceChangeListener?
            ) = Unit
            override fun unregisterOnSharedPreferenceChangeListener(
                listener: SharedPreferences.OnSharedPreferenceChangeListener?
            ) = Unit
        }
    }

    /** 3 September 2026, 14:05:00 UTC. */
    private val moment = 1_788_444_300_000L
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun `the default format is used when nothing was chosen`() {
        // The month's short name comes from the platform's own data ("Sep"
        // on Android, "Sept" on a recent JVM), so the parts that are ours
        // are what is checked.
        val out = TimeFormat.format(prefs(), moment, utc)
        assertTrue(out, out.startsWith("3 Sep"))
        assertTrue(out, out.endsWith("2026, 14:05"))
    }

    @Test
    fun `a chosen preset is used`() {
        val out = TimeFormat.format(
            prefs(TimeFormat.KEY_FORMAT to "yyyy-MM-dd HH:mm"), moment, utc
        )
        assertEquals("2026-09-03 14:05", out)
    }

    @Test
    fun `every preset renders without throwing`() {
        for ((label, pattern) in TimeFormat.PRESETS) {
            if (pattern == TimeFormat.CUSTOM) continue
            val out = TimeFormat.format(prefs(TimeFormat.KEY_FORMAT to pattern), moment, utc)
            assertTrue("$label produced nothing", out.isNotBlank())
        }
    }

    @Test
    fun `the custom pattern is used when custom is chosen`() {
        val out = TimeFormat.format(
            prefs(
                TimeFormat.KEY_FORMAT to TimeFormat.CUSTOM,
                TimeFormat.KEY_CUSTOM to "yyyy-MM-dd HH:mm"
            ),
            moment,
            utc
        )
        assertEquals("2026-09-03 14:05", out)
    }

    @Test
    fun `the days-ago token becomes a number`() {
        // Today is zero days ago, whatever today is when this runs.
        val out = TimeFormat.format(
            prefs(
                TimeFormat.KEY_FORMAT to TimeFormat.CUSTOM,
                TimeFormat.KEY_CUSTOM to "-N"
            ),
            System.currentTimeMillis(),
            TimeZone.getDefault()
        )
        // Wrapped in the directional isolates that keep the minus on the left
        assertEquals("⁦-0⁩", out)
    }

    @Test
    fun `yesterday is one day ago, counted in whole days`() {
        val yesterdayEvening = System.currentTimeMillis() - 20 * 60 * 60 * 1000L
        val out = TimeFormat.format(
            prefs(
                TimeFormat.KEY_FORMAT to TimeFormat.CUSTOM,
                TimeFormat.KEY_CUSTOM to "-N"
            ),
            yesterdayEvening,
            TimeZone.getDefault()
        )
        // Twenty hours back is either yesterday or today, depending on the
        // hour it runs at; both are whole-day answers, never a fraction.
        assertTrue(out == "⁦-0⁩" || out == "⁦-1⁩")
    }

    @Test
    fun `a time still to come reads as zero days ago, not a positive number`() {
        val tomorrow = System.currentTimeMillis() + 48 * 60 * 60 * 1000L
        val out = TimeFormat.format(
            prefs(
                TimeFormat.KEY_FORMAT to TimeFormat.CUSTOM,
                TimeFormat.KEY_CUSTOM to "-N"
            ),
            tomorrow,
            TimeZone.getDefault()
        )
        assertEquals("⁦-0⁩", out)
    }

    @Test
    fun `a broken pattern falls back to the default instead of crashing`() {
        // A free-text field will be typed in badly one day; the screen must
        // still draw.
        val out = TimeFormat.format(
            prefs(
                TimeFormat.KEY_FORMAT to TimeFormat.CUSTOM,
                TimeFormat.KEY_CUSTOM to "yyyy 'unclosed"
            ),
            moment,
            utc
        )
        assertTrue(out, out.endsWith("2026, 14:05"))
    }

    @Test
    fun `the timestamp is drawn in the zone it was written in`() {
        val jerusalem = TimeZone.getTimeZone("Asia/Jerusalem")
        val out = TimeFormat.format(
            prefs(TimeFormat.KEY_FORMAT to "HH:mm"), moment, jerusalem
        )
        // 14:05 UTC is 17:05 in Jerusalem in September
        assertEquals("17:05", out)
    }

    @Test
    fun `the custom default is the one the specification names`() {
        assertEquals("yyyy-MM-dd HH:mm EEE -N", TimeFormat.DEFAULT_CUSTOM)
        assertEquals("d MMM yyyy, HH:mm", TimeFormat.DEFAULT_PATTERN)
    }
}
