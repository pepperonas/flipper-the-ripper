package io.celox.flipperripper.domain.model

/**
 * Metadata resolved for a URL before downloading, obtained from the engine's `--dump-single-json`.
 */
data class VideoInfo(
    val sourceUrl: String,
    val platform: Platform,
    val title: String,
    val uploader: String?,
    val thumbnailUrl: String?,
    /** Duration in seconds, when known. */
    val durationSeconds: Long?,
    val id: String?,
)
