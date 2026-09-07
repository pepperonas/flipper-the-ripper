package io.celox.flipperripper.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.celox.flipperripper.domain.model.BackendConfig
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.DownloadSource
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.model.EngineUpdateOutcome
import io.celox.flipperripper.domain.model.ThemeMode
import io.celox.flipperripper.domain.model.UserPreferences
import io.celox.flipperripper.domain.repository.BackendConfigRepository
import io.celox.flipperripper.domain.repository.SettingsRepository
import io.celox.flipperripper.domain.usecase.UpdateEngineUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
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

    private val _messages = Channel<SettingsMessage>(Channel.BUFFERED)
    val messages: Flow<SettingsMessage> = _messages.receiveAsFlow()

    /** True while an engine update is in flight, so the button can say so instead of looking idle. */
    private val _updatingEngine = MutableStateFlow(false)
    val updatingEngine = _updatingEngine.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }

    fun setAutoDownload(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setAutoDownloadOnShare(enabled) }

    fun setClipboardDetection(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setClipboardDetection(enabled) }

    fun setDefaultMode(mode: DownloadMode) = viewModelScope.launch { settingsRepository.setDefaultMode(mode) }

    fun updateEngineNow() {
        // Claim the slot synchronously, before any coroutine starts. Checking the flag *inside* the
        // coroutine lets two taps in the same frame both pass the check and both fetch — which is
        // exactly what happened until a test caught it.
        if (!_updatingEngine.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                val message =
                    when (val result = updateEngine()) {
                        is EngineResult.Success ->
                            SettingsMessage.EngineUpdate(EngineUpdateOutcome.parse(result.value))
                        // Engine errors already carry human wording from the error taxonomy.
                        is EngineResult.Failure -> SettingsMessage.Plain(result.error.message)
                    }
                _messages.send(message)
            } finally {
                _updatingEngine.value = false
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
