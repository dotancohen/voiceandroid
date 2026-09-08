package com.dotancohen.voiceandroid.transcription

import android.content.Context

/**
 * On-device transcription settings kept on the phone (not synced): which
 * downloaded model to use, the language to assume, and greedy versus beam
 * search decoding.
 */
class TranscriptionPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("voice_settings", Context.MODE_PRIVATE)

    /** Id of the model in [WhisperModels.CATALOGUE] to transcribe with. */
    var modelId: String
        get() = prefs.getString(KEY_MODEL, WhisperModels.DEFAULT_MODEL_ID) ?: WhisperModels.DEFAULT_MODEL_ID
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    /** ISO 639-1 code, or [LANGUAGE_AUTO] to let the model detect the language. */
    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "he") ?: "he"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    /** Beam search width; 1 = greedy. 5 is the accurate setting. */
    var beamSize: Int
        get() = prefs.getInt(KEY_BEAM, 5).coerceIn(1, 8)
        set(value) = prefs.edit().putInt(KEY_BEAM, value.coerceIn(1, 8)).apply()

    companion object {
        private const val KEY_MODEL = "transcription_model"
        private const val KEY_LANGUAGE = "transcription_language"
        private const val KEY_BEAM = "transcription_beam_size"
        const val LANGUAGE_AUTO = "auto"

        /** Languages offered in the settings, in the order the user cares about. */
        val LANGUAGES: List<Pair<String, String>> = listOf(
            "he" to "Hebrew (עברית)",
            "en" to "English",
            "ar" to "Arabic (العربية)",
            "ru" to "Russian (Русский)",
            LANGUAGE_AUTO to "Detect automatically",
        )

        fun languageTitle(code: String): String = LANGUAGES.firstOrNull { it.first == code }?.second ?: code
    }
}
