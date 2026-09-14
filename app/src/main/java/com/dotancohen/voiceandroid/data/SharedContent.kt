package com.dotancohen.voiceandroid.data

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/**
 * What another application shared with Voice through Android's share menu
 * (an `ACTION_SEND` intent): text, which becomes a new note, or an audio
 * file, which becomes a new note holding that recording.
 */
sealed class SharedContent {
    data class Text(val text: String) : SharedContent()

    data class Audio(val uri: Uri, val mimeType: String) : SharedContent()

    companion object {
        /** The shared content, or null when the intent is not a share Voice takes. */
        fun from(intent: Intent?): SharedContent? {
            if (intent?.action != Intent.ACTION_SEND) return null
            val type = intent.type ?: return null
            val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            if (stream != null) {
                return if (type.startsWith("audio/")) Audio(stream, type) else null
            }
            if (!type.startsWith("text/")) return null
            val text = noteText(intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(), intent.getStringExtra(Intent.EXTRA_SUBJECT))
            return text?.let { Text(it) }
        }

        /**
         * The note's text from a share's text and subject. A browser shares a
         * page as subject (its title) and text (its address); a mail shares
         * subject and body. The subject goes first on a line of its own,
         * unless the text already starts with it. Null when both are empty.
         */
        fun noteText(text: String?, subject: String?): String? {
            val body = text?.trim().orEmpty()
            val title = subject?.trim().orEmpty()
            return when {
                body.isEmpty() && title.isEmpty() -> null
                title.isEmpty() || body.startsWith(title) -> body
                body.isEmpty() -> title
                else -> "$title\n\n$body"
            }
        }

        /**
         * The file name a shared file is imported under: the name the sharing
         * application gave, with the extension of its MIME type added when the
         * name has none (the extension decides the format, FILE-21).
         */
        fun fileName(displayName: String?, mimeType: String, extensionOfMimeType: (String) -> String?): String {
            val extension = extensionOfMimeType(mimeType)
            val name = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: "shared"
            val hasExtension = name.substringAfterLast('.', "").isNotEmpty()
            return if (hasExtension || extension == null) name else "$name.$extension"
        }
    }
}
