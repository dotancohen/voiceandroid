package com.dotancohen.voiceandroid.util

import android.content.Context
import android.text.format.DateFormat
import uniffi.voicecore.Stamp
import java.time.ZoneOffset
import java.util.Date
import java.util.TimeZone

/**
 * Showing a time as the clock read where it happened.
 *
 * The core stores an instant together with the offset from UTC that was in
 * force on the device that wrote it, so a note recorded at 15:20 in Jerusalem
 * still reads 15:20 after its author flies to New York. Only the phone knows
 * the locale and whether the user wants a 12 or 24-hour clock, so the drawing
 * happens here.
 *
 * A stamp with no offset, written before these were recorded or by a device
 * that never reported one, is shown in this phone's own timezone.
 */
fun Stamp.timeZone(): TimeZone =
    offset
        // An offset that cannot be true, from a corrupted row, is treated as
        // unknown rather than throwing: this phone's clock is the honest answer
        ?.let { seconds -> runCatching { ZoneOffset.ofTotalSeconds(seconds) }.getOrNull() }
        ?.let { TimeZone.getTimeZone(it) }
        ?: TimeZone.getDefault()

/** Milliseconds since the epoch, for anything that wants a [Date]. */
val Stamp.epochMillis: Long get() = at * 1000L

/**
 * Date and time, in the format chosen in Settings → Advanced, or the
 * phone's own when that is left alone.
 */
fun Stamp.format(context: Context): String =
    parts(context).joinToString("") { it.text }

/**
 * The same as [format], split into the date and everything else, so that the
 * time of day, the weekday and the days-ago count can be drawn a little
 * smaller than the date itself.
 */
fun Stamp.parts(context: Context): List<TimeFormat.Part> {
    val prefs = context.getSharedPreferences(UiPreferences.SETTINGS_FILE, Context.MODE_PRIVATE)
    if (prefs.getString(TimeFormat.KEY_FORMAT, null) != null) {
        return TimeFormat.parts(prefs, epochMillis, timeZone())
    }
    // One line, with a single space between the day and the time. If the
    // width cannot hold it, the break falls here, before the time of day,
    // because a date split across lines is unreadable and a time is not.
    return listOf(
        TimeFormat.Part("${formatDate(context)} ", isSmall = false),
        TimeFormat.Part(formatTime(context), isSmall = true),
    )
}

/** Just the day, in the phone's own format. */
fun Stamp.formatDate(context: Context): String {
    val format = DateFormat.getDateFormat(context)
    format.timeZone = timeZone()
    return format.format(Date(epochMillis))
}

/** Just the time of day, in the phone's own format and clock. */
fun Stamp.formatTime(context: Context): String {
    val format = DateFormat.getTimeFormat(context)
    format.timeZone = timeZone()
    return format.format(Date(epochMillis))
}

