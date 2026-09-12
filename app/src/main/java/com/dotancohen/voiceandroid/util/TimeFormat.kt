package com.dotancohen.voiceandroid.util

import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Formats timestamps using the pattern chosen in Advanced settings — either
 * one of the preset SimpleDateFormat patterns or the free-form custom
 * pattern, which additionally supports the token `-N`: the number of whole
 * days ago (e.g. "yyyy-MM-dd HH:mm EEE -N" → "2026-09-05 14:03 Sat -2").
 *
 * Copied from SSIA's specification, with one addition: an optional
 * [TimeZone], because a timestamp in this application carries the offset it
 * was written in and must be shown as the clock read there.
 */
object TimeFormat {

    const val DEFAULT_PATTERN = "d MMM yyyy, HH:mm"
    const val DEFAULT_CUSTOM = "yyyy-MM-dd HH:mm EEE -N"
    private const val MARKER = "\uE000" // private-use placeholder

    /**
     * The preset choices: what the user sees, and the pattern behind it.
     *
     * Every example depicts the same instant — Thursday 3 September 2026 at
     * 14:05 — so that the twelve differ only in their formatting and can be
     * compared at a glance. Keep that if the list is ever edited. (The
     * specification this was copied from called that day a Wednesday, which
     * it is not; the two examples that name the day were corrected.)
     */
    val PRESETS: List<Pair<String, String>> = listOf(
        "3 Sep 2026, 14:05" to "d MMM yyyy, HH:mm",
        "2026-09-03 14:05" to "yyyy-MM-dd HH:mm",
        "03/09/2026 14:05" to "dd/MM/yyyy HH:mm",
        "09/03/2026 2:05 PM" to "MM/dd/yyyy h:mm a",
        "03.09.2026 14:05" to "dd.MM.yyyy HH:mm",
        "3 Sep, 14:05" to "d MMM, HH:mm",
        "Thu 3 Sep, 14:05" to "EEE d MMM, HH:mm",
        "Thursday, 3 September 2026, 14:05" to "EEEE, d MMMM yyyy, HH:mm",
        "2:05 PM, Sep 3" to "h:mm a, MMM d",
        "2026-09-03" to "yyyy-MM-dd",
        "3 September 2026" to "d MMMM yyyy",
        "14:05" to "HH:mm",
        "Custom (Advanced date format)" to CUSTOM,
    )

    /** The key in the preferences, and the sentinel meaning "use the pattern below". */
    const val KEY_FORMAT = "time_format"
    const val KEY_CUSTOM = "time_format_custom"
    const val CUSTOM = "custom"

    /**
     * One piece of a rendered timestamp, and whether it is drawn small.
     *
     * The date itself — the day, the month, the year — is what a timestamp is
     * read for, and it is drawn at the full size. Everything else is drawn a
     * little smaller: the time of day, the name of the weekday, the days-ago
     * count. The pattern is what says which is which, so this works for a
     * free-form pattern in whatever order it puts them.
     *
     * The punctuation between two fields belongs to whichever came before it.
     */
    data class Part(val text: String, val isSmall: Boolean) {
        @Deprecated("Renamed: the weekday and the days-ago count are small too", ReplaceWith("isSmall"))
        val isTime: Boolean get() = isSmall
    }

    fun format(prefs: SharedPreferences, timeMs: Long, zone: TimeZone = TimeZone.getDefault()): String =
        parts(prefs, timeMs, zone).joinToString("") { it.text }

    /** The same rendering as [format], split into full-size and small pieces. */
    fun parts(
        prefs: SharedPreferences,
        timeMs: Long,
        zone: TimeZone = TimeZone.getDefault(),
    ): List<Part> {
        val selected = prefs.getString(KEY_FORMAT, DEFAULT_PATTERN) ?: DEFAULT_PATTERN
        val pattern =
            if (selected == CUSTOM)
                prefs.getString(KEY_CUSTOM, DEFAULT_CUSTOM) ?: DEFAULT_CUSTOM
            else selected
        return try {
            patternParts(pattern).map { (segment, isSmall) ->
                Part(formatPattern(segment, timeMs, zone), isSmall)
            }
        } catch (_: Exception) {
            // A pattern the formatter will not take falls back to the
            // default, rather than leaving the screen with no date on it.
            patternParts(DEFAULT_PATTERN).map { (segment, isSmall) ->
                Part(formatPattern(segment, timeMs, zone), isSmall)
            }
        }
    }

