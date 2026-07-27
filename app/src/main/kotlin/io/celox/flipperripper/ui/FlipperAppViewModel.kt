package io.celox.flipperripper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.celox.flipperripper.domain.model.hasActiveDownload
import io.celox.flipperripper.domain.usecase.ObserveHistoryUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * App-level state that outlives individual screens: whether any download is in flight. Continuous
 * motion is gated on this, so the UI stays still while nothing is happening.
 */
@HiltViewModel
class FlipperAppViewModel
@Inject
constructor(observeHistory: ObserveHistoryUseCase) : ViewModel() {
    val hasActiveDownloads: StateFlow<Boolean> =
        observeHistory()
            .map { it.hasActiveDownload() }
            // History updates on every progress tick; only the transitions matter here.
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = false,
            )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
