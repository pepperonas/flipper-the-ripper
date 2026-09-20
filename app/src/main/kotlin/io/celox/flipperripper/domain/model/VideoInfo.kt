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
    /**
     * The renditions the platform offers, reduced to what a quality choice depends on.
     *
     * Empty when the engine did not report any — a WebView platform, or a link nobody resolved
     * first. The picker then offers its default rather than nothing.
     */
    val formats: List<MediaFormat> = emptyList(),
)
