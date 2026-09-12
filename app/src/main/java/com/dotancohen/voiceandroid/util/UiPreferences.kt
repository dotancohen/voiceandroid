package com.dotancohen.voiceandroid.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Small interface settings kept on this device (not synced).
 */
class UiPreferences internal constructor(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(
        context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)
    )

    /**
     * How long the row of the note you just left flashes in the notes list,
     * in milliseconds. 0 turns the flash off; the most that can be set is one
     * second.
     */
    var spotlightDurationMs: Int
        get() = prefs.getInt(KEY_SPOTLIGHT, DEFAULT_SPOTLIGHT_MS).coerceIn(0, MAX_SPOTLIGHT_MS)
        set(value) = prefs.edit().putInt(KEY_SPOTLIGHT, value.coerceIn(0, MAX_SPOTLIGHT_MS)).apply()

    /**
     * How many lines of the note's text, and of its transcription, each row
     * of the notes list shows. One each by default. A preview of a note
     * (long-press on Next or Previous in a note) shows twice as many, so
     * that it says more than the row the user is already looking at.
     */
    var notesListLines: Int
        get() = prefs.getInt(KEY_LIST_LINES, DEFAULT_LIST_LINES).coerceIn(1, MAX_LIST_LINES)
        set(value) = prefs.edit().putInt(KEY_LIST_LINES, value.coerceIn(1, MAX_LIST_LINES)).apply()

    /**
     * How big the interface is drawn: [SIZE_STANDARD], [SIZE_LARGE], or
     * [SIZE_TOGGLE], which puts a button in the notes toolbar and lets the
     * user switch between the two whenever they like.
     */
    var uiSizeMode: String
        get() = prefs.getString(KEY_UI_SIZE, SIZE_STANDARD)?.takeIf { it in UI_SIZES } ?: SIZE_STANDARD
        set(value) = prefs.edit().putString(KEY_UI_SIZE, value.takeIf { it in UI_SIZES } ?: SIZE_STANDARD).apply()

    /**
     * Whether the toggle is currently on large, when the mode is
     * [SIZE_TOGGLE]. Kept so that the choice survives leaving the app.
     */
    var toggledLarge: Boolean
        get() = prefs.getBoolean(KEY_TOGGLED_LARGE, false)
        set(value) = prefs.edit().putBoolean(KEY_TOGGLED_LARGE, value).apply()

    /** Whether everything should be drawn at double size right now. */
    val isLargeNow: Boolean
        get() = isLargeFor(uiSizeMode, toggledLarge)

    /**
     * How big the marks on a note are drawn: [ICONS_SMALL], [ICONS_MEDIUM]
     * or [ICONS_LARGE].
     *
     * These are the recording and transcription marks in the notes list and
     * the transcribe mark inside a note — small, tappable things that say
     * what a note holds. They are sized apart from the interface as a whole
     * because a person who can read the text at the standard size may still
     * want the marks big enough to see at a glance, or to hit while moving.
     */
    var iconSize: String
        get() = prefs.getString(KEY_ICON_SIZE, ICONS_SMALL)?.takeIf { it in ICON_SIZES } ?: ICONS_SMALL
        set(value) = prefs.edit().putString(KEY_ICON_SIZE, value.takeIf { it in ICON_SIZES } ?: ICONS_SMALL).apply()

    /** What to multiply an icon's size by, for the chosen [iconSize]. */
    val iconScale: Float get() = iconScaleFor(iconSize)

    /**
     * Whether Tags are drawn in their own colours in the Notes list.
     *
     * On by default: the colours are what a Note is recognised by before its
     * words are read. Off draws them as plain text, for a list that should be
     * quiet or a screen that renders colour poorly.
     */
    var colouredTags: Boolean
        get() = prefs.getBoolean(KEY_COLOURED_TAGS, true)
        set(value) = prefs.edit().putBoolean(KEY_COLOURED_TAGS, value).apply()

    /**
     * Whether opening a note starts playing its main recording at once.
     *
     * Off by default: a note opened in a quiet room, or in company, should
     * not begin talking on its own unless the user asked for that.
     */
    var autoplayOnOpen: Boolean
        get() = prefs.getBoolean(KEY_AUTOPLAY, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTOPLAY, value).apply()

    /**
     * The chosen time format: one of [TimeFormat.PRESETS]' patterns, the
     * sentinel [TimeFormat.CUSTOM], or null while the user has not chosen
     * and the phone's own format is used.
     */
    var timeFormat: String?
        get() = prefs.getString(TimeFormat.KEY_FORMAT, null)
        set(value) = prefs.edit().putString(TimeFormat.KEY_FORMAT, value).apply()

    /** The free-form pattern used when the choice is Custom. */
    var timeFormatCustom: String
        get() = prefs.getString(TimeFormat.KEY_CUSTOM, TimeFormat.DEFAULT_CUSTOM) ?: TimeFormat.DEFAULT_CUSTOM
        set(value) = prefs.edit().putString(TimeFormat.KEY_CUSTOM, value).apply()

    companion object {
        /** Every setting the phone keeps for itself lives in this one file. */
        const val SETTINGS_FILE = "voice_settings"
        private const val KEY_SPOTLIGHT = "spotlight_duration_ms"
        private const val KEY_AUTOPLAY = "autoplay_on_open"
        private const val KEY_UI_SIZE = "ui_size_mode"
        private const val KEY_TOGGLED_LARGE = "ui_size_toggled_large"

        /** The size the interface has always been. */
        const val SIZE_STANDARD = "standard"
        /**
         * Everything twice the size: text, icons and the space around them.
         * For reading and tapping while moving, on a horse or in a vehicle,
         * where a standard interface is too small to hit.
         */
        const val SIZE_LARGE = "large"
        /**
         * Standard, with a button in the notes toolbar that switches to
         * large and back. The switch applies to every screen, not only the
         * one it is pressed on.
         */
        const val SIZE_TOGGLE = "toggle"
        val UI_SIZES = listOf(SIZE_STANDARD, SIZE_LARGE, SIZE_TOGGLE)

        /**
         * How much bigger "large" is.
         *
         * One and a half, not double: at double, a phone holds so little
         * that a note becomes a column of two or three words.
         */
        const val LARGE_SCALE = Magic.LARGE_UI_SCALE

        /**
         * Whether the interface is large, given the setting and the state of
         * the toolbar switch.
         *
         * The switch is only consulted in [SIZE_TOGGLE]: a user who has
         * chosen Standard or Large gets that size whatever the switch was
         * last left at, so that changing the setting is not undone by a
         * button pressed days ago.
         */
        fun isLargeFor(mode: String, toggled: Boolean): Boolean = when (mode) {
            SIZE_LARGE -> true
            SIZE_TOGGLE -> toggled
            else -> false
        }

        fun uiSizeTitle(mode: String): String = when (mode) {
            SIZE_LARGE -> "Large"
            SIZE_TOGGLE -> "Standard, with a button to switch"
            else -> "Standard"
        }

        fun uiSizeDescription(mode: String): String = when (mode) {
            SIZE_LARGE -> "Every text and every icon half again as large. Less fits on the screen."
            SIZE_TOGGLE -> "Starts standard. A button in the notes toolbar switches to large and back, on every screen."
            else -> "The usual size."
        }
        private const val KEY_ICON_SIZE = "icon_size"
        private const val KEY_COLOURED_TAGS = "coloured_tags"

        /** The size the marks have always been. */
        const val ICONS_SMALL = "small"
        /** Half again as large. */
        const val ICONS_MEDIUM = "medium"
        /** Half again as large as medium, so a bit over twice the small one. */
        const val ICONS_LARGE = "large"
        val ICON_SIZES = listOf(ICONS_SMALL, ICONS_MEDIUM, ICONS_LARGE)

        /** Each step up is half as large again as the one before it. */
        const val ICON_STEP = Magic.ICON_SIZE_STEP

        fun iconScaleFor(size: String): Float = when (size) {
            ICONS_MEDIUM -> ICON_STEP
            ICONS_LARGE -> ICON_STEP * ICON_STEP
            else -> 1f
        }

        fun iconSizeTitle(size: String): String = when (size) {
            ICONS_MEDIUM -> "Medium"
            ICONS_LARGE -> "Large"
            else -> "Small"
        }

        fun iconSizeDescription(size: String): String = when (size) {
            ICONS_MEDIUM -> "Half again as large as small."
            ICONS_LARGE -> "Half again as large as medium."
            else -> "The usual size."
        }

        private const val KEY_LIST_LINES = "notes_list_lines"
        const val DEFAULT_LIST_LINES = Magic.DEFAULT_LIST_LINES
        /** Six lines of text and six of transcription already fill a phone. */
        const val MAX_LIST_LINES = Magic.MAX_LIST_LINES
        const val DEFAULT_SPOTLIGHT_MS = Magic.DEFAULT_SPOTLIGHT_MS
        const val MAX_SPOTLIGHT_MS = Magic.MAX_SPOTLIGHT_MS
    }
}
