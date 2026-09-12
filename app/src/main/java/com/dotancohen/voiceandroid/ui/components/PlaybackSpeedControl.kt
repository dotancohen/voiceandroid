package com.dotancohen.voiceandroid.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.dotancohen.voiceandroid.audio.PlaybackPreferences
import com.dotancohen.voiceandroid.util.Magic
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Playback speed under a waveform: one slim slider with the marks ½, 1 and 2
 * drawn on it. Tap or drag anywhere; near a mark the value snaps to it. The
 * value is applied to the running player at once, without restarting.
 */
@Composable
fun PlaybackSpeedControl(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val min = PlaybackPreferences.MIN_SPEED
    val max = PlaybackPreferences.MAX_SPEED
    val marks = SPEED_MARKS
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.surfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 30.dp else 36.dp)
    ) {
        Text(
            text = formatSpeed(speed),
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = labelColor,
            modifier = Modifier.width(40.dp)
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(min, max) {
                    val pad = PAD_DP.dp.toPx()
                    detectTapGestures { offset ->
                        onSpeedChange(snapSpeed(valueAtX(offset.x, size.width.toFloat(), pad, min, max)))
                    }
                }
                .pointerInput(min, max) {
                    val pad = PAD_DP.dp.toPx()
                    detectDragGestures { change, _ ->
                        change.consume()
                        onSpeedChange(snapSpeed(valueAtX(change.position.x, size.width.toFloat(), pad, min, max)))
                    }
                }
        ) {
            val pad = PAD_DP.dp.toPx()
            val trackWidth = size.width - 2 * pad
            val cy = size.height * 0.38f
            fun xOf(v: Float) = pad + (v - min) / (max - min) * trackWidth
            val thumbX = xOf(speed)
            // Track: played part in the accent colour, the rest muted
            drawLine(inactive, Offset(pad, cy), Offset(pad + trackWidth, cy), strokeWidth = 4.dp.toPx())
            drawLine(active, Offset(pad, cy), Offset(thumbX, cy), strokeWidth = 4.dp.toPx())
            // Marks with their labels under the track
            for ((mark, label) in marks) {
                val x = xOf(mark)
                drawLine(labelColor, Offset(x, cy - 5.dp.toPx()), Offset(x, cy + 5.dp.toPx()), strokeWidth = 1.5f.dp.toPx())
                val layout = textMeasurer.measure(label, labelStyle)
                drawText(
                    textLayoutResult = layout,
                    color = labelColor,
                    topLeft = Offset(x - layout.size.width / 2f, cy + 6.dp.toPx())
                )
            }
            drawCircle(active, radius = 7.dp.toPx(), center = Offset(thumbX, cy))
        }
    }
}

private const val PAD_DP = 10
/** How close (in speed units) to ½, 1 or 2 the thumb snaps. */
const val SNAP_DISTANCE = Magic.PLAYBACK_SNAP_DISTANCE

/** The marks drawn on the slider, and what each is labelled. */
val SPEED_MARKS = listOf(0.5f to "½", 1f to "1", 2f to "2")

/**
 * The speed a touch means: rounded to a twentieth, and pulled onto ½, 1 or 2
 * when it lands within [SNAP_DISTANCE] of one of them.
 *
 * A finger on a slider a centimetre wide cannot hit 1.00 exactly, and a
 * recording played back at 1.05 sounds wrong in a way that is hard to place,
 * so the common speeds are given a wide target.
 */
fun snapSpeed(
    value: Float,
    min: Float = PlaybackPreferences.MIN_SPEED,
    max: Float = PlaybackPreferences.MAX_SPEED,
): Float {
    val rounded = (value.coerceIn(min, max) * 20f).roundToInt() / 20f
    for ((mark, _) in SPEED_MARKS) if (abs(rounded - mark) <= SNAP_DISTANCE) return mark
    return rounded
}

/** The speed under a touch at [x], along a track [width] wide with [pad] each side. */
fun valueAtX(x: Float, width: Float, pad: Float, min: Float, max: Float): Float {
    val fraction = ((x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)
    return min + fraction * (max - min)
}

fun formatSpeed(speed: Float): String {
    val text = String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')
    return "$text×"
}
