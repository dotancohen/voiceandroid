package com.dotancohen.voiceandroid.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.dotancohen.voiceandroid.util.UiPreferences

/**
 * How big the whole interface is drawn.
 *
 * Large means everything half again as large: text, icons, buttons and the
 * space around them. It is done by telling Compose that the screen has twice
 * the pixel density it really has, so every measurement in the application
 * doubles at once and nothing is left behind at its old size. A screen laid
 * out this way holds half as much, which is the point: it is for reading and
 * tapping while moving, on a horse or in a vehicle, where the standard size
 * cannot be hit.
 *
 * The setting lives in Settings → Advanced. Its third choice puts a button in
 * the notes toolbar so that the user decides when to switch; the switch
 * applies to every screen, not only the one they were on.
 */
/**
 * The state behind the toolbar's switch, so that pressing it redraws every
 * screen at once. Read from the preference at startup, and written back on
 * every change so the choice survives leaving the app.
 */
class UiSizeState(context: Context) {
    private val prefs = UiPreferences(context)

    /** The setting: standard, large, or standard with a button to switch. */
    var mode: MutableState<String> = mutableStateOf(prefs.uiSizeMode)
        private set

    /** Whether the interface is large right now. */
    var large: MutableState<Boolean> = mutableStateOf(prefs.isLargeNow)
        private set

    /**
     * How much bigger the marks on a note are drawn than they used to be:
     * the recording and transcription marks in the notes list, and the
     * transcribe mark inside a note. A separate setting from the interface
     * size, and it multiplies with it.
     */
    var iconScale: MutableState<Float> = mutableStateOf(prefs.iconScale)
        private set

    /** Whether the notes toolbar should offer the switch. */
    val offersSwitch: Boolean get() = mode.value == UiPreferences.SIZE_TOGGLE

    /** The toolbar button: switch between the two sizes and remember it. */
    fun toggle() {
        if (mode.value != UiPreferences.SIZE_TOGGLE) return
        prefs.toggledLarge = !prefs.toggledLarge
        large.value = prefs.toggledLarge
    }

    /** Read the setting again, after a visit to Settings. */
    fun refresh() {
        mode.value = prefs.uiSizeMode
        large.value = prefs.isLargeNow
        iconScale.value = prefs.iconScale
    }
}

/**
 * Draw everything inside at the chosen size.
 *
 * Scaling the density rather than the text alone keeps the proportions of
 * every screen: a button stays a button with its icon centred, and nothing
 * has to be told about the setting to obey it.
 */
@Composable
fun ScaledUi(large: Boolean, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scale = if (large) UiPreferences.LARGE_SCALE else 1f
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density * scale,
            fontScale = density.fontScale
        ),
        content = content
    )
}

/**
 * How much bigger to draw the marks on a note than they used to be.
 *
 * Provided once around the whole navigation graph, so that a screen deep in
 * the application does not have to be handed the setting to obey it. One
 * means the size these marks have always been.
 */
val LocalIconScale = compositionLocalOf { 1f }

/** A dimension scaled by the icon-size setting. */
@Composable
fun scaledIcon(size: Dp): Dp = size * LocalIconScale.current

/** A text size scaled by the icon-size setting, for a mark drawn as a symbol. */
@Composable
fun scaledIconText(size: TextUnit): TextUnit = size * LocalIconScale.current
