package com.dotancohen.voiceandroid.util

/**
 * The tuned numbers of the application, in one place.
 *
 * These are the values that were chosen by looking at a screen rather than
 * derived from anything: how much larger "large" is, how much smaller the
 * time of day is drawn than the date beside it, how long a flash lasts. They
 * are gathered here so that a value can be found and changed without reading
 * the code that uses it, and so that the same number is never written twice
 * in two files that then drift apart.
 *
 * A number belongs here when it could reasonably be argued with. A number
 * fixed by something outside the application does not: the 44 bytes of a WAV
 * header, Whisper's 16 kHz, the 1..8 range a beam search accepts, and the
 * sizes of the model files are facts, and they live beside the code that
 * depends on them.
 */
object Magic {

    // ---- Sizes ------------------------------------------------------------

    /**
     * How much bigger the large interface is.
     *
     * One and a half, not double: at double, a phone holds so little that a
     * note becomes a column of two or three words.
     */
    const val LARGE_UI_SCALE = 1.5f

    /**
     * How much bigger each step of the icon-size setting is than the one
     * before it, so Medium is this and Large is this squared.
     */
    const val ICON_SIZE_STEP = 1.5f

    /**
     * How many points smaller the time of day is drawn than the date it
     * follows.
     *
     * A timestamp is read for its date far more often than for its minute,
     * and the two run together as one line; drawing the time a little
     * smaller lets the eye find the date first without either being hidden.
     */
    const val TIME_FONT_DELTA_SP = -2f

    /**
     * The width of one button in a note's toolbar, in dp.
     *
     * Smaller than Android's usual 48: the note screen's toolbar carries
     * seven buttons and a date, and at the usual width they push the date
     * off the screen. Still large enough to hit, since they sit in a row
     * with nothing else near them.
     */
    const val TOOLBAR_BUTTON_DP = 34

    /** The width of the star and tags buttons on a note's tags line, in dp. */
    const val NOTE_LINE_BUTTON_DP = 28

    // ---- The notes list ---------------------------------------------------

    /** Lines of a note's text, and of its transcription, that a row shows. */
    const val DEFAULT_LIST_LINES = 1

    /** Six lines of text and six of transcription already fill a phone. */
    const val MAX_LIST_LINES = 6

    /**
     * How many lines of a transcription a row keeps in memory. A preview
     * shows twice what a row shows, and the row can show [MAX_LIST_LINES].
     */
    const val TRANSCRIPTION_PREVIEW_LINES = 12

    /** How long the row of the note you just left is pointed out, in ms. */
    const val DEFAULT_SPOTLIGHT_MS = 500

    /** Longer than this and the flash is in the way rather than a help. */
    const val MAX_SPOTLIGHT_MS = 1000

    // ---- Playback ---------------------------------------------------------

    /** Slower than this and speech is no longer speech. */
    const val MIN_PLAYBACK_SPEED = 0.5f

    /** Faster than this and even a familiar voice cannot be followed. */
    const val MAX_PLAYBACK_SPEED = 3.0f

    /**
     * How close to ½, 1 or 2 the playback slider has to come before it snaps
     * to that mark. A finger on a slider a centimetre wide cannot hit 1.00.
     */
    const val PLAYBACK_SNAP_DISTANCE = 0.12f

    // ---- Long recordings --------------------------------------------------

    /**
     * How long a recording may be before its waveform is drawn only when
     * asked for, in seconds.
     *
     * Drawing a waveform means decoding the whole recording. A phone manages
     * perhaps fifty to two hundred times real time through MediaCodec, so an
     * hour of audio is tens of seconds of work and one of the phone's three
     * hardware decoders held for all of it. Past this length the user is asked
     * first, and the answer is kept, so the waiting happens once.
     *
     * An hour covers every voice note and most meetings. A recording of
     * somebody sleeping is eight hours and has nothing to look at anyway.
     */
    const val LONG_RECORDING_SECONDS = 60 * 60

    /**
     * How large a recording may be before the same applies, in bytes.
     *
     * The duration is the better measure — the work tracks the length of the
     * audio, not its size — so this is the guard for when the duration is not
     * known yet or the file's header is wrong. 100 MiB is about 52 minutes of
     * 16 kHz WAV, 1.8 hours of Opus at 128 kb/s, or 2.4 hours of AAC at 96.
     */
    const val LARGE_RECORDING_BYTES = 100L * 1024 * 1024

    /**
     * The longest recording this phone will transcribe, in seconds.
     *
     * Whisper holds the whole recording in memory while it works — roughly
     * 230 MB an hour on top of a model of one to three gigabytes — and a
     * phone has neither the memory nor the patience for a long one. Ten
     * minutes covers the voice notes this application is for; a recording past
     * it is transcribed on the desktop, which has the memory, once the two
     * have synced.
     */
    const val TRANSCRIBE_MAX_SECONDS_ON_PHONE = 10 * 60

    // ---- Recording and drawing --------------------------------------------

    /** Bars drawn in a waveform, whatever the length of the recording. */
    const val WAVEFORM_BARS = 150

    /** How many levels the recorder's live waveform keeps on screen. */
    const val RECORDER_WAVEFORM_SAMPLES = 120
}
