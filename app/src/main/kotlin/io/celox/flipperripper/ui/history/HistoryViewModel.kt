package io.celox.flipperripper.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.celox.flipperripper.data.engine.InstagramSession
import io.celox.flipperripper.domain.model.DownloadRecord
import io.celox.flipperripper.domain.model.isPending
import io.celox.flipperripper.domain.usecase.CancelDownloadUseCase
import io.celox.flipperripper.domain.usecase.ClearHistoryUseCase
import io.celox.flipperripper.domain.usecase.DeleteRecordUseCase
import io.celox.flipperripper.domain.usecase.ObserveHistoryUseCase
import io.celox.flipperripper.domain.usecase.PauseDownloadUseCase
import io.celox.flipperripper.domain.usecase.ReorderQueueUseCase
import io.celox.flipperripper.domain.usecase.RestoreRecordUseCase
import io.celox.flipperripper.domain.usecase.ResumeDownloadUseCase
import io.celox.flipperripper.domain.usecase.RetryDownloadUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel
@Inject
constructor(
    observeHistory: ObserveHistoryUseCase,
    private val cancelDownload: CancelDownloadUseCase,
    private val pauseDownload: PauseDownloadUseCase,
    private val resumeDownload: ResumeDownloadUseCase,
    private val reorderQueue: ReorderQueueUseCase,
    private val retryDownload: RetryDownloadUseCase,
    private val deleteRecord: DeleteRecordUseCase,
    private val restoreRecord: RestoreRecordUseCase,
    private val clearHistory: ClearHistoryUseCase,
    private val instagramSession: InstagramSession,
) : ViewModel() {
    /** Whether an Instagram session exists — a failed Instagram card offers the sign-in only without one. */
    val instagramSignedIn: StateFlow<Boolean> = instagramSession.loggedIn

    init {
        instagramSession.refresh()
    }

    /**
     * `null` means the database has not answered yet — deliberately not an empty list.
     *
     * The screen used to start from `emptyList()` and could not tell the two apart, so right after
     * sharing a link it drew "No downloads yet" for a few hundred milliseconds before the card
     * arrived (measured 260-320 ms). The first thing a user saw after a share was the app telling
     * them nothing had been downloaded.
     */
    val history: StateFlow<List<DownloadRecord>?> =
        observeHistory().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null,
        )

    fun cancel(id: String) = viewModelScope.launch { cancelDownload(id) }

    fun pause(id: String) = viewModelScope.launch { pauseDownload(id) }

    fun resume(id: String) = viewModelScope.launch { resumeDownload(id) }

    /** Called once, when the finger lets go — not on every pixel of the drag. */
    fun reorder(ids: List<String>) = viewModelScope.launch { reorderQueue(ids) }

    fun retry(id: String) = viewModelScope.launch { retryDownload(id) }

    /**
     * The entry the last delete removed, while it can still be put back. The screen shows an Undo
     * snackbar for it; [token] tells two deletes of the same entry apart (delete, undo, delete).
     */
    data class Removed(val record: DownloadRecord, val token: Long)

    private val removedState = MutableStateFlow<Removed?>(null)
    val removed: StateFlow<Removed?> = removedState.asStateFlow()
    private var removalCount = 0L

    /**
     * Deletes one entry. A finished one can be brought back with [undoDelete]; a pending one cannot,
     * because deleting it also cancelled the download and threw its partial file away.
     */
    fun delete(id: String) =
        viewModelScope.launch {
            val record = deleteRecord(id)
            removedState.value =
                if (record != null && !record.status.isPending) Removed(record, ++removalCount) else null
        }

    /** Puts back the entry [token] removed. A stale token (a newer delete happened) does nothing. */
    fun undoDelete(token: Long) =
        viewModelScope.launch {
            val removed = removedState.value?.takeIf { it.token == token } ?: return@launch
            removedState.value = null
            restoreRecord(removed.record)
        }

    /** The snackbar went away without Undo: the delete stands. */
    fun undoExpired(token: Long) {
        if (removedState.value?.token == token) removedState.value = null
    }

    fun clearAll() = viewModelScope.launch { clearHistory() }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
