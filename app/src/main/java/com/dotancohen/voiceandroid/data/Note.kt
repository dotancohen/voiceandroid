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
    val deletedAt: String? = null
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
