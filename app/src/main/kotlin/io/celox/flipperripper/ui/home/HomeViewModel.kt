package io.celox.flipperripper.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.celox.flipperripper.BuildConfig
import io.celox.flipperripper.domain.model.DownloadRequest
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.model.FormatSelection
import io.celox.flipperripper.domain.model.QualityChoice
import io.celox.flipperripper.domain.repository.SettingsRepository
import io.celox.flipperripper.domain.usecase.ObserveEngineReadyUseCase
import io.celox.flipperripper.domain.usecase.PeekClipboardUrlUseCase
import io.celox.flipperripper.domain.usecase.ResolveUrlUseCase
import io.celox.flipperripper.domain.usecase.ResolveVideoInfoUseCase
import io.celox.flipperripper.domain.usecase.StartDownloadUseCase
import io.celox.flipperripper.domain.util.AppVersions
import io.celox.flipperripper.domain.util.UrlParser
import io.celox.flipperripper.ui.AppNavTarget
import io.celox.flipperripper.ui.AppNavigator
import io.celox.flipperripper.ui.ShareLinkHandler
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    private val shareLinkHandler: ShareLinkHandler,
    private val appNavigator: AppNavigator,
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
                        defaultQuality = prefs.defaultQuality,
                        // Only when the user has not picked for this download yet: overwriting a
                        // deliberate choice because the stored default arrived a moment later is
                        // exactly the kind of surprise the picker exists to avoid.
                        quality = if (it.quality == it.defaultQuality) prefs.defaultQuality else it.quality,
                        clipboardDetectionEnabled = prefs.clipboardDetection,
                    )
                }
            }
        }
        viewModelScope.launch {
            // Only the *prefill* case reaches Home: a share that auto-downloads is handled by
            // ShareLinkHandler on the application scope, because this ViewModel does not exist
            // unless Home happens to be composed (see ShareLinkHandler for what that cost).
            shareLinkHandler.prefilledLink.collect { link ->
                if (link != null) {
                    shareLinkHandler.consumePrefilledLink()
                    onUrlChange(link)
                    resolve()
                }
            }
        }
        viewModelScope.launch {
            combine(
                settingsRepository.knownAppUpdate,
                settingsRepository.dismissedUpdateVersion,
            ) { known, dismissed ->
                AppVersions.visibleUpdate(BuildConfig.VERSION_NAME, known, dismissed)
            }.collect { notice -> _state.update { it.copy(updateNotice = notice) } }
        }
    }

    fun dismissUpdateNotice() {
        val version = _state.value.updateNotice?.version ?: return
        viewModelScope.launch { settingsRepository.setDismissedUpdateVersion(version) }
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
            when (val result = resolveVideoInfo(url, FormatSelection.mode(_state.value.quality))) {
                is EngineResult.Success ->
                    _state.update { it.copy(isResolving = false, videoInfo = result.value) }
                is EngineResult.Failure ->
                    _state.update { it.copy(isResolving = false, errorMessage = result.error.message) }
            }
        }
    }

    /**
     * Enqueues the download for [mode] and shows it running.
     *
     * Every started download ends on History, whether the Download button or a shared link
     * started it. 1.8.3 briefly kept a shared link on Home with a snackbar instead — and what the
     * user then saw was an empty form and a message that was gone in four seconds: "I just see the
     * start screen". A download the user cannot see is a download that may or may not be
     * happening. The History card, queued or running with its progress wave at the top of the
     * list, is the only evidence that answers that; so that is where a share lands.
     */
    fun setQuality(choice: QualityChoice) = _state.update { it.copy(quality = choice) }

    fun download() {
        val state = _state.value
        val platform = state.detectedPlatform ?: return
        viewModelScope.launch {
            val request =
                DownloadRequest(
                    url = state.urlInput.trim(),
                    platform = platform,
                    quality = state.quality,
                    title = state.videoInfo?.title,
                    thumbnailUrl = state.videoInfo?.thumbnailUrl,
                )
            when (val result = startDownload(request)) {
                is EngineResult.Success -> {
                    _state.update {
                        HomeUiState(
                            engineReady = it.engineReady,
                            defaultQuality = it.defaultQuality,
                            quality = it.defaultQuality,
                        )
                    }
                    // Through the app-level navigator, not a Home-screen event: a share arrives with
                    // whatever tab was last open, and Home may not be composed at all.
                    appNavigator.navigateTo(AppNavTarget.HISTORY)
                }
                is EngineResult.Failure ->
                    _state.update { it.copy(errorMessage = result.error.message) }
            }
        }
    }
}
