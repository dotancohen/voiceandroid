package com.dotancohen.voiceandroid.data

import android.content.Context
import android.os.Build
import android.os.Environment
import com.dotancohen.voiceandroid.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.voicecore.VoiceClient
import uniffi.voicecore.VoiceCoreException
import uniffi.voicecore.SyncServerConfig as UniFFISyncServerConfig
import uniffi.voicecore.NoteData as UniFFINoteData
import uniffi.voicecore.SyncResultData as UniFFISyncResultData
import uniffi.voicecore.AudioFileData as UniFFIAudioFileData
import uniffi.voicecore.NoteAttachmentData as UniFFINoteAttachmentData
import uniffi.voicecore.TranscriptionData as UniFFITranscriptionData
import uniffi.voicecore.TagData as UniFFITagData
import uniffi.voicecore.SearchResultData as UniFFISearchResultData
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
            Result.success(Unit)
        } catch (e: VoiceCoreException) {
            AppLogger.e(TAG, "Failed to initialize VoiceClient", e)
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to initialize VoiceClient", e)
            Result.failure(e)
        }
    }

    private fun ensureInitialized(): VoiceClient {
        return client ?: VoiceClient(dataDir).also {
            client = it
            // Configure audiofile directory for the new client
            it.setAudiofileDirectory(audioFileDir)
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
            AppLogger.i(TAG, "Starting sync")
            val voiceClient = ensureInitialized()
            val result = voiceClient.syncNow()
            AppLogger.i(TAG, "Sync completed: success=${result.success}, received=${result.notesReceived}, sent=${result.notesSent}")
            Result.success(SyncResult(
                success = result.success,
                notesReceived = result.notesReceived,
                notesSent = result.notesSent,
                errorMessage = result.errorMessage
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
                errorMessage = result.errorMessage
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
                    deletedAt = data.deletedAt
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
     */
    suspend fun addTagToNote(noteId: String, tagId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.addTagToNote(noteId, tagId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remove a tag from a note.
     * Soft-deletes the note_tag association between the note and tag.
     */
    suspend fun removeTagFromNote(noteId: String, tagId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.removeTagFromNote(noteId, tagId))
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
                        deletedAt = data.deletedAt
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
