package com.dotancohen.voiceandroid.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.dotancohen.voiceandroid.util.AppLogger
import com.dotancohen.voiceandroid.util.ContentHash
import com.dotancohen.voiceandroid.viewmodel.SUPPORTED_AUDIO_EXTENSIONS

/** What importing one audio file did. */
sealed class AudioImportOutcome {
    /** A new note holds the recording. */
    data class Imported(val noteId: String, val audioFileId: String) : AudioImportOutcome()

    /** The account already holds a recording with this file name and these bytes (D31); nothing was added. */
    data class AlreadyImported(val audioFileId: String) : AudioImportOutcome()

    /** Nothing was added, for the reason given in words. */
    data class Refused(val reason: String) : AudioImportOutcome()
}

/**
 * Importing one audio file into a new note: the steps shared by the folder
 * import (Import Audio screen) and a file shared with Voice from another
 * application. The file is read through its URI, so a content:// URI granted
 * by the sharing application works as well as a document in a chosen folder.
 */
class AudioImport(private val context: Context, private val repository: VoiceRepository) {

    /**
     * Import `uri` under `filename` (its extension decides the format), unless
     * the account already holds it. Throws when the core or the copy fails.
     */
    suspend fun importFile(uri: Uri, filename: String, fileCreatedAt: Long?, tagIds: List<String> = emptyList()): AudioImportOutcome {
        val extension = filename.substringAfterLast('.', "").lowercase()
        if (extension !in SUPPORTED_AUDIO_EXTENSIONS) {
            return AudioImportOutcome.Refused("$filename is not in an audio format Voice imports")
        }
        val hash = context.contentResolver.openInputStream(uri)?.use { ContentHash.sha256(it) }
            ?: return AudioImportOutcome.Refused("$filename could not be read")
        repository.findImportedAudioFile(filename, hash).getOrElse { throw it }?.let { existing ->
            AppLogger.i(TAG, "Already imported: $filename")
            return AudioImportOutcome.AlreadyImported(existing)
        }

        val imported = repository.importAudioFile(filename, fileCreatedAt, durationSeconds(uri)).getOrElse { throw it }
        repository.copyAudioFileToStorage(context, uri, imported.audioFileId).getOrElse { throw it }
        for (tagId in tagIds) {
            repository.addTagToNote(imported.noteId, tagId)
        }
        AppLogger.i(TAG, "Imported: $filename -> note=${imported.noteId.take(8)}")
        return AudioImportOutcome.Imported(imported.noteId, imported.audioFileId)
    }

    /** The recording's length in whole seconds, or null when Android cannot tell. */
    private fun durationSeconds(uri: Uri): Long? {
        return try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { it / 1000 }
            } finally {
                retriever.release()
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not get duration for $uri: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "AudioImport"
    }
}
