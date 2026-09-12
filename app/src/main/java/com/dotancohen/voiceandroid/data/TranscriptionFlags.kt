package com.dotancohen.voiceandroid.data

/** One of the things that can be said about a transcription. */
data class TranscriptionFlagInfo(
    /** The word written in the transcription's stored `state` field. */
    val name: String,
    /** What the user reads. */
    val title: String,
    /** What it means, in one line. */
    val description: String,
)

/**
 * The five flags of a transcription, in the order they are shown.
 *
 * They are flags rather than one state because any number of them can be
 * true at once: a transcription can be verified and cleaned, or verbatim and
 * not yet verified.
 *
 * They are kept in the transcription's `state` field — a space-separated
 * list of these words, each either set (`verified`) or explicitly not
 * (`!verified`). That field name is the one in the database and in the sync
 * protocol, shared with every device that has already synced, so it stays as
 * it is; everything above the database calls them flags.
 *
 * The order is the life of a transcription: what came out of the service,
 * whether a person has checked it, and then the three ways it may have been
 * rewritten since.
 */
object TranscriptionFlags {

    const val ORIGINAL = "original"
    const val VERIFIED = "verified"
    const val VERBATIM = "verbatim"
    const val CLEANED = "cleaned"
    const val POLISHED = "polished"

    val ALL: List<TranscriptionFlagInfo> = listOf(
        TranscriptionFlagInfo(
            ORIGINAL,
            "Original",
            "Unmodified transcription from the service",
        ),
        TranscriptionFlagInfo(
            VERIFIED,
            "Verified",
            "User has verified the transcription is accurate",
        ),
        TranscriptionFlagInfo(
            VERBATIM,
            "Verbatim",
            "Transcription includes filler words, false starts, etc.",
        ),
        TranscriptionFlagInfo(
            CLEANED,
            "Cleaned",
            "Transcription has been cleaned up (remove filler words)",
        ),
        TranscriptionFlagInfo(
            POLISHED,
            "Polished",
            "Transcription has been edited for readability",
        ),
    )

    /** The flags a transcription is created with: original, and nothing else. */
    val DEFAULT_FLAGS: String = ALL.joinToString(" ") { info ->
        if (info.name == ORIGINAL) info.name else "!${info.name}"
    }
}
