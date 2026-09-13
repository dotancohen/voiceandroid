package com.dotancohen.voiceandroid.util

import uniffi.voicecore.FileLocationData
import uniffi.voicecore.IssuesData
import uniffi.voicecore.RecordingNotInCloudData

/**
 * The words of the Issues screen and of where a recording's copies are
 * (ISSUE-1, FILE-22). The same words as the desktop's
 * `src/core/issues_text.py`, so both applications say one thing.
 */
object IssuesText {
    /** The short id the desktop shows too (UUID_SHORT_LEN) */
    private const val ID_START = 12

    /** A file size as people read it; "size unknown" when no device measured it. */
    fun sizeWords(bytes: Long?): String = when {
        bytes == null -> "size unknown"
        bytes >= 1024L * 1024 * 1024 -> String.format(java.util.Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024))
        bytes >= 1024L -> "${Math.round(bytes / 1024.0)} KB"
        else -> "$bytes bytes"
    }

    /** "the bucket", "this device", a device's name, or the start of its id. */
    fun placeLabel(place: String, names: Map<String, String>, here: String): String = when (place) {
        "cloud" -> "the bucket"
        here -> "this device"
        else -> names[place] ?: place.take(ID_START)
    }

    fun reasonWords(recording: RecordingNotInCloudData, limitBytes: ULong, names: Map<String, String>, here: String): String =
        when (recording.reason) {
            "no_bucket" -> "no bucket is set up for the account"
            "too_large" -> "larger than the account's upload limit of ${sizeWords(limitBytes.toLong())}"
            "waiting_for_upload" -> "waiting for ${recording.heldBy.joinToString(", ") { placeLabel(it, names, here) }} to upload it"
            else -> "no device and no bucket is known to hold it"
        }

    /** The Issues screen as sections of lines; a kind with nothing in it is left out. */
    fun sections(issues: IssuesData, names: Map<String, String>, here: String): List<Pair<String, List<String>>> {
        val sections = mutableListOf<Pair<String, List<String>>>()
        val notInCloud = issues.recordingsNotInCloud
        if (notInCloud.isNotEmpty()) {
            sections += "Recordings not in cloud storage (${notInCloud.size})" to notInCloud.map {
                "${it.filename} (${sizeWords(it.sizeBytes)}): ${reasonWords(it, issues.maxUploadBytes, names, here)}"
            }
        }
        if (issues.orphanedTranscriptions.isNotEmpty()) {
            sections += "Transcriptions whose recording is not there (${issues.orphanedTranscriptions.size})" to issues.orphanedTranscriptions.map {
                "Transcription ${it.transcriptionId.take(ID_START)} of recording ${it.audioFileId.take(ID_START)}: ${it.contentStart}"
            }
        }
        if (issues.orphanedAttachments.isNotEmpty()) {
            sections += "Attachments whose note or recording is not there (${issues.orphanedAttachments.size})" to issues.orphanedAttachments.map {
                val missing = buildList {
                    if (it.noteMissing) add("its note ${it.noteId.take(ID_START)}")
                    if (it.targetMissing) add("its recording ${it.targetId.take(ID_START)}")
                }
                "Attachment ${it.attachmentId.take(ID_START)}: ${missing.joinToString(" and ")} is not there"
            }
        }
        if (issues.orphanedRecordings.isNotEmpty()) {
            sections += "Recordings no note holds (${issues.orphanedRecordings.size})" to issues.orphanedRecordings.map {
                "${it.filename} (${it.audioId.take(ID_START)})"
            }
        }
        if (issues.tagsWithWhitespace.isNotEmpty()) {
            sections += "Tags whose names contain spaces (${issues.tagsWithWhitespace.size})" to issues.tagsWithWhitespace.map { it.path }
        }
        return sections
    }

    /** Where a recording's copies are, one line per place, as last stated. */
    fun locationLines(locations: List<FileLocationData>, names: Map<String, String>, here: String, time: (Long) -> String): List<String> {
        if (locations.isEmpty()) return listOf("No place is known to hold it")
        return locations.map {
            val state = if (it.present) "holds it" else "does not hold it"
            "${placeLabel(it.place, names, here)}: $state (since ${time(it.changedAt)})"
        }
    }
}
