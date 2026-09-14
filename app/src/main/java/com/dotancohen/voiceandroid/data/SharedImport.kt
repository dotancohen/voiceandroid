package com.dotancohen.voiceandroid.data

import android.content.Context
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.dotancohen.voiceandroid.util.AppLogger

/**
 * Turns what another application shared with Voice into a note: shared text
 * becomes a new note with that text, a shared audio file a new note holding
 * that recording (the same import as a folder's files, [AudioImport]).
 */
class SharedImport(private val context: Context, private val repository: VoiceRepository) {

    sealed class Result {
        /** The note to open, and a sentence to show beside it when there is something to say. */
        data class OpenNote(val noteId: String, val sentence: String? = null) : Result()

        /** Nothing was added; the sentence says why. */
        data class NothingAdded(val sentence: String) : Result()
    }

    suspend fun take(content: SharedContent): Result {
        // Voice may have been started by the share itself, before any screen set up the core
        repository.initialize().onFailure { return Result.NothingAdded("Voice could not open its notes: ${it.message}") }
        return when (content) {
            is SharedContent.Text -> repository.createNote(content.text).fold(
                onSuccess = { Result.OpenNote(it) },
                onFailure = { Result.NothingAdded("The shared text was not saved: ${it.message}") }
            )
            is SharedContent.Audio -> takeAudio(content)
        }
    }

    private suspend fun takeAudio(content: SharedContent.Audio): Result {
        val name = SharedContent.fileName(displayNameOf(content), content.mimeType) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(it)
        }
        val outcome = try {
            AudioImport(context, repository).importFile(content.uri, name, fileCreatedAt = null)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Shared file $name was not imported", e)
            return Result.NothingAdded("$name was not imported: ${e.message}")
        }
        return when (outcome) {
            is AudioImportOutcome.Imported -> Result.OpenNote(outcome.noteId)
            is AudioImportOutcome.AlreadyImported -> {
                val noteId = repository.getNotesForAudioFile(outcome.audioFileId).getOrNull()?.firstOrNull()
                if (noteId != null) Result.OpenNote(noteId, "$name is already in Voice: this is its note")
                else Result.NothingAdded("$name is already in Voice")
            }
            is AudioImportOutcome.Refused -> Result.NothingAdded(outcome.reason)
        }
    }

    /** The name the sharing application gave the file, or null when it gave none. */
    private fun displayNameOf(content: SharedContent.Audio): String? = try {
        context.contentResolver.query(content.uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: content.uri.lastPathSegment
    } catch (e: Exception) {
        content.uri.lastPathSegment
    }

    companion object {
        private const val TAG = "SharedImport"
    }
}
