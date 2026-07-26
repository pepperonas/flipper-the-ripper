package io.celox.flipperripper.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.model.ThemeMode
import io.celox.flipperripper.domain.model.UserPreferences
import io.celox.flipperripper.domain.repository.SettingsRepository
import io.celox.flipperripper.domain.usecase.UpdateEngineUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    private val settingsRepository: SettingsRepository,
    private val updateEngine: UpdateEngineUseCase,
) : ViewModel() {
    val preferences =
        settingsRepository.preferences.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UserPreferences(),
        )

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }

    fun setAutoDownload(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setAutoDownloadOnShare(enabled) }

    fun setClipboardDetection(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setClipboardDetection(enabled) }

    fun setDefaultMode(mode: DownloadMode) = viewModelScope.launch { settingsRepository.setDefaultMode(mode) }

    fun updateEngineNow() =
        viewModelScope.launch {
            when (val result = updateEngine()) {
                is EngineResult.Success -> _messages.send("Engine updated: ${result.value}")
                is EngineResult.Failure -> _messages.send(result.error.message)
            }
        }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
