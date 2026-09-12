package com.dotancohen.voiceandroid.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.dotancohen.voiceandroid.data.AudioFile
import com.dotancohen.voiceandroid.data.Note
import com.dotancohen.voiceandroid.data.Transcription
import com.dotancohen.voiceandroid.data.isFinished
import com.dotancohen.voiceandroid.ui.components.ShareItem
import java.io.File

/**
 * Sharing a Note, or the parts of it worth sharing.
 *
 * What can be shared is: the Note's own text, each recording, and each
 * finished Transcription of each recording. Anything empty is not offered —
 * a Note with no text does not show "Note content" with nothing behind it,
 * and a Transcription still being worked on is not sent as the word
 * "Pending...".
 */
object NoteSharing {

    const val NOTE_CONTENT_ID = "note-content"

    /**
     * The tree of things this Note can share, in reading order: the text
     * first, then each recording with its Transcriptions under it.
     *
     * An empty list means there is nothing to share at all.
     */
    fun itemsFor(
        note: Note?,
        audioFiles: List<AudioFile>,
        transcriptions: Map<String, List<Transcription>>,
        /** Recordings whose file is not on this phone cannot be sent. */
        isLocal: (String) -> Boolean = { true },
    ): List<ShareItem> {
        val items = mutableListOf<ShareItem>()

        note?.content?.takeIf { it.isNotBlank() }?.let { content ->
            items += ShareItem(
                id = NOTE_CONTENT_ID,
                label = "Note text",
                detail = content.lineSequence().firstOrNull { it.isNotBlank() },
            )
        }

        for (audioFile in audioFiles) {
            if (isLocal(audioFile.id)) {
                items += ShareItem(
                    id = audioFile.id,
                    label = audioFile.filename,
                    detail = "Recording",
                )
            }
            val rows = transcriptions[audioFile.id].orEmpty()
                .filter { it.deletedAt == null && it.isFinished && it.content.isNotBlank() }
            for (transcription in rows) {
                items += ShareItem(
                    id = transcription.id,
                    label = "Transcription (${transcription.service})",
                    detail = transcription.content.lineSequence().firstOrNull { it.isNotBlank() },
                    depth = 1,
                )
            }
        }
        return items
    }

    /**
     * The text of everything chosen, in the order of the tree, with a blank
     * line between one piece and the next.
     */
    fun textFor(
        chosen: Set<String>,
        note: Note?,
        audioFiles: List<AudioFile>,
        transcriptions: Map<String, List<Transcription>>,
    ): String {
        val parts = mutableListOf<String>()
        if (NOTE_CONTENT_ID in chosen) {
            note?.content?.takeIf { it.isNotBlank() }?.let(parts::add)
        }
        for (audioFile in audioFiles) {
            for (transcription in transcriptions[audioFile.id].orEmpty()) {
                if (transcription.id in chosen && transcription.content.isNotBlank()) {
                    parts.add(transcription.content)
                }
            }
        }
        return parts.joinToString("\n\n")
    }

    /** The chosen recordings, in the order they are shown. */
    fun audioFilesFor(chosen: Set<String>, audioFiles: List<AudioFile>): List<AudioFile> =
        audioFiles.filter { it.id in chosen }

    /**
     * Send what was chosen to another application.
     *
     * Text alone goes as text; recordings go as content URIs the receiving
     * application may read for this one action; both together go as files
     * with the text attached to them, which is what a mail client turns into
     * a message with attachments.
     */
    fun share(
        context: Context,
        text: String,
        files: List<File>,
        subject: String? = null,
    ): String? {
        // A recording kept somewhere the file provider was not told about, or
        // a phone with nothing able to receive what is being sent, must not
        // take the application down on the way out of it.
        val uris = files.filter { it.exists() }.mapNotNull { file ->
            try {
                FileProvider.getUriForFile(context, "${context.packageName}.shared", file)
            } catch (e: Throwable) {
                AppLogger.w(TAG, "Cannot share ${file.name} from where it is kept: $e")
                null
            }
        }
        if (uris.isEmpty() && text.isBlank()) {
            return "Nothing here could be shared"
        }

        val intent = when {
            uris.isEmpty() -> Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            uris.size == 1 -> Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uris.first())
                if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
            }
            else -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "audio/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(uris))
                if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
            }
        }
        subject?.takeIf { it.isNotBlank() }?.let { intent.putExtra(Intent.EXTRA_SUBJECT, it) }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        return try {
            context.startActivity(
                Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            null
        } catch (e: Throwable) {
            AppLogger.w(TAG, "Nothing on this phone would take what was shared: $e")
            "Nothing on this phone can receive that"
        }
    }

    private const val TAG = "NoteSharing"
}
