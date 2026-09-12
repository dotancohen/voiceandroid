package com.dotancohen.voiceandroid.transcription

import android.content.Context
import android.content.SharedPreferences
import com.dotancohen.voiceandroid.util.UiPreferences

/**
 * On-device transcription settings kept on the phone (not synced): which
 * downloaded model to use, the language to assume, and greedy versus beam
 * search decoding.
 */
class TranscriptionPreferences internal constructor(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(
        context.getSharedPreferences(UiPreferences.SETTINGS_FILE, Context.MODE_PRIVATE)
    )

    /** Id of the model in [WhisperModels.CATALOGUE] to transcribe with. */
    var modelId: String
        get() = prefs.getString(KEY_MODEL, WhisperModels.DEFAULT_MODEL_ID) ?: WhisperModels.DEFAULT_MODEL_ID
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    /** ISO 639-1 code, or [LANGUAGE_AUTO] to let the model detect the language. */
    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "he") ?: "he"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    /**
     * What a language is offered as: [USE_OFF], [USE_AVAILABLE] or
     * [USE_BUTTON].
     */
    fun languageUse(code: String): String {
        val stored = prefs.getString(KEY_LANGUAGE_USE_PREFIX + code, null)
        return stored?.takeIf { it in USES } ?: DEFAULT_USES[code] ?: USE_OFF
    }

    fun setLanguageUse(code: String, use: String) {
        prefs.edit().putString(KEY_LANGUAGE_USE_PREFIX + code, use.takeIf { it in USES } ?: USE_OFF).apply()
    }

    /** The languages with a button of their own, in catalogue order. */
    fun buttonLanguages(): List<Pair<String, String>> =
        LANGUAGE_CATALOGUE.filter { languageUse(it.first) == USE_BUTTON }

    /**
     * The languages offered in the list: everything not switched off.
     *
     * The ones with buttons are in it too, so that the list is the whole
     * answer to "what else can I choose", and the current language is always
     * in it even when it has since been switched off, or the box would show
     * a language it does not contain.
     */
    fun selectableLanguages(): List<Pair<String, String>> {
        val chosen = language
        return LANGUAGE_CATALOGUE.filter {
            languageUse(it.first) != USE_OFF || it.first == chosen
        }
    }

    /** Beam search width; 1 = greedy. 5 is the accurate setting. */
    var beamSize: Int
        get() = prefs.getInt(KEY_BEAM, 5).coerceIn(1, 8)
        set(value) = prefs.edit().putInt(KEY_BEAM, value.coerceIn(1, 8)).apply()

    companion object {
        private const val KEY_MODEL = "transcription_model"
        private const val KEY_LANGUAGE = "transcription_language"
        private const val KEY_BEAM = "transcription_beam_size"
        private const val KEY_LANGUAGE_USE_PREFIX = "transcription_language_use_"
        const val LANGUAGE_AUTO = "auto"

        /**
         * Every language this can be told to transcribe in, with the name in
         * English and in the language itself.
         *
         * Whisper knows about a hundred; these are the ones worth offering
         * in a list a person reads. Which of them appear, and where, is the
         * user's choice ([languageUse]), not a decision made here: the four
         * that used to be written into this file were the author's, not
         * everybody's.
         */
        val LANGUAGE_CATALOGUE: List<Pair<String, String>> = listOf(
            LANGUAGE_AUTO to "Detect automatically",
            "he" to "Hebrew (עברית)",
            "en" to "English",
            "ar" to "Arabic (العربية)",
            "ru" to "Russian (Русский)",
            "es" to "Spanish (Español)",
            "el" to "Greek (Ελληνικά)",
            "fr" to "French (Français)",
            "de" to "German (Deutsch)",
            "it" to "Italian (Italiano)",
            "pt" to "Portuguese (Português)",
            "nl" to "Dutch (Nederlands)",
            "pl" to "Polish (Polski)",
            "uk" to "Ukrainian (Українська)",
            "ro" to "Romanian (Română)",
            "tr" to "Turkish (Türkçe)",
            "fa" to "Persian (فارسی)",
            "hi" to "Hindi (हिन्दी)",
            "ur" to "Urdu (اردو)",
            "am" to "Amharic (አማርኛ)",
            "yi" to "Yiddish (ייִדיש)",
            "zh" to "Chinese (中文)",
            "ja" to "Japanese (日本語)",
            "ko" to "Korean (한국어)",
            "vi" to "Vietnamese (Tiếng Việt)",
            "th" to "Thai (ไทย)",
            "id" to "Indonesian (Bahasa Indonesia)",
            "sw" to "Swahili (Kiswahili)",
            "cs" to "Czech (Čeština)",
            "hu" to "Hungarian (Magyar)",
            "sv" to "Swedish (Svenska)",
            "no" to "Norwegian (Norsk)",
            "da" to "Danish (Dansk)",
            "fi" to "Finnish (Suomi)",
            "bg" to "Bulgarian (Български)",
            "sr" to "Serbian (Српски)",
            "hr" to "Croatian (Hrvatski)",
            "ca" to "Catalan (Català)",
            "hy" to "Armenian (Հայերեն)",
            "ka" to "Georgian (ქართული)",
            "az" to "Azerbaijani (Azərbaycan)",
            "kk" to "Kazakh (Қазақша)",
            "ta" to "Tamil (தமிழ்)",
            "bn" to "Bengali (বাংলা)",
            "ml" to "Malayalam (മലയാളം)",
            "ms" to "Malay (Bahasa Melayu)",
            "tl" to "Tagalog",
            "la" to "Latin",
        )

        /** What a language is offered as. */
        const val USE_OFF = "off"
        /** In the list of other languages, behind one tap. */
        const val USE_AVAILABLE = "available"
        /** A button of its own in the Transcribe dialog. */
        const val USE_BUTTON = "button"
        val USES = listOf(USE_OFF, USE_AVAILABLE, USE_BUTTON)

        fun useTitle(use: String): String = when (use) {
            USE_BUTTON -> "Button"
            USE_AVAILABLE -> "In the list"
            else -> "Not used"
        }

        /**
         * What each language is offered as until the user says otherwise.
         *
         * Anything not named here is off. These defaults are only a starting
         * point; every one of them can be changed in Settings →
         * Transcription → Languages.
         */
        val DEFAULT_USES: Map<String, String> = mapOf(
            "he" to USE_BUTTON,
            "en" to USE_BUTTON,
            LANGUAGE_AUTO to USE_BUTTON,
            "ar" to USE_AVAILABLE,
            "ru" to USE_AVAILABLE,
            "es" to USE_AVAILABLE,
            "el" to USE_AVAILABLE,
        )

        fun languageTitle(code: String): String =
            LANGUAGE_CATALOGUE.firstOrNull { it.first == code }?.second ?: code
    }
}
