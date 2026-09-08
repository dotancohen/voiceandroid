package com.dotancohen.voiceandroid.data

import android.content.Context
import android.os.Build
import android.os.Environment
import com.dotancohen.voiceandroid.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.voicecore.VoiceClient
import uniffi.voicecore.VoiceCoreException
import uniffi.voicecore.ConflictData
import uniffi.voicecore.VersionData
import uniffi.voicecore.SyncServerConfig as UniFFISyncServerConfig
import uniffi.voicecore.NoteData as UniFFINoteData
import uniffi.voicecore.SyncResultData as UniFFISyncResultData
import uniffi.voicecore.AudioFileData as UniFFIAudioFileData
import uniffi.voicecore.NoteAttachmentData as UniFFINoteAttachmentData
import uniffi.voicecore.TranscriptionData as UniFFITranscriptionData
import uniffi.voicecore.TagData as UniFFITagData
import uniffi.voicecore.SearchResultData as UniFFISearchResultData
import uniffi.voicecore.TagChangeResultData as UniFFITagChangeResultData
import uniffi.voicecore.ImportAudioResultData as UniFFIImportAudioResultData
import uniffi.voicecore.generateDeviceId as uniffiGenerateDeviceId
import java.io.File

/**
 * Repository for interacting with the Voice Core Rust library.
 */
class VoiceRepository(private val context: Context) {

    private val dataDir: String = context.filesDir.absolutePath
    private val prefs = context.getSharedPreferences("voice_settings", Context.MODE_PRIVATE)

    // Default audio file directory (app's external storage - accessible via file manager)
    val defaultAudioFileDir: String by lazy {
        val dir = context.getExternalFilesDir("audio") ?: File(context.filesDir, "audio")
        dir.mkdirs()
        dir.absolutePath
    }

    // Current audio file directory (may be user-configured or default)
    private var _audioFileDir: String? = null
    val audioFileDir: String
        get() = _audioFileDir ?: defaultAudioFileDir

    private var client: VoiceClient? = null

    init {
        // Load saved audiofile directory from preferences immediately on creation
        loadSavedAudiofileDirectory()
    }

