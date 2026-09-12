package com.dotancohen.voiceandroid.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import com.dotancohen.voiceandroid.util.Magic
import com.dotancohen.voiceandroid.util.parts
import uniffi.voicecore.Stamp

/**
 * A date and time as one piece of text, with everything that is not the date
 * itself drawn a little smaller: the time of day, the name of the weekday,
 * the days-ago count.
 *
 * A timestamp is read for its date far more often than for its minute, and it
 * is all one line; the smaller remainder lets the eye find the date first
 * without hiding anything. How much smaller is [Magic.TIME_FONT_DELTA_SP],
 * with the other tuned numbers of the application.
 *
 * Which part of the text is the date comes from the chosen format itself, so
 * this works for every preset and for a free-form pattern, in whatever order
 * that pattern puts them.
 */
@Composable
fun stampText(
    stamp: Stamp,
    /** The size the date is drawn at; the time is drawn smaller than this. */
    baseSize: TextUnit = LocalTextStyle.current.fontSize,
    /** Text put in front of the date, e.g. "Deleted ". */
    prefix: String = "",
): AnnotatedString {
    val parts = stamp.parts(LocalContext.current)
    val timeSize = if (baseSize.isUnspecified) {
        TextUnit.Unspecified
    } else {
        (baseSize.value + Magic.TIME_FONT_DELTA_SP).coerceAtLeast(1f).sp
    }
    return buildAnnotatedString {
        append(prefix)
        for (part in parts) {
            if (part.isSmall && timeSize != TextUnit.Unspecified) {
                withStyle(SpanStyle(fontSize = timeSize)) { append(part.text) }
            } else {
                append(part.text)
            }
        }
    }
}
