package com.dotancohen.voiceandroid.data

import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File

/**
 * [MissingData.Store] over the real database and the real files.
 *
 * The phone has no ffmpeg, so a length is read with [MediaMetadataRetriever],
 * which reads the container's header and does not decode the audio. An
 * eight-hour recording is therefore as cheap to measure as a one-minute one.
 */
class RepositoryMissingDataStore(
    private val repository: VoiceRepository,
) : MissingData.Store {

    override suspend fun recordings(): List<AudioFile> =
        repository.getAllAudioFiles().getOrDefault(emptyList())

    override suspend fun fileFor(recording: AudioFile): File? {
        val path = repository.getAudioFilePath(recording.id).getOrNull() ?: return null
        val file = File(path)
        return if (file.exists() && file.length() > 0) file else null
    }

    override fun lengthSeconds(file: File): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val millis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: return null
            if (millis <= 0) null else (millis + 500) / 1000
        } catch (e: Throwable) {
            // A truncated or unsupported file is reported, never guessed at
            Log.w(TAG, "Could not read the length of ${file.name}: ${e.message}")
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Throwable) {
                // Nothing to do about a retriever that will not close
            }
        }
    }

    override fun madeAt(file: File, recordedName: String): Long? {
        val modified = file.lastModified().let { if (it > 0) it / 1000 else null }
        return MissingData.madeAtFrom(modified, recordedName)
    }

    override suspend fun saveLength(recordingId: String, seconds: Long): Boolean =
        repository.updateAudioFileDuration(recordingId, seconds).getOrDefault(false)

    override suspend fun saveMadeAt(recordingId: String, at: Long): Boolean =
        repository.updateAudioFileCreatedAt(recordingId, at).getOrDefault(false)

    private companion object {
        const val TAG = "MissingData"
    }
}
