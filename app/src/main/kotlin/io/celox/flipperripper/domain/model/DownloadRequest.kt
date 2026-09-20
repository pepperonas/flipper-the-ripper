package io.celox.flipperripper.domain.model

/** A user's request to download a URL, optionally with metadata already resolved. */
data class DownloadRequest(
    val url: String,
    val platform: Platform,
    /**
     * What the user picked. [mode] follows from it — they are written together, from this one
     * value, so a record cannot end up claiming to be audio at 720p.
     */
    val quality: QualityChoice = QualityChoice.BEST,
    val mode: DownloadMode = FormatSelection.mode(quality),
    /** Pre-resolved title, used for the filename. If null the worker resolves it. */
    val title: String? = null,
    val thumbnailUrl: String? = null,
)
