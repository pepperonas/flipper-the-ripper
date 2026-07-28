package io.celox.flipperripper.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.DownloadRequest
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.repository.SettingsRepository
import io.celox.flipperripper.domain.usecase.ObserveEngineReadyUseCase
import io.celox.flipperripper.domain.usecase.PeekClipboardUrlUseCase
import io.celox.flipperripper.domain.usecase.ResolveUrlUseCase
import io.celox.flipperripper.domain.usecase.ResolveVideoInfoUseCase
import io.celox.flipperripper.domain.usecase.StartDownloadUseCase
import io.celox.flipperripper.domain.util.UrlParser
import io.celox.flipperripper.ui.IncomingLinkBus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
@Inject
constructor(
    private val resolveUrl: ResolveUrlUseCase,
    private val resolveVideoInfo: ResolveVideoInfoUseCase,
    private val startDownload: StartDownloadUseCase,
    private val peekClipboardUrl: PeekClipboardUrlUseCase,
    observeEngineReady: ObserveEngineReadyUseCase,
    private val settingsRepository: SettingsRepository,
    private val incomingLinkBus: IncomingLinkBus,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeEngineReady().collect { ready -> _state.update { it.copy(engineReady = ready) } }
        }
        viewModelScope.launch {
            settingsRepository.preferences.collect { prefs ->
                _state.update {
                    it.copy(
                        defaultMode = prefs.defaultMode,
                        clipboardDetectionEnabled = prefs.clipboardDetection,
                    )
                }
            }
        }
        viewModelScope.launch {
            incomingLinkBus.links.collect { handleIncomingText(it) }
        }
    }

    fun onUrlChange(text: String) {
        _state.update {
            it.copy(
                urlInput = text,
                detectedPlatform = UrlParser.detectPlatform(text),
                videoInfo = null,
                errorMessage = null,
            )
        }
    }

    fun onPaste(pasted: String?) {
        val parsed = resolveUrl(pasted)
        if (parsed != null) {
            onUrlChange(parsed.url)
        } else if (!pasted.isNullOrBlank()) {
            onUrlChange(pasted.trim())
        }
    }

    /**
     * Called from a `LaunchedEffect` on first composition. That coroutine runs on the main dispatcher,
     * so the clipboard read has to be dispatched away from it — reading the clipboard is a binder call
     * into the system service and used to freeze the UI right after opening.
     */
    fun checkClipboard(prefEnabled: Boolean) {
        if (!prefEnabled) return
        viewModelScope.launch {
            val suggestion = peekClipboardUrl() ?: return@launch
            if (suggestion.url != _state.value.urlInput) {
                _state.update { it.copy(clipboardSuggestion = suggestion) }
            }
        }
    }

    fun acceptClipboardSuggestion() {
        val suggestion = _state.value.clipboardSuggestion ?: return
        _state.update { it.copy(clipboardSuggestion = null) }
        onUrlChange(suggestion.url)
        resolve()
    }

    fun dismissClipboardSuggestion() {
        _state.update { it.copy(clipboardSuggestion = null) }
    }

    fun resolve() {
        val url = _state.value.urlInput.trim()
        if (UrlParser.detectPlatform(url) == null) {
            _state.update { it.copy(errorMessage = "This link is not from a supported platform.") }
            return
        }
        _state.update { it.copy(isResolving = true, errorMessage = null, videoInfo = null) }
        viewModelScope.launch {
            when (val result = resolveVideoInfo(url, _state.value.defaultMode)) {
                is EngineResult.Success ->
                    _state.update { it.copy(isResolving = false, videoInfo = result.value) }
                is EngineResult.Failure ->
                    _state.update { it.copy(isResolving = false, errorMessage = result.error.message) }
            }
        }
    }

    fun download(mode: DownloadMode) {
        val state = _state.value
        val platform = state.detectedPlatform ?: return
        viewModelScope.launch {
            val request =
                DownloadRequest(
                    url = state.urlInput.trim(),
                    platform = platform,
                    mode = mode,
                    title = state.videoInfo?.title,
                    thumbnailUrl = state.videoInfo?.thumbnailUrl,
                )
            when (val result = startDownload(request)) {
                is EngineResult.Success -> {
                    _events.send(HomeEvent.DownloadStarted(result.value))
                    _state.update { HomeUiState(engineReady = it.engineReady, defaultMode = it.defaultMode) }
                }
                is EngineResult.Failure ->
                    _state.update { it.copy(errorMessage = result.error.message) }
            }
        }
    }

    private fun handleIncomingText(text: String) {
        val parsed = resolveUrl(text) ?: return
        onUrlChange(parsed.url)
        viewModelScope.launch {
            val prefs = settingsRepository.preferences.first()
            if (prefs.autoDownloadOnShare) download(prefs.defaultMode) else resolve()
        }
    }
}
