package io.celox.flipperripper.ui.home

import io.celox.flipperripper.domain.model.AppUpdate
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
    /** A newer app release on GitHub, surfaced as a dismissible notice. */
    val updateNotice: AppUpdate? = null,
) {
    val canDownload: Boolean get() = detectedPlatform != null && urlInput.isNotBlank()
    val showAudioOption: Boolean get() = detectedPlatform == Platform.YOUTUBE
}

/**
 * One-shot events from the Home ViewModel. Navigation after a download starts is deliberately NOT
 * an event here — it goes through [io.celox.flipperripper.ui.AppNavigator], because these events
 * are only collected while the Home screen is composed.
 */
sealed interface HomeEvent {
    data class ShowMessage(val message: String) : HomeEvent
}
