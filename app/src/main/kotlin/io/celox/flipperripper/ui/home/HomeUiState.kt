package io.celox.flipperripper.ui.home

import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.Platform
import io.celox.flipperripper.domain.model.VideoInfo
import io.celox.flipperripper.domain.util.ParsedUrl

/** State for the Home screen. */
data class HomeUiState(
    val urlInput: String = "",
    val detectedPlatform: Platform? = null,
    val isResolving: Boolean = false,
    val videoInfo: VideoInfo? = null,
    val errorMessage: String? = null,
    val engineReady: Boolean = false,
    val defaultMode: DownloadMode = DownloadMode.VIDEO,
    val clipboardDetectionEnabled: Boolean = true,
    /** A supported URL detected on the clipboard, offered as a one-tap suggestion. */
    val clipboardSuggestion: ParsedUrl? = null,
) {
    val canDownload: Boolean get() = detectedPlatform != null && urlInput.isNotBlank()
    val showAudioOption: Boolean get() = detectedPlatform == Platform.YOUTUBE
}

/** One-shot events from the Home ViewModel. */
sealed interface HomeEvent {
    data class DownloadStarted(val recordId: String) : HomeEvent

    data class ShowMessage(val message: String) : HomeEvent
}
