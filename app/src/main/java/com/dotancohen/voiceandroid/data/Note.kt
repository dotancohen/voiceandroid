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