    /**
     * Load the saved audiofile directory from SharedPreferences.
     * Called in init and can be called again to refresh.
     */
    private fun loadSavedAudiofileDirectory() {
        val savedPath = prefs.getString("audiofile_directory_path", null)
        if (savedPath != null && savedPath.isNotBlank()) {
            val dir = File(savedPath)
            // On Android 11+, File.canWrite() doesn't work reliably for external storage
            // even when MANAGE_EXTERNAL_STORAGE is granted. Check the permission instead.
            val canAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // For Android 11+, trust the path if we have all files access
                Environment.isExternalStorageManager() && dir.exists()
            } else {
                // For older versions, use the traditional check
                dir.exists() && dir.canWrite()
            }
            if (canAccess) {
                _audioFileDir = savedPath
            }
        }
    }

    /**
     * Initialize the Voice client.
     * This should be called once when the app starts.
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i(TAG, "Initializing VoiceRepository, dataDir=$dataDir")
            if (client == null) {
                client = VoiceClient(dataDir)
                AppLogger.i(TAG, "VoiceClient created")
            }

            // Reload audiofile directory in case permission was granted after creation
            loadSavedAudiofileDirectory()

            // Configure audiofile directory for sync
            ensureInitialized().setAudiofileDirectory(audioFileDir)
            AppLogger.i(TAG, "Audio file directory set to: $audioFileDir")

            // Rebuild list caches to ensure duration/tags/marked are populated
            try {
                val count = ensureInitialized().rebuildAllNoteListCaches()
                AppLogger.i(TAG, "Rebuilt list caches for $count notes")
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to rebuild list caches: ${e.message}")
            }

            Result.success(Unit)
        } catch (e: VoiceCoreException) {
            AppLogger.e(TAG, "Failed to initialize VoiceClient", e)
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to initialize VoiceClient", e)
            Result.failure(e)
        }
    }

    @Synchronized
    private fun ensureInitialized(): VoiceClient {
        return client ?: VoiceClient(dataDir).also {
            client = it
            // Configure audiofile directory for the new client
            it.setAudiofileDirectory(audioFileDir)
            AppLogger.i(TAG, "VoiceClient created, dataDir=$dataDir, audioFileDir=$audioFileDir")

            // Rebuild list caches to ensure duration/tags/marked are populated
            try {
                val count = it.rebuildAllNoteListCaches()
                AppLogger.i(TAG, "Rebuilt list caches for $count notes")
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to rebuild list caches: ${e.message}")
            }
        }
    }

    /**
     * Set the audiofile directory path.
     * Pass null or empty string to use the default directory.
     * Returns true if the directory was set successfully, false if it doesn't exist or isn't writable.
     */
    suspend fun setAudiofileDirectory(path: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (path.isNullOrBlank()) {
                _audioFileDir = null
                prefs.edit().remove("audiofile_directory_path").apply()
            } else {
                val dir = File(path)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                // On Android 11+, File.canWrite() doesn't work reliably for external storage
                // even when MANAGE_EXTERNAL_STORAGE is granted. Check the permission instead.
                val canAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager() && dir.exists()
                } else {
                    dir.exists() && dir.canWrite()
                }
                if (!canAccess) {
                    return@withContext Result.failure(Exception("Directory does not exist or is not writable: $path"))
                }
                _audioFileDir = path
                prefs.edit().putString("audiofile_directory_path", path).apply()
            }

            // Update voicecore with new directory
            ensureInitialized().setAudiofileDirectory(audioFileDir)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
                    deletedAt = noteData.deletedAt,
                    listDisplayCache = noteData.listDisplayCache
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
            AppLogger.i(TAG, "Starting sync")
            val voiceClient = ensureInitialized()
            val result = voiceClient.syncNow()
            AppLogger.i(TAG, "Sync completed: success=${result.success}, received=${result.notesReceived}, sent=${result.notesSent}")
            Result.success(SyncResult(
                success = result.success,
                notesReceived = result.notesReceived,
                notesSent = result.notesSent,
                errorMessage = result.errorMessage,
                warnings = result.warnings
            ))
        } catch (e: VoiceCoreException) {
            AppLogger.e(TAG, "Sync failed", e)
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Sync failed", e)
            Result.failure(e)
        }
    }

    /**
     * Clear sync state to force a full re-sync from scratch.
     */
    suspend fun clearSyncState(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            voiceClient.clearSyncState()
            Result.success(Unit)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reset sync timestamps to force re-fetching all data from peers.
     * Unlike clearSyncState, this preserves peer configuration.
     */
    suspend fun resetSyncTimestamps(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            voiceClient.resetSyncTimestamps()
            Result.success(Unit)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Perform initial sync - fetches full dataset from server.
     * Use this for first-time sync or to re-fetch everything.
     */
    suspend fun initialSync(): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i(TAG, "Starting initial sync (full dataset fetch)")
            val voiceClient = ensureInitialized()
            val result = voiceClient.initialSync()
            AppLogger.i(TAG, "Initial sync completed: success=${result.success}, received=${result.notesReceived}, sent=${result.notesSent}")
            Result.success(SyncResult(
                success = result.success,
                notesReceived = result.notesReceived,
                notesSent = result.notesSent,
                errorMessage = result.errorMessage,
                warnings = result.warnings
            ))
        } catch (e: VoiceCoreException) {
            AppLogger.e(TAG, "Initial sync failed", e)
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Initial sync failed", e)
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
                    deletedAt = data.deletedAt,
                    storageProvider = data.storageProvider,
                    storageKey = data.storageKey
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
                    deletedAt = data.deletedAt,
                    storageProvider = data.storageProvider,
                    storageKey = data.storageKey
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
     * Get all audio files in the database (for debugging).
     */
    suspend fun getAllAudioFiles(): Result<List<AudioFile>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val audioFiles = voiceClient.getAllAudioFiles().map { data ->
                AudioFile(
                    id = data.id,
                    importedAt = data.importedAt,
                    filename = data.filename,
                    fileCreatedAt = data.fileCreatedAt,
                    summary = data.summary,
                    deviceId = data.deviceId,
                    modifiedAt = data.modifiedAt,
                    deletedAt = data.deletedAt,
                    storageProvider = data.storageProvider,
                    storageKey = data.storageKey
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
     * Update a note's content.
     */
    suspend fun updateNote(noteId: String, content: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.updateNote(noteId, content))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if there are local changes that haven't been synced.
     */
    suspend fun hasUnsyncedChanges(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.hasUnsyncedChanges())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Debug: get sync state details.
     */
    suspend fun debugSyncState(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.debugSyncState())
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

    // =========================================================================
    // Transcription Methods
    // =========================================================================

    /**
     * Get all transcriptions for an audio file.
     */
    suspend fun getTranscriptionsForAudioFile(audioFileId: String): Result<List<Transcription>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val transcriptions = voiceClient.getTranscriptionsForAudioFile(audioFileId).map { data ->
                Transcription(
                    id = data.id,
                    audioFileId = data.audioFileId,
                    content = data.content,
                    contentSegments = data.contentSegments,
                    service = data.service,
                    serviceArguments = data.serviceArguments,
                    serviceResponse = data.serviceResponse,
                    state = data.state,
                    deviceId = data.deviceId,
                    createdAt = data.createdAt,
                    modifiedAt = data.modifiedAt,
                    deletedAt = data.deletedAt
                )
            }
            Result.success(transcriptions)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get a single transcription by ID.
     */
    suspend fun getTranscription(transcriptionId: String): Result<Transcription?> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val transcription = voiceClient.getTranscription(transcriptionId)?.let { data ->
                Transcription(
                    id = data.id,
                    audioFileId = data.audioFileId,
                    content = data.content,
                    contentSegments = data.contentSegments,
                    service = data.service,
                    serviceArguments = data.serviceArguments,
                    serviceResponse = data.serviceResponse,
                    state = data.state,
                    deviceId = data.deviceId,
                    createdAt = data.createdAt,
                    modifiedAt = data.modifiedAt,
                    deletedAt = data.deletedAt
                )
            }
            Result.success(transcription)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update a transcription's state.
     * State is a space-separated list of tags.
     */
    suspend fun updateTranscriptionState(transcriptionId: String, state: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.updateTranscriptionState(transcriptionId, state))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a transcription record (on-device transcription writes a
     * "Pending..." row first, then the result). Returns the transcription id.
     */
    suspend fun createTranscription(
        audioFileId: String,
        content: String,
        service: String,
        serviceArguments: String?,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.createTranscription(audioFileId, content, null, service, serviceArguments, null))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Store a finished (or failed) transcription: text, segments JSON and the
     * service response, the same fields the desktop fills in.
     */
    suspend fun updateTranscriptionResult(
        transcriptionId: String,
        content: String,
        contentSegments: String?,
        serviceResponse: String?,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.updateTranscriptionResult(transcriptionId, content, contentSegments, serviceResponse))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update a transcription's content and optionally its state.
     */
    suspend fun updateTranscription(transcriptionId: String, content: String, state: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.updateTranscription(transcriptionId, content, state))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a note (soft delete - sets deleted_at timestamp).
     */
    suspend fun deleteNote(noteId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.deleteNote(noteId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a tag (soft delete - sets deleted_at timestamp).
     */
    suspend fun deleteTag(tagId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.deleteTag(tagId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a new tag.
     *
     * @param name The tag name
     * @param parentId Optional parent tag ID (null for root-level tag)
     * @return The ID of the newly created tag
     */
    suspend fun createTag(name: String, parentId: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.createTag(name, parentId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Rename a tag.
     *
     * @param tagId The tag ID
     * @param newName The new name for the tag
     * @return True if the tag was renamed, false if not found
     */
    suspend fun renameTag(tagId: String, newName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.renameTag(tagId, newName))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Move a tag to a different parent (or make it a root tag).
     *
     * @param tagId The tag ID to move
     * @param newParentId The new parent ID, or null to make it a root tag
     * @return True if the tag was moved, false if not found
     */
    suspend fun reparentTag(tagId: String, newParentId: String?): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.reparentTag(tagId, newParentId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Filter notes by tag IDs.
     *
     * @param tagIds List of tag IDs to filter by
     * @return Notes that have all the specified tags
     */
    suspend fun filterNotesByTags(tagIds: List<String>): Result<List<Note>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val notes = voiceClient.filterNotes(tagIds).map { data ->
                Note(
                    id = data.id,
                    content = data.content,
                    createdAt = data.createdAt,
                    modifiedAt = data.modifiedAt,
                    deletedAt = data.deletedAt,
                    listDisplayCache = data.listDisplayCache
                )
            }
            Result.success(notes)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // Tag and Search Methods
    // =========================================================================

    /**
     * Get all tags from the database.
     */
    suspend fun getAllTags(): Result<List<Tag>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val tags = voiceClient.getAllTags().map { data ->
                Tag(
                    id = data.id,
                    name = data.name,
                    parentId = data.parentId,
                    createdAt = data.createdAt,
                    modifiedAt = data.modifiedAt
                )
            }
            Result.success(tags)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all tags for a specific note.
     */
    suspend fun getTagsForNote(noteId: String): Result<List<Tag>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val tags = voiceClient.getTagsForNote(noteId).map { data ->
                Tag(
                    id = data.id,
                    name = data.name,
                    parentId = data.parentId,
                    createdAt = data.createdAt,
                    modifiedAt = data.modifiedAt
                )
            }
            Result.success(tags)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Add a tag to a note.
     * Creates a note_tag association between the note and tag.
     *
     * @return TagChangeResult with changed (true if tag was added), noteId, and listCacheRebuilt
     */
    suspend fun addTagToNote(noteId: String, tagId: String): Result<TagChangeResult> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val result = voiceClient.addTagToNote(noteId, tagId)
            Result.success(TagChangeResult(
                changed = result.changed,
                noteId = result.noteId,
                listCacheRebuilt = result.listCacheRebuilt
            ))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remove a tag from a note.
     * Soft-deletes the note_tag association between the note and tag.
     *
     * @return TagChangeResult with changed (true if tag was removed), noteId, and listCacheRebuilt
     */
    suspend fun removeTagFromNote(noteId: String, tagId: String): Result<TagChangeResult> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val result = voiceClient.removeTagFromNote(noteId, tagId)
            Result.success(TagChangeResult(
                changed = result.changed,
                noteId = result.noteId,
                listCacheRebuilt = result.listCacheRebuilt
            ))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // Note Marking (Star/Bookmark) Methods
    // =========================================================================

    /**
     * Check if a note is marked (starred/bookmarked).
     */
    suspend fun isNoteMarked(noteId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.isNoteMarked(noteId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mark a note (add the _system/_marked tag).
     * Returns true if the note was marked, false if already marked.
     */
    suspend fun markNote(noteId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.markNote(noteId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Unmark a note (remove the _system/_marked tag).
     * Returns true if the note was unmarked, false if not marked.
     */
    suspend fun unmarkNote(noteId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.unmarkNote(noteId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Toggle a note's marked state.
     * Returns the new marked state (true if now marked, false if now unmarked).
     */
    suspend fun toggleNoteMarked(noteId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.toggleNoteMarked(noteId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the _system tag ID as a hex string.
     * Used for filtering system tags from UI display.
     */
    suspend fun getSystemTagIdHex(): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getSystemTagIdHex())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Execute a search query.
     * Supports "tag:Name" syntax for tag filtering and free text search.
     * Multiple tags can be combined: "tag:Work tag:Important meeting notes"
     */
    suspend fun searchNotes(query: String): Result<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val result = voiceClient.searchNotes(query)
            val searchResult = SearchResult(
                notes = result.notes.map { data ->
                    Note(
                        id = data.id,
                        content = data.content,
                        createdAt = data.createdAt,
                        modifiedAt = data.modifiedAt,
                        deletedAt = data.deletedAt,
                        listDisplayCache = data.listDisplayCache
                    )
                },
                ambiguousTags = result.ambiguousTags,
                notFoundTags = result.notFoundTags
            )
            Result.success(searchResult)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the types of unresolved conflicts for a specific note.
     * Returns a list of conflict type strings (e.g., ["content", "delete"]).
     */
    suspend fun getNoteConflictTypes(noteId: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getNoteConflictTypes(noteId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Every unresolved conflict that concerns a note: its content, deletion,
     * tag links, attachments and their transcriptions. Each entry names the
     * two devices that disagreed.
     */
    suspend fun getNoteConflicts(noteId: String): Result<List<ConflictData>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getNoteConflicts(noteId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Accept the merged values of every conflict on a note as they stand.
     * The acceptance is a new version and reaches every peer on the next sync.
     * Returns how many conflicts were accepted.
     */
    suspend fun acceptNoteConflicts(noteId: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.acceptNoteConflicts(noteId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Every version of a note's content, oldest first.
     */
    suspend fun getNoteHistory(noteId: String): Result<List<VersionData>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getFieldHistory("note", noteId, "content"))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * One version by id (the sides of a conflict, for example).
     */
    suspend fun getVersion(versionId: String): Result<VersionData?> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getVersion(versionId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resolve a conflict by writing a new value for its field. Syncs to every peer.
     */
    suspend fun resolveConflictWithContent(conflictId: String, content: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.resolveConflictWithContent(conflictId, content))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Number of unresolved conflicts in the whole database.
     */
    suspend fun getUnresolvedConflictCount(): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getUnresolvedConflictCount())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * A synced setting shared by every device (e.g. transcription.preferred_languages), or null.
     */
    suspend fun getSetting(key: String): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getSetting(key))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Set a synced setting. Concurrent changes on two devices are merged and flagged.
     */
    suspend fun setSetting(key: String, value: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.setSetting(key, value))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // Audio Import Methods
    // =========================================================================

    /**
     * Import an audio file, creating all necessary database records.
     *
     * This creates:
     * 1. An AudioFile record
     * 2. A Note record (with created_at = file_created_at if provided)
     * 3. A NoteAttachment linking them
     *
     * @param filename Original filename of the audio file
     * @param fileCreatedAt Unix timestamp of when the file was created (optional)
     * @param durationSeconds Duration of the audio file in seconds (optional)
     * @return ImportAudioResult with noteId and audioFileId
     */
    suspend fun importAudioFile(
        filename: String,
        fileCreatedAt: Long?,
        durationSeconds: Long?
    ): Result<ImportAudioResult> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val result = voiceClient.importAudioFile(filename, fileCreatedAt, durationSeconds)
            Result.success(ImportAudioResult(
                noteId = result.noteId,
                audioFileId = result.audioFileId
            ))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Copy an audio file from a source URI to the audio file storage directory.
     *
     * @param context Application context for content resolver
     * @param sourceUri The URI of the source audio file
     * @param audioFileId The ID of the audio file record (used as the destination filename)
     * @param extension The file extension (e.g., "mp3", "m4a")
     * @return Result indicating success or failure
     */
    suspend fun copyAudioFileToStorage(
        context: Context,
        sourceUri: android.net.Uri,
        audioFileId: String,
        extension: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val destFile = File(audioFileDir, "$audioFileId.$extension")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Could not open source file"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a new note with empty content.
     *
     * @param content Initial content for the note (can be empty)
     * @return The ID of the created note
     */
    suspend fun createNote(content: String = ""): Result<String> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.createNote(content))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // File Storage Configuration Methods
    // =========================================================================

    /**
     * Get the current file storage configuration as JSON.
     * Returns null if no configuration is set.
     */
    suspend fun getFileStorageConfig(): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getFileStorageConfig())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Set the file storage configuration.
     *
     * @param provider The storage provider name (e.g., "s3", "none")
     * @param config Provider-specific configuration as JSON, or null to disable
     */
    suspend fun setFileStorageConfig(provider: String, config: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            voiceClient.setFileStorageConfig(provider, config)
            Result.success(Unit)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the current file storage provider name.
     * Returns "none" if no provider is configured.
     */
    suspend fun getFileStorageProvider(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getFileStorageProvider())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if file storage is enabled (provider is not "none").
     */
    suspend fun isFileStorageEnabled(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.isFileStorageEnabled())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update the storage info for an audio file after successful cloud upload.
     *
     * @param audioFileId The ID of the audio file
     * @param storageProvider The provider name (e.g., "s3")
     * @param storageKey The key/path in cloud storage
     * @return True if the audio file was found and updated
     */
    suspend fun updateAudioFileStorage(
        audioFileId: String,
        storageProvider: String,
        storageKey: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.updateAudioFileStorage(audioFileId, storageProvider, storageKey))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clear the storage info for an audio file (marks it as pending upload again).
     *
     * @param audioFileId The ID of the audio file
     * @return True if the audio file was found and updated
     */
    suspend fun clearAudioFileStorage(audioFileId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.clearAudioFileStorage(audioFileId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // Sync Configuration Methods
    // =========================================================================

    /**
     * Get the maximum sync file size in MB.
     * Files larger than this will be tagged as _system/_nonsynced/_too-big.
     */
    suspend fun getMaxSyncFileSizeMb(): Result<UInt> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getMaxSyncFileSizeMb())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Set the maximum sync file size in MB.
     * Files larger than this will be tagged as _system/_nonsynced/_too-big.
     *
     * @param sizeMb The maximum file size in MB (e.g., 100 for 100MB)
     */
    suspend fun setMaxSyncFileSizeMb(sizeMb: UInt): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            voiceClient.setMaxSyncFileSizeMb(sizeMb)
            Result.success(Unit)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // Cloud Storage Download Methods
    // =========================================================================

    private fun uniffi.voicecore.DownloadResultData.toModel() = DownloadResult(
        downloaded = downloaded,
        alreadyLocal = alreadyLocal,
        notInCloud = notInCloud,
        failed = failed,
        errors = errors
    )

    /**
     * Download every audio file of a note that is in cloud storage but not on
     * this device. This is the on-demand "media missing, download" action.
     *
     * Fails (Result.failure) when the cloud configuration has not arrived via
     * sync yet, when offline, or when an object is missing in the bucket.
     */
    suspend fun downloadAudioFilesForNote(noteId: String): Result<DownloadResult> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.downloadAudioFilesForNote(noteId).toModel())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download a single audio file on demand.
     */
    suspend fun downloadAudioFile(audioFileId: String): Result<DownloadResult> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.downloadAudioFile(audioFileId).toModel())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download every audio file that is in cloud storage but not on this device.
     * Only for explicit "fetch everything" actions; sync never does this on Android.
     *
     * @return DownloadResult with count of downloaded files and any errors
     */
    suspend fun downloadMissingAudioFiles(): Result<DownloadResult> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.downloadMissingAudioFiles().toModel())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if an audio file exists locally on disk.
     *
     * @param audioFileId The audio file ID
     * @return True if the file exists locally
     */
    suspend fun audioFileExistsLocally(audioFileId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.audioFileExistsLocally(audioFileId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if an audio file is available in cloud storage.
     *
     * @param audioFileId The audio file ID
     * @return True if the file has been uploaded to cloud storage
     */
    suspend fun audioFileInCloud(audioFileId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.audioFileInCloud(audioFileId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Close the client and release resources.
     */
    fun close() {
        client = null
    }

    companion object {
        private const val TAG = "VoiceRepository"

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

/**
 * Result of a tag change operation (add/remove tag from note).
 */
data class TagChangeResult(
    /** Whether the tag association was actually changed */
    val changed: Boolean,
    /** The note ID that was affected */
    val noteId: String,
    /** Whether the list pane cache was rebuilt */
    val listCacheRebuilt: Boolean
)

/**
 * Result of importing an audio file.
 */
data class ImportAudioResult(
    /** The ID of the created note */
    val noteId: String,
    /** The ID of the created audio file record */
    val audioFileId: String
)

/**
 * Result of downloading audio files from cloud storage.
 */
data class DownloadResult(
    /** Number of files successfully downloaded and verified */
    val downloaded: Int,
    /** Number of files that were already on this device */
    val alreadyLocal: Int = 0,
    /** Number of files whose owning device has not uploaded them yet */
    val notInCloud: Int = 0,
    /** Number of downloads that failed */
    val failed: Int = 0,
    /** Error messages for any failed downloads */
    val errors: List<String>
) {
    /** One-line human description of the outcome. */
    fun describe(): String {
        val parts = mutableListOf<String>()
        if (downloaded > 0) parts.add("downloaded $downloaded")
        if (alreadyLocal > 0) parts.add("$alreadyLocal already on this device")
        if (notInCloud > 0) parts.add("$notInCloud not uploaded by their device yet")
        if (failed > 0) parts.add("$failed failed")
        return if (parts.isEmpty()) "nothing to download" else parts.joinToString(", ")
    }
}
