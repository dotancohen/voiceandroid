package com.dotancohen.voiceandroid.data

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * What the pairing screen was asked to do from outside it (Stage 9): a
 * setup text that arrived as a `voice://pair` link, or the first-run
 * screen's "Pair with another device", which opens the camera. The sync
 * screen consumes each and clears it.
 */
object PairingRequests {
    /** A setup text to use as soon as the sync screen shows. */
    val link = MutableStateFlow<String?>(null)

    /** Open the code reader as soon as the sync screen shows. */
    val openReader = MutableStateFlow(false)

    /** Whether `text` is a setup text link. */
    fun isSetupLink(text: String?): Boolean = text != null && text.trim().startsWith(SETUP_LINK_SCHEME)

    const val SETUP_LINK_SCHEME = "voice://pair?"
}
