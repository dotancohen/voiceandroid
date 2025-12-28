package com.dotancohen.voiceandroid.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.voicecore.VoiceClient
import uniffi.voicecore.VoiceCoreException
import uniffi.voicecore.SyncServerConfig as UniFFISyncServerConfig
import uniffi.voicecore.NoteData as UniFFINoteData
import uniffi.voicecore.SyncResultData as UniFFISyncResultData
import uniffi.voicecore.AudioFileData as UniFFIAudioFileData
import uniffi.voicecore.NoteAttachmentData as UniFFINoteAttachmentData
import uniffi.voicecore.generateDeviceId as uniffiGenerateDeviceId
import java.io.File

/**
 * Repository for interacting with the Voice Core Rust library.
 */
class VoiceRepository(private val context: Context) {

    private val dataDir: String = context.filesDir.absolutePath
    private val audioFileDir: String by lazy {
        // Use external files directory for audio files (accessible for debugging, backed up)
        val dir = context.getExternalFilesDir("audio") ?: File(context.filesDir, "audio")
        dir.mkdirs()
        dir.absolutePath
    }

    private var client: VoiceClient? = null

    /**
     * Initialize the Voice client.
     * This should be called once when the app starts.
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (client == null) {
                client = VoiceClient(dataDir)
            }
            // Configure audiofile directory for sync
            ensureInitialized().setAudiofileDirectory(audioFileDir)
            Result.success(Unit)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun ensureInitialized(): VoiceClient {
        return client ?: VoiceClient(dataDir).also { client = it }
    }

    /**
     * Get all notes from the local database.
     */
    suspend fun getAllNotes(): Result<List<Note>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val notes = voiceClient.getAllNotes().map { noteData ->
                Note(
                    id = noteData.id,
                    content = noteData.content,
                    createdAt = noteData.createdAt,
                    modifiedAt = noteData.modifiedAt,
                    deletedAt = noteData.deletedAt
                )
            }
            Result.success(notes)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Configure sync settings.
     */
    suspend fun configureSync(
        serverUrl: String,
        serverPeerId: String,
        deviceId: String,
        deviceName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val config = UniFFISyncServerConfig(
                serverUrl = serverUrl,
                serverPeerId = serverPeerId,
                deviceId = deviceId,
                deviceName = deviceName
            )
            voiceClient.configureSync(config)
            Result.success(Unit)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Perform sync with the configured server.
     */
    suspend fun syncNow(): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val result = voiceClient.syncNow()
            Result.success(SyncResult(
                success = result.success,
                notesReceived = result.notesReceived,
                notesSent = result.notesSent,
                errorMessage = result.errorMessage
            ))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the current device ID.
     */
    suspend fun getDeviceId(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getDeviceId())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Set the device ID.
     */
    suspend fun setDeviceId(deviceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            voiceClient.setDeviceId(deviceId)
            Result.success(Unit)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the current device name.
     */
    suspend fun getDeviceName(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getDeviceName())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Set the device name.
     */
    suspend fun setDeviceName(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            voiceClient.setDeviceName(name)
            Result.success(Unit)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if sync is configured.
     */
    suspend fun isSyncConfigured(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.isSyncConfigured())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the current sync configuration.
     */
    suspend fun getSyncConfig(): Result<SyncServerConfig?> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val config = voiceClient.getSyncConfig()
            Result.success(config?.let {
                SyncServerConfig(
                    serverUrl = it.serverUrl,
                    serverPeerId = it.serverPeerId,
                    deviceId = it.deviceId,
                    deviceName = it.deviceName
                )
            })
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generate a new device ID.
     */
    fun generateDeviceId(): String {
        return uniffiGenerateDeviceId()
    }

    /**
     * Get all attachments for a note.
     */
    suspend fun getAttachmentsForNote(noteId: String): Result<List<NoteAttachment>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val attachments = voiceClient.getAttachmentsForNote(noteId).map { data ->
                NoteAttachment(
                    id = data.id,
                    noteId = data.noteId,
                    attachmentId = data.attachmentId,
                    attachmentType = data.attachmentType,
                    createdAt = data.createdAt,
                    deviceId = data.deviceId,
                    modifiedAt = data.modifiedAt,
                    deletedAt = data.deletedAt
                )
            }
            Result.success(attachments)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all audio files for a note (via note_attachments).
     */
    suspend fun getAudioFilesForNote(noteId: String): Result<List<AudioFile>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val audioFiles = voiceClient.getAudioFilesForNote(noteId).map { data ->
                AudioFile(
                    id = data.id,
                    importedAt = data.importedAt,
                    filename = data.filename,
                    fileCreatedAt = data.fileCreatedAt,
                    summary = data.summary,
                    deviceId = data.deviceId,
                    modifiedAt = data.modifiedAt,
                    deletedAt = data.deletedAt
                )
            }
            Result.success(audioFiles)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get a single audio file by ID.
     */
    suspend fun getAudioFile(audioFileId: String): Result<AudioFile?> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val audioFile = voiceClient.getAudioFile(audioFileId)?.let { data ->
                AudioFile(
                    id = data.id,
                    importedAt = data.importedAt,
                    filename = data.filename,
                    fileCreatedAt = data.fileCreatedAt,
                    summary = data.summary,
                    deviceId = data.deviceId,
                    modifiedAt = data.modifiedAt,
                    deletedAt = data.deletedAt
                )
            }
            Result.success(audioFile)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the file path for an audio file (if it exists on disk).
     */
    suspend fun getAudioFilePath(audioFileId: String): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getAudioFilePath(audioFileId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the audio file directory path.
     */
    fun getAudioFileDirectory(): String = audioFileDir

    /**
     * Close the client and release resources.
     */
    fun close() {
        client = null
    }

    companion object {
        @Volatile
        private var instance: VoiceRepository? = null

        fun getInstance(context: Context): VoiceRepository {
            return instance ?: synchronized(this) {
                instance ?: VoiceRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}

/**
 * Data class for sync server configuration.
 */
data class SyncServerConfig(
    val serverUrl: String,
    val serverPeerId: String,
    val deviceId: String,
    val deviceName: String
)
