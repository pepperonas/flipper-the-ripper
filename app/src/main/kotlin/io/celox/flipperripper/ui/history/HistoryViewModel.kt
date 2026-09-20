package io.celox.flipperripper.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.celox.flipperripper.domain.model.DownloadRecord
import io.celox.flipperripper.domain.usecase.CancelDownloadUseCase
import io.celox.flipperripper.domain.usecase.ClearHistoryUseCase
import io.celox.flipperripper.domain.usecase.DeleteRecordUseCase
import io.celox.flipperripper.domain.usecase.ObserveHistoryUseCase
import io.celox.flipperripper.domain.usecase.RetryDownloadUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel
@Inject
constructor(
    observeHistory: ObserveHistoryUseCase,
    private val cancelDownload: CancelDownloadUseCase,
    private val retryDownload: RetryDownloadUseCase,
    private val deleteRecord: DeleteRecordUseCase,
    private val clearHistory: ClearHistoryUseCase,
) : ViewModel() {
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

    fun retry(id: String) = viewModelScope.launch { retryDownload(id) }

    fun delete(id: String) = viewModelScope.launch { deleteRecord(id) }

    fun clearAll() = viewModelScope.launch { clearHistory() }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
