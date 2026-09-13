package com.dotancohen.voiceandroid.data

import uniffi.voicecore.Stamp

/**
 * Data class representing a Note.
 * This mirrors the NoteData struct from the Rust UniFFI bindings.
 */
data class Note(
    val id: String,
    val content: String,
    val createdAt: Stamp,
    val modifiedAt: Stamp? = null,
    val deletedAt: Stamp? = null,
    /** Cache for notes list pane display (JSON with date, marked, content_preview) */
    val listDisplayCache: String? = null
)

/**
 * Data class representing sync result.
 * This mirrors the SyncResultData struct from the Rust UniFFI bindings.
 */
data class SyncResult(
    val success: Boolean,
    val notesReceived: Int,
    val notesSent: Int,
    /** Recordings sent to the peer (deliver, exchange, send) */
    val filesSent: Int = 0,
    /** Recordings fetched from the peer (exchange, fetch) */
    val filesFetched: Int = 0,
    /** Bytes of recordings moved either way */
    val bytesMoved: Long = 0,
    val errorMessage: String? = null,
    /** Non-fatal problems, e.g. a cloud upload that will be retried on the next sync */
    val warnings: List<String> = emptyList(),
    /** The id of the operation, on every request of it and in both logs (Stage 12) */
    val requestId: String = "",
    /** The peer's clock minus this phone's, in seconds, past a minute; else 0 */
    val clockSkewSeconds: Long = 0
)

/** A peer of this phone (Stage 5): the card's name or the local one, the remembered address, and the last operation. */
data class Peer(
    val peerId: String,
    val name: String,
    val url: String,
    val certificateFingerprint: String,
    val lastReachedAt: Long?,
    val lastOperation: String,
    /** The one the visible button names */
    val isLast: Boolean
)

/** What is on this phone only (Stage 10). */
data class NotDuplicated(val notes: Long, val recordings: Long) {
    /** The one line of the sync screen. */
    fun sentence(): String {
        if (notes == 0L && recordings == 0L) return "Everything is duplicated off this device."
        val notePart = "$notes note" + (if (notes != 1L) "s" else "")
        val recordingPart = "$recordings recording" + (if (recordings != 1L) "s" else "")
        return "$notePart and $recordingPart are not duplicated off this device."
    }
}

/** A peer known to hold a copy of a recording, and when that was learnt (Stage 10). */
data class RecordingCopy(val peerId: String, val at: Long)

/** A peer as remembered: when it was last reached and by which operation (Stage 10). */
data class PeerSummary(val peerId: String, val peerName: String, val lastReachedAt: Long?, val lastOperation: String)

/** One row of a connection check (Stage 12): what was checked, whether it passed, a sentence and a code. */
data class CheckRow(val name: String, val passed: Boolean, val detail: String, val code: String)

/**
 * Data class representing an audio file attachment.
 * This mirrors the AudioFileData struct from the Rust UniFFI bindings.
 */
data class AudioFile(
    val id: String,
    val importedAt: Stamp,
    val filename: String,
    val fileCreatedAt: Stamp? = null,
    /**
     * How long the recording is, where it is known.
     *
     * Null for a recording imported before the length was recorded, or one
     * whose header did not say. What is decided from it — whether a waveform
     * is drawn without asking, whether the phone will transcribe it — treats
     * null as "not known" and errs towards allowing the work.
     */
    val durationSeconds: Long? = null,
    val summary: String? = null,
    val deviceId: String,
    val modifiedAt: Stamp? = null,
    val deletedAt: Stamp? = null,
    /** Cloud storage provider ("s3") once the owning device uploaded the file */
    val storageProvider: String? = null,
    /** Object key in cloud storage once uploaded */
    val storageKey: String? = null,
    /** The file's name in the audio directory (Stage 13): the recording's start, the tail of its id, the extension */
    val localName: String = "",
    /** The SHA-256 of the file's bytes, lowercase hex, once computed (Stage 13) */
    val contentSha256: String? = null
) {
    /** True once the binary is available in cloud storage. */
    val isInCloud: Boolean
        get() = storageProvider != null && storageKey != null
}

/**
 * Data class representing a note-attachment association.
 * This mirrors the NoteAttachmentData struct from the Rust UniFFI bindings.
 */
data class NoteAttachment(
    val id: String,
    val noteId: String,
    val attachmentId: String,
    val attachmentType: String,
    val createdAt: Stamp,
    val deviceId: String,
    val modifiedAt: Stamp? = null,
    val deletedAt: Stamp? = null
)