    /**
     * The pattern split into runs that are all date or all time.
     *
     * The letters in [SMALL_LETTERS] are drawn small; the rest are the date.
     * Text between two runs joins the run before it, so "d MMM yyyy, HH:mm"
     * divides after the comma and the space. Quoted text is a literal and
     * does the same; the `-N` token is drawn small.
     */
    internal fun patternParts(pattern: String): List<Pair<String, Boolean>> {
        val tokens = mutableListOf<Pair<String, Boolean?>>() // text, time?  null = literal
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '\'' -> {
                    // A quoted literal, or '' for a single quote
                    val end = pattern.indexOf('\'', i + 1)
                    val stop = if (end < 0) pattern.length else end + 1
                    tokens.add(pattern.substring(i, stop) to null)
                    i = stop
                }
                c == '-' && i + 1 < pattern.length && pattern[i + 1] == 'N' -> {
                    // Days ago: a note on the date, drawn small like the time
                    tokens.add("-N" to true)
                    i += 2
                }
                c.isLetter() -> {
                    var j = i
                    while (j < pattern.length && pattern[j] == c) j++
                    tokens.add(pattern.substring(i, j) to (c in SMALL_LETTERS))
                    i = j
                }
                else -> {
                    var j = i
                    while (j < pattern.length && !pattern[j].isLetter() && pattern[j] != '\'' &&
                        !(pattern[j] == '-' && j + 1 < pattern.length && pattern[j + 1] == 'N')
                    ) j++
                    tokens.add(pattern.substring(i, j) to null)
                    i = j
                }
            }
        }

        // Literals take the side of what came before them, or of what comes
        // after when they open the pattern.
        val parts = mutableListOf<Pair<StringBuilder, Boolean>>()
        var pending = StringBuilder()
        for ((index, token) in tokens.withIndex()) {
            val (text, isTime) = token
            if (isTime == null) {
                if (parts.isEmpty()) pending.append(text) else parts.last().first.append(text)
                continue
            }
            if (parts.isNotEmpty() && parts.last().second == isTime) {
                parts.last().first.append(text)
            } else {
                val builder = StringBuilder()
                if (parts.isEmpty()) {
                    builder.append(pending)
                    pending = StringBuilder()
                }
                builder.append(text)
                parts.add(builder to isTime)
            }
            @Suppress("UNUSED_EXPRESSION") index
        }
        if (parts.isEmpty() && pending.isNotEmpty()) return listOf(pending.toString() to false)
        return parts.map { it.first.toString() to it.second }
    }

    /**
     * Pattern letters drawn smaller than the date.
     *
     * The time of day (`H k K h m s S a`), the zone (`z Z X`), and the name
     * of the day (`E c`), which is a label on the date rather than the date.
     * The `-N` days-ago token joins them, in [patternParts].
     */
    private val SMALL_LETTERS = setOf(
        'H', 'k', 'K', 'h', 'm', 's', 'S', 'a', 'A', 'z', 'Z', 'X', 'x', 'E', 'c',
    )

    private fun formatPattern(pattern: String, timeMs: Long, zone: TimeZone): String {
        val quoted = pattern.replace("-N", "'$MARKER'")
        var out = SimpleDateFormat(quoted, Locale.getDefault())
            .apply { timeZone = zone }
            .format(Date(timeMs))
        if (out.contains(MARKER)) {
            // LTR-isolate the "-N" so the minus stays left of the number in
            // RTL locales (U+2066 LRI … U+2069 PDI)
            out = out.replace(MARKER, "\u2066-" + daysAgo(timeMs, zone) + "\u2069")
        }
        return out
    }

    /** Whole calendar days between the timestamp's date and today. */
    private fun daysAgo(timeMs: Long, zone: TimeZone): Long {
        fun midnight(ms: Long, tz: TimeZone): Long = Calendar.getInstance(tz).apply {
            timeInMillis = ms
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return ((midnight(System.currentTimeMillis(), TimeZone.getDefault()) - midnight(timeMs, zone)) /
                86_400_000L).coerceAtLeast(0)
    }
}
