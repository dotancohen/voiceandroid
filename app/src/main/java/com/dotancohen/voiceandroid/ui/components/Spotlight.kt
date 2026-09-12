package com.dotancohen.voiceandroid.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/** Bright, but clearly not white, for the light theme. */
private val SpotlightWarm = Color(0xFFFFEFA6)

/**
 * Colour of the two dots that sweep across the row of a note the user has
 * just left. In the light theme they are a bright warm dot; in the dark theme
 * only slightly lighter than the row itself, so a dark room stays dark.
 */
@Composable
fun spotlightColor(rowBackground: Color): Color =
    if (isSystemInDarkTheme()) lerp(rowBackground, Color.White, 0.12f) else SpotlightWarm

/**
 * Two dots that start in the middle of the row and travel out to its edges as
 * [progress] runs from 0 to 1, fading away at the end. A null progress draws
 * nothing, so a row that is not being pointed out costs nothing.
 */
fun Modifier.spotlight(progress: Float?, color: Color): Modifier =
    if (progress == null) this else drawWithContent {
        drawContent()
        val p = progress.coerceIn(0f, 1f)
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = minOf(size.height * 0.30f, 16.dp.toPx())
        // Solid while they travel, gone by the time they reach the edges
        val alpha = if (p < 0.70f) 1f else ((1f - p) / 0.30f).coerceIn(0f, 1f)
        val dot = color.copy(alpha = color.alpha * alpha)
        val travel = (centerX + radius) * p
        drawCircle(dot, radius, Offset(centerX - travel, centerY))
        drawCircle(dot, radius, Offset(centerX + travel, centerY))
    }
