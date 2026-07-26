package io.celox.flipperripper.domain.model

/**
 * User-facing error taxonomy.
 *
 * Ported from the two stderr classifiers in inspector-rust
 * (`is_bot_block` / `looks_stale_or_rate_limited`, `core/rust-lib/src/social_dl.rs`) plus the
 * "matches neither classifier" fall-through that surfaces private/removed/region-locked videos.
 * See [io.celox.flipperripper.data.engine.ErrorClassifier] for the classification rules.
 */
sealed class DownloadError(
    /** A short, stable, human-readable message. UIs may localise on [kind]. */
    open val message: String,
) {
    /** The URL was empty, malformed, or from an unsupported host. */
    data class InvalidUrl(override val message: String = "This link is not valid or not supported.") : DownloadError(message)

    /** The content requires sign-in / is behind an anti-bot wall (yt-dlp: "sign in to confirm", cookies). */
    data class LoginRequired(override val message: String) : DownloadError(message)

    /** The video is private. */
    data class PrivateVideo(override val message: String) : DownloadError(message)

    /** The video is not available in the current region. */
    data class RegionBlocked(override val message: String) : DownloadError(message)

    /** Rate-limited or the extractor is stale (yt-dlp needs updating / retry later). */
    data class RateLimitedOrStale(override val message: String) : DownloadError(message)

    /** The video was removed or otherwise unavailable. */
    data class Unavailable(override val message: String) : DownloadError(message)

    /** A network/connectivity failure (offline, timeout, DNS). */
    data class Network(override val message: String = "Network error. Check your connection and try again.") :
        DownloadError(message)

    /** The download engine (yt-dlp binaries) is not initialised yet. */
    data class EngineNotReady(override val message: String = "The download engine is still initialising.") : DownloadError(message)

    /** Writing the file to storage failed. */
    data class Storage(override val message: String) : DownloadError(message)

    /** The user cancelled the download. */
    data class Cancelled(override val message: String = "Download cancelled.") : DownloadError(message)

    /** Anything not otherwise classified; carries the raw engine message. */
    data class Unknown(override val message: String) : DownloadError(message)

    /** Stable machine key for analytics/localisation, independent of the message text. */
    val kind: String get() = this::class.simpleName ?: "Unknown"
}
