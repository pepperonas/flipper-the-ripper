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
    val history =
        observeHistory().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList<DownloadRecord>(),
        )

    fun cancel(id: String) = viewModelScope.launch { cancelDownload(id) }

    fun retry(id: String) = viewModelScope.launch { retryDownload(id) }

    fun delete(id: String) = viewModelScope.launch { deleteRecord(id) }

    fun clearAll() = viewModelScope.launch { clearHistory() }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
