package io.celox.flipperripper.domain.model

/** A user's request to download a URL, optionally with metadata already resolved. */
data class DownloadRequest(
    val url: String,
    val platform: Platform,
    val mode: DownloadMode = DownloadMode.VIDEO,
    /** Pre-resolved title, used for the filename. If null the worker resolves it. */
    val title: String? = null,
    val thumbnailUrl: String? = null,
)
