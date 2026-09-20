package io.celox.flipperripper.ui.home

import androidx.annotation.StringRes
import io.celox.flipperripper.data.engine.EngineRouting
import io.celox.flipperripper.domain.model.AppUpdate
import io.celox.flipperripper.domain.model.FormatSelection
import io.celox.flipperripper.domain.model.Platform
import io.celox.flipperripper.domain.model.QualityChoice
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
    /** The remembered default, and what a fresh screen starts on. */
    val defaultQuality: QualityChoice = QualityChoice.BEST,
    /** What this download will ask for. Starts at [defaultQuality] and follows the picker. */
    val quality: QualityChoice = QualityChoice.BEST,
    val clipboardDetectionEnabled: Boolean = true,
    /** A supported URL detected on the clipboard, offered as a one-tap suggestion. */
    val clipboardSuggestion: ParsedUrl? = null,
    /** A newer app release on GitHub, surfaced as a dismissible notice. */
    val updateNotice: AppUpdate? = null,
) {
    val canDownload: Boolean get() = detectedPlatform != null && urlInput.isNotBlank()

    /**
     * The tiers worth offering.
     *
     * Before *Load info* nothing is known about the renditions, so everything is offered rather
     * than nothing — the picker is not going to guess a ceiling it has no evidence for. The
     * platforms the app reaches through a WebView hand back one file and no format list at all;
     * there the sheet says so instead of showing choices that do nothing.
     */
    val availableQualities: Set<QualityChoice>
        get() = when {
            detectedPlatform?.let { !EngineRouting.usesYtDlp(it) } == true -> setOf(QualityChoice.BEST)
            videoInfo == null || videoInfo.formats.isEmpty() -> QualityChoice.entries.toSet()
            else -> FormatSelection.availableTiers(videoInfo.formats)
        }

    /** The highest resolution the platform reported, for the hint beside an unreachable tier. */
    val bestHeight: Int? get() = videoInfo?.formats?.let { FormatSelection.bestHeight(it) }
}

/**
 * One-shot events from the Home ViewModel. Navigation after a download starts is deliberately NOT
 * an event here — it goes through [io.celox.flipperripper.ui.AppNavigator], because these events
 * are only collected while the Home screen is composed.
 */
sealed interface HomeEvent {
    /**
     * A message to show as a snackbar, carried as a resource id rather than text: the ViewModel has
     * no Context, and a message built there would be English on every phone.
     */
    data class ShowMessage(@param:StringRes val messageRes: Int) : HomeEvent
}
