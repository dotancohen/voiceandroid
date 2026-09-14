package com.dotancohen.voiceandroid.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dotancohen.voiceandroid.data.Tag
import com.dotancohen.voiceandroid.data.TagTree
import com.dotancohen.voiceandroid.data.VoiceRepository
import com.dotancohen.voiceandroid.util.AppLogger
import com.dotancohen.voiceandroid.util.CriticalLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Every common audio format a recording may be imported in, by extension: the
 * one list, kept in the core. A file with any other extension is skipped.
 */
val SUPPORTED_AUDIO_EXTENSIONS: Set<String> by lazy { uniffi.voicecore.audioFileFormats().toSet() }

/**
 * Data class representing a tag with hierarchy information for selection.
 */
data class SelectableTagItem(
    val tag: Tag,
    val path: String,
    val depth: Int,
    val hasChildren: Boolean,
    val isSelected: Boolean
)

/**
 * State of the import operation.
 */
sealed class ImportState {
    data object Idle : ImportState()
    data class InProgress(
        val current: Int,
        val total: Int,
        val currentFile: String
    ) : ImportState()
    data class Complete(
        val successCount: Int,
        val failedCount: Int,
        val errors: List<String>,
        /** Files the account already held, by name and bytes, and did not import again (D31) */
        val alreadyImportedCount: Int = 0
    ) : ImportState()
    data class Error(val message: String) : ImportState()
}

/**
 * ViewModel for the Import Audio screen.
 * Handles folder selection, tag selection, and audio file import.
 */
class ImportAudioViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VoiceRepository.getInstance(application)
    private val context: Context = application.applicationContext

    // Selected folder
    private val _selectedFolderUri = MutableStateFlow<Uri?>(null)
    val selectedFolderUri: StateFlow<Uri?> = _selectedFolderUri.asStateFlow()

    private val _selectedFolderName = MutableStateFlow<String?>(null)
    val selectedFolderName: StateFlow<String?> = _selectedFolderName.asStateFlow()

    private val _audioFileCount = MutableStateFlow(0)
    val audioFileCount: StateFlow<Int> = _audioFileCount.asStateFlow()

    // Tag selection
    private val _allTags = MutableStateFlow<List<SelectableTagItem>>(emptyList())
    val allTags: StateFlow<List<SelectableTagItem>> = _allTags.asStateFlow()

    private val _selectedTagIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedTagIds: StateFlow<Set<String>> = _selectedTagIds.asStateFlow()

    private val _filterText = MutableStateFlow("")
    val filterText: StateFlow<String> = _filterText.asStateFlow()

    private val _filteredTags = MutableStateFlow<List<SelectableTagItem>>(emptyList())
    val filteredTags: StateFlow<List<SelectableTagItem>> = _filteredTags.asStateFlow()

    private val _collapsedTagIds = MutableStateFlow<Set<String>>(emptySet())
    val collapsedTagIds: StateFlow<Set<String>> = _collapsedTagIds.asStateFlow()

    // Import state
    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Internal data
    private var rawTags: List<Tag> = emptyList()
    private var childrenByParentId: Map<String, Set<String>> = emptyMap()

    init {
        loadTags()
    }

    /**
     * Load all tags from the database.
     */
    fun loadTags() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            repository.getAllTags()
                .onSuccess { tags ->
                    // Without _system and every tag under it; the ids are fixed, so
                    // nothing has to be loaded first
                    val userTags = com.dotancohen.voiceandroid.data.TagTree.withoutSystemTags(tags)
                    rawTags = userTags
                    val tagsWithPaths = computeTagHierarchy(userTags)
                    _allTags.value = tagsWithPaths
                    updateFilteredTags()
                }
                .onFailure { e ->
                    _error.value = "Failed to load tags: ${e.message}"
                }

            _isLoading.value = false
        }
    }

    /**
     * Compute hierarchy information for all tags.
     */
    private fun computeTagHierarchy(tags: List<Tag>): List<SelectableTagItem> {
        val tagById = tags.associateBy { it.id }
        childrenByParentId = TagTree.childrenByParent(tags)

        return tags.map { tag ->
            SelectableTagItem(
                tag = tag,
                path = TagTree.pathOf(tag, tagById),
                depth = TagTree.depthOf(tag, tagById),
                hasChildren = childrenByParentId.containsKey(tag.id),
                isSelected = _selectedTagIds.value.contains(tag.id)
            )
        }.sortedBy { it.path.lowercase() }
    }

    /**
     * Set the selected folder URI.
     */
    fun setSelectedFolder(uri: Uri) {
        _selectedFolderUri.value = uri

        // Get folder name from URI
        val docFile = DocumentFile.fromTreeUri(context, uri)
        _selectedFolderName.value = docFile?.name ?: "Selected folder"

        // Count audio files in the folder
        countAudioFiles(uri)
    }

    /**
     * Count audio files in the selected folder.
     */
    private fun countAudioFiles(folderUri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val count = getAudioFilesInFolder(folderUri).size
                _audioFileCount.value = count
            }
        }
    }

    /**
     * Get list of audio files in the folder.
     */
    private fun getAudioFilesInFolder(folderUri: Uri): List<DocumentFile> {
        val docFile = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
        return docFile.listFiles().toList().filter { file ->
            file.isFile && file.name?.let { name ->
                val ext = name.substringAfterLast('.', "").lowercase()
                ext in SUPPORTED_AUDIO_EXTENSIONS
            } == true
        }
    }

    /**
     * Toggle tag selection.
     */
    fun toggleTag(tagId: String) {
        val current = _selectedTagIds.value
        _selectedTagIds.value = if (current.contains(tagId)) {
            current - tagId
        } else {
            current + tagId
        }
        // Update the allTags list to reflect selection change
        _allTags.value = _allTags.value.map { item ->
            if (item.tag.id == tagId) {
                item.copy(isSelected = !item.isSelected)
            } else {
                item
            }
        }
        updateFilteredTags()
    }

    /**
     * Update filter text and recompute filtered tags.
     */
    fun setFilterText(text: String) {
        _filterText.value = text
        updateFilteredTags()
    }

    /**
     * Toggle collapse state of a tag.
     */
    fun toggleCollapse(tagId: String) {
        val current = _collapsedTagIds.value
        _collapsedTagIds.value = if (current.contains(tagId)) {
            current - tagId
        } else {
            current + tagId
        }
        updateFilteredTags()
    }

    /**
     * Update the filtered tags list based on current filter and collapse state.
     */
    private fun updateFilteredTags() {
        val filter = _filterText.value.trim().lowercase()
        val all = _allTags.value

        val filtered = if (filter.isEmpty()) {
            all.filter { !isTagHiddenByCollapse(it) }
        } else {
            all.filter { tagItem ->
                tagItem.path.lowercase().contains(filter)
            }
        }
        _filteredTags.value = filtered
    }

    /**
     * Check if a tag is hidden due to a collapsed ancestor.
     */
    private fun isTagHiddenByCollapse(tagItem: SelectableTagItem): Boolean =
        TagTree.isHiddenByCollapse(
            tagItem.tag,
            _allTags.value.associate { it.tag.id to it.tag },
            _collapsedTagIds.value
        )

    /**
     * Check if storage permission is granted (for Android 11+).
     */
    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * Start the import process.
     */
    fun startImport() {
        val folderUri = _selectedFolderUri.value ?: return
        val selectedTags = _selectedTagIds.value.toList()

        viewModelScope.launch {
            _importState.value = ImportState.InProgress(0, 0, "Starting...")

            withContext(Dispatchers.IO) {
                val audioFiles = getAudioFilesInFolder(folderUri)
                val total = audioFiles.size
                var successCount = 0
                var failedCount = 0
                var alreadyImportedCount = 0
                val errors = mutableListOf<String>()

                for ((index, docFile) in audioFiles.withIndex()) {
                    val filename = docFile.name ?: "unknown"
                    _importState.value = ImportState.InProgress(index + 1, total, filename)

                    try {
                        when (importSingleFile(docFile, selectedTags)) {
                            ImportOutcome.IMPORTED -> successCount++
                            ImportOutcome.ALREADY_IMPORTED -> alreadyImportedCount++
                            ImportOutcome.FAILED -> {
                                failedCount++
                                errors.add(filename)
                                CriticalLog.logImportFailure(filename, "Import returned false")
                            }
                        }
                    } catch (e: Exception) {
                        failedCount++
                        val errorMsg = e.message ?: "Unknown error"
                        errors.add(filename)
                        CriticalLog.logImportFailure(filename, errorMsg)
                        AppLogger.e(TAG, "Failed to import $filename", e)
                    }
                }

                _importState.value = ImportState.Complete(successCount, failedCount, errors, alreadyImportedCount)
            }
        }
    }

    /** What happened to one file of the folder. */
    private enum class ImportOutcome { IMPORTED, ALREADY_IMPORTED, FAILED }

    private val audioImport = com.dotancohen.voiceandroid.data.AudioImport(context, repository)

    /**
     * Import a single audio file, unless the account already holds it: a
     * recording with the same file name and the same bytes (D31).
     */
    private suspend fun importSingleFile(docFile: DocumentFile, tagIds: List<String>): ImportOutcome {
        val filename = docFile.name ?: return ImportOutcome.FAILED
        val fileCreatedAt = docFile.lastModified().let { if (it > 0) it / 1000 else null }
        return when (val outcome = audioImport.importFile(docFile.uri, filename, fileCreatedAt, tagIds)) {
            is com.dotancohen.voiceandroid.data.AudioImportOutcome.Imported -> ImportOutcome.IMPORTED
            is com.dotancohen.voiceandroid.data.AudioImportOutcome.AlreadyImported -> ImportOutcome.ALREADY_IMPORTED
            is com.dotancohen.voiceandroid.data.AudioImportOutcome.Refused -> {
                AppLogger.w(TAG, outcome.reason)
                ImportOutcome.FAILED
            }
        }
    }

    /**
     * Reset the import state to Idle.
     */
    fun resetState() {
        _importState.value = ImportState.Idle
        _selectedFolderUri.value = null
        _selectedFolderName.value = null
        _audioFileCount.value = 0
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _error.value = null
    }

    companion object {
        private const val TAG = "ImportAudioViewModel"
    }
}
