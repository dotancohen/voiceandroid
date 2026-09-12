package com.dotancohen.voiceandroid.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Transcribe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A transcription that has been asked for and has not arrived: the
 * transcribe mark with a clock turning over its lower corner.
 *
 * It says two things at once, which is why it is one icon and not two: this
 * recording is being transcribed (the mark), and it is not finished yet (the
 * clock, whose hand moves so that a glance tells a waiting transcription
 * from a finished one without reading anything).
 *
 * The same icon is used in the notes list, on the recording's button, and in
 * the note itself beside the pending row.
 */
@Composable
fun PendingTranscriptionIcon(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    /** Drawn behind the clock so it reads as covering the mark, not merged into it. */
    background: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val turn = rememberInfiniteTransition(label = "pending transcription")
    val angle by turn.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "clock hand"
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Filled.Transcribe,
            contentDescription = null,
            modifier = Modifier.size(size),
            tint = tint
        )
        // Half over the mark, at the corner, so the mark is still readable.
        Icon(
            imageVector = Icons.Filled.Schedule,
            contentDescription = "waiting to be transcribed",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = size / 4, y = size / 4)
                .size(size * 0.7f)
                .background(background, CircleShape)
                .rotate(angle),
            tint = tint
        )
    }
}