/**
 * Data class representing a transcription of an audio file.
 * This mirrors the TranscriptionData struct from the Rust UniFFI bindings.
 */
data class Transcription(
    val id: String,
    val audioFileId: String,
    val content: String,
    val contentSegments: String? = null,
    val service: String,
    val serviceArguments: String? = null,
    val serviceResponse: String? = null,
    val state: String,
    val deviceId: String,
    val createdAt: Stamp,
    val modifiedAt: Stamp? = null,
    val deletedAt: Stamp? = null
) {
    /**
     * Whether one of the transcription's flags is set.
     *
     * The flags live in the [state] field as a space-separated list of
     * words, each set (`verified`) or explicitly not (`!verified`), e.g.
     * "original !verified !verbatim !cleaned !polished". The field keeps its
     * old name because that is the name in the database and in the sync
     * protocol; everything above the database calls them flags.
     */
    fun hasFlag(flag: String): Boolean {
        return flags().contains(flag)
    }

    /**
     * The flag words, with the blanks that come of an empty string or a
     * double space dropped. Splitting `""` on a space yields one empty word,
     * which would otherwise be written back out as a leading space.
     */
    private fun flags(): List<String> =
        state.split(" ").filter { it.isNotBlank() }

    /**
     * Check if the transcription is verified.
     */
    val isVerified: Boolean
        get() = hasFlag("verified")

    /**
     * Check if the transcription is the original (not edited).
     */
    val isOriginal: Boolean
        get() = hasFlag("original")

    /**
     * Check if the transcription has been cleaned (corrected errors).
     */
    val isCleaned: Boolean
        get() = hasFlag("cleaned")

    /**
     * Check if the transcription has been polished (improved for readability).
     */
    val isPolished: Boolean
        get() = hasFlag("polished")

    /**
     * The flag field with one flag turned the other way round.
     *
     * Set becomes explicitly not set, not set becomes set, and a flag the
     * field never mentioned is added as set. The other words keep their
     * order.
     */
    fun toggleFlag(flag: String): String {
        val tags = flags().toMutableList()
        val negated = "!$flag"

        return when {
            tags.contains(flag) -> {
                tags.remove(flag)
                tags.add(negated)
                tags.joinToString(" ")
            }
            tags.contains(negated) -> {
                tags.remove(negated)
                tags.add(flag)
                tags.joinToString(" ")
            }
            else -> {
                tags.add(flag)
                tags.joinToString(" ")
            }
        }
    }
}

/**
 * A transcription that actually holds text: the rows that are still waiting
 * or that failed do not count as a transcription of the recording.
 */
val Transcription.isFinished: Boolean
    get() = !content.startsWith("Error:") && !content.startsWith("Pending...")

/**
 * A transcription that was asked for and has not arrived: the phone writes
 * the row before it starts working, and fills it in when it is done.
 *
 * A row that failed is not pending: it will not become a transcription by
 * waiting, and the user is told what went wrong instead of being shown a
 * clock for ever.
 */
val Transcription.isPending: Boolean
    get() = content.startsWith("Pending...")

/**
 * A transcription that did not happen: the row says what went wrong.
 *
 * Its own kind, because the queue shows it differently from one that worked
 * and differently again from one that is still waiting.
 */
val Transcription.isError: Boolean
    get() = content.startsWith("Error:")

/**
 * The model a local transcription was made with, taken from the arguments
 * stored with the row (e.g. "large-v3-turbo-q5_0"), or null when the row does
 * not name one (a cloud service, or an older row).
 */
/**
 * The language the transcription was asked for, or null when the service was
 * left to detect it.
 */
