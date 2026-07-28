package io.celox.flipperripper.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.celox.flipperripper.domain.model.BackendConfig
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.DownloadSource
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.model.ThemeMode
import io.celox.flipperripper.domain.model.UserPreferences
import io.celox.flipperripper.domain.repository.BackendConfigRepository
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
    private val backendConfigRepository: BackendConfigRepository,
    private val updateEngine: UpdateEngineUseCase,
    private val instagramSession: io.celox.flipperripper.data.engine.InstagramSession,
) : ViewModel() {
    val instagramLoggedIn = instagramSession.loggedIn

    /** Re-read the login state (call when Settings is shown, so returning from login updates it). */
    fun refreshInstagram() = instagramSession.refresh()

    fun signOutInstagram() = instagramSession.signOut()

    val preferences =
        settingsRepository.preferences.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UserPreferences(),
        )

    val backendConfig =
        backendConfigRepository.config.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = BackendConfig(DownloadSource.ON_DEVICE, "", ""),
        )

    fun setDownloadSource(source: DownloadSource) =
        viewModelScope.launch { backendConfigRepository.setSource(source) }

    fun setServer(url: String, apiKey: String) =
        viewModelScope.launch { backendConfigRepository.setServer(url, apiKey) }

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
