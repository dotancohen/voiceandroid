package com.dotancohen.voiceandroid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dotancohen.voiceandroid.data.Snapshot
import com.dotancohen.voiceandroid.data.VoiceRepository
import com.dotancohen.voiceandroid.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The snapshots beside the database: a copy is taken before every sync, and
 * any of them can be brought back. Holds no toolkit types, so the screen
 * that draws it can be replaced.
 */
class SnapshotsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VoiceRepository.getInstance(application)

    private val _snapshots = MutableStateFlow<List<Snapshot>>(emptyList())
    val snapshots: StateFlow<List<Snapshot>> = _snapshots.asStateFlow()

    /** The outcome of the last action in one sentence, or null. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _accountId = MutableStateFlow("")
    val accountId: StateFlow<String> = _accountId.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.listSnapshots().onSuccess { _snapshots.value = it }
            repository.getAccountId().onSuccess { _accountId.value = it }
        }
    }

    fun takeSnapshot() {
        viewModelScope.launch {
            repository.takeSnapshot()
                .onSuccess { _message.value = "Snapshot written" }
                .onFailure { _message.value = "Snapshot failed: ${it.message}" }
            refresh()
        }
    }

    fun restore(name: String) {
        viewModelScope.launch {
            repository.restoreSnapshot(name)
                .onSuccess { _message.value = "Restored $name. The state it replaced is the newest snapshot." }
                .onFailure {
                    AppLogger.e(TAG, "Restore of $name failed", it)
                    _message.value = "Restore failed: ${it.message}"
                }
            refresh()
        }
    }

    companion object {
        private const val TAG = "SnapshotsViewModel"
    }
}