val Transcription.requestedLanguage: String?
    get() = serviceArguments?.let { args ->
        try {
            val json = org.json.JSONObject(args)
            if (json.isNull("language")) null
            else json.optString("language").takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

/**
 * The languages the service says it found, when it says. A detected language
 * is worth showing even though nobody chose it: it explains a transcription
 * that came back in the wrong one.
 */
val Transcription.detectedLanguages: List<String>
    get() = serviceResponse?.let { response ->
        try {
            val array = org.json.JSONObject(response).optJSONArray("languages") ?: return@let null
            (0 until array.length()).mapNotNull { i -> array.optString(i).takeIf { it.isNotEmpty() } }
        } catch (_: Exception) {
            null
        }
    }.orEmpty()

/**
 * What the phone was doing while this transcription ran, as label and value
 * pairs ready to be read.
 *
 * The numbers are written by the transcriber into the service response. An
 * older transcription, or one from another service, has none and gives an
 * empty list.
 */
val Transcription.performance: List<Pair<String, String>>
    get() {
        val response = serviceResponse ?: return emptyList()
        val json = try {
            org.json.JSONObject(response)
        } catch (_: Exception) {
            return emptyList()
        }
        val out = mutableListOf<Pair<String, String>>()

        fun seconds(value: Double): String = when {
            value >= 60 -> "${(value / 60).toInt()} min ${"%.0f".format(value % 60)} s"
            value >= 1 -> "%.1f s".format(value)
            else -> "%.2f s".format(value)
        }

        fun bytes(value: Long): String = when {
            value >= 1_000_000_000L -> "%.2f GB".format(value / 1e9)
            value >= 1_000_000L -> "%.0f MB".format(value / 1e6)
            value >= 1_000L -> "%.0f kB".format(value / 1e3)
            else -> "$value B"
        }

        val audio = json.optDouble("duration_seconds", Double.NaN)
        if (!audio.isNaN()) out += "Recording" to seconds(audio)

        val performance = json.optJSONObject("performance")
        val elapsed = performance?.optDouble("elapsed_seconds", Double.NaN)
            ?: json.optDouble("elapsed_time", Double.NaN)
        if (elapsed != null && !elapsed.isNaN()) out += "Transcribing" to seconds(elapsed)

        if (performance != null) {
            performance.optDouble("total_seconds", Double.NaN).takeIf { !it.isNaN() }?.let {
                out += "Whole job" to seconds(it)
            }
            performance.optDouble("speed_vs_realtime", Double.NaN).takeIf { !it.isNaN() }?.let {
                out += "Speed" to "%.2f× real time".format(it)
            }
            performance.optDouble("cpu_seconds", Double.NaN).takeIf { !it.isNaN() }?.let {
                out += "CPU time" to seconds(it)
            }
            performance.optDouble("cpu_cores_busy", Double.NaN).takeIf { !it.isNaN() }?.let {
                val cores = performance.optInt("cpu_cores", 0)
                out += "Cores busy" to if (cores > 0) "%.2f of $cores".format(it) else "%.2f".format(it)
            }
            performance.optLong("peak_native_heap_bytes", 0L).takeIf { it > 0 }?.let {
                out += "Memory, peak" to bytes(it)
            }
            performance.optLong("native_heap_growth_bytes", 0L).takeIf { it > 0 }?.let {
                out += "Memory for this" to bytes(it)
            }
            performance.optLong("peak_java_heap_bytes", 0L).takeIf { it > 0 }?.let {
                val limit = performance.optLong("java_heap_limit_bytes", 0L)
                out += "App heap, peak" to
                    (bytes(it) + if (limit > 0) " of ${bytes(limit)}" else "")
            }
            performance.optLong("model_bytes", 0L).takeIf { it > 0 }?.let {
                out += "Model file" to bytes(it)
            }
            performance.optInt("beam_size", 0).takeIf { it > 0 }?.let {
                out += "Beam size" to it.toString()
            }
            performance.optLong("audio_bytes", 0L).takeIf { it > 0 }?.let {
                out += "Recording file" to bytes(it)
            }
            performance.optLong("converted_wav_bytes", 0L).takeIf { it > 0 }?.let {
                out += "Converted to" to "${bytes(it)} of 16 kHz WAV"
            }
            performance.optString("device_model").takeIf { it.isNotEmpty() }?.let {
                val sdk = performance.optInt("android_sdk", 0)
                out += "Phone" to (it + if (sdk > 0) ", Android ${performance.optString("android_version")} (API $sdk)" else "")
            }
        }

        json.optInt("segment_count", 0).takeIf { it > 0 }?.let {
            out += "Segments" to it.toString()
        }
        json.optDouble("confidence", Double.NaN).takeIf { !it.isNaN() }?.let {
            out += "Confidence" to "%.2f".format(it)
        }
        return out
    }

val Transcription.modelId: String?
    get() = serviceArguments?.let { args ->
        try {
            org.json.JSONObject(args).optString("model").takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

/**
 * Data class representing a tag.
 * This mirrors the TagData struct from the Rust UniFFI bindings.
 * Tags can be hierarchical with parent-child relationships.
 */
data class Tag(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val createdAt: Stamp? = null,
    val modifiedAt: Stamp? = null
)

/**
 * Data class representing a search result.
 * This mirrors the SearchResultData struct from the Rust UniFFI bindings.
 */
data class SearchResult(
    val notes: List<Note>,
    val ambiguousTags: List<String>,
    val notFoundTags: List<String>
)
