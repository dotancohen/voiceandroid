package com.dotancohen.voiceandroid.data

/**
 * Data class representing a Note.
 * This mirrors the NoteData struct from the Rust UniFFI bindings.
 */
data class Note(
    val id: String,
    val content: String,
    val createdAt: String,
    val modifiedAt: String? = null,
    val deletedAt: String? = null,
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
    val errorMessage: String? = null
)

/**
 * Data class representing an audio file attachment.
 * This mirrors the AudioFileData struct from the Rust UniFFI bindings.
 */
data class AudioFile(
    val id: String,
    val importedAt: String,
    val filename: String,
    val fileCreatedAt: String? = null,
    val summary: String? = null,
    val deviceId: String,
    val modifiedAt: String? = null,
    val deletedAt: String? = null
)

/**
 * Data class representing a note-attachment association.
 * This mirrors the NoteAttachmentData struct from the Rust UniFFI bindings.
 */
data class NoteAttachment(
    val id: String,
    val noteId: String,
    val attachmentId: String,
    val attachmentType: String,
    val createdAt: String,
    val deviceId: String,
    val modifiedAt: String? = null,
    val deletedAt: String? = null
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
    val createdAt: String,
    val modifiedAt: String? = null,
    val deletedAt: String? = null
) {
    /**
     * Check if the transcription has a specific state tag.
     * State is a space-separated list of tags. Tags prefixed with `!` indicate false/negation.
     * Example: "original !verified !verbatim !cleaned !polished"
     */
    fun hasState(tag: String): Boolean {
        return state.split(" ").contains(tag)
    }

    /**
     * Check if the transcription is verified.
     */
    val isVerified: Boolean
        get() = hasState("verified")

    /**
     * Check if the transcription is the original (not edited).
     */
    val isOriginal: Boolean
        get() = hasState("original")

    /**
     * Check if the transcription has been cleaned (corrected errors).
     */
    val isCleaned: Boolean
        get() = hasState("cleaned")

    /**
     * Check if the transcription has been polished (improved for readability).
     */
    val isPolished: Boolean
        get() = hasState("polished")

    /**
     * Toggle a state tag. Returns the new state string.
     * If the tag is currently true (e.g., "verified"), it becomes false ("!verified").
     * If the tag is currently false (e.g., "!verified"), it becomes true ("verified").
     */
    fun toggleState(tag: String): String {
        val tags = state.split(" ").toMutableList()
        val negatedTag = "!$tag"

        return when {
            tags.contains(tag) -> {
                // Tag is true, make it false
                tags.remove(tag)
                tags.add(negatedTag)
                tags.joinToString(" ")
            }
            tags.contains(negatedTag) -> {
                // Tag is false, make it true
                tags.remove(negatedTag)
                tags.add(tag)
                tags.joinToString(" ")
            }
            else -> {
                // Tag doesn't exist, add it as true
                tags.add(tag)
                tags.joinToString(" ")
            }
        }
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
    val createdAt: String? = null,
    val modifiedAt: String? = null
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
