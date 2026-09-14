package com.dotancohen.voiceandroid.util

/**
 * The words for where this phone's listener can be reached (LISTEN-4), the same
 * as the desktop's `src/core/addresses_text.py`: the address found through the
 * phone's route; or every candidate followed by the sentence saying that only
 * one of them is correct; or the sentence that no address was found.
 */
object AddressText {
    fun words(detected: Boolean, shown: List<String>, sentence: String): String = when {
        shown.isEmpty() -> sentence
        detected -> shown.first()
        else -> "${shown.joinToString(", ")}. $sentence".trim()
    }
}
