package io.celox.flipperripper.data.engine

import io.celox.flipperripper.domain.model.DownloadError

/**
 * Classifies raw yt-dlp stderr into the [DownloadError] taxonomy.
 *
 * Direct port of the two classifiers in inspector-rust (`is_bot_block` /
 * `looks_stale_or_rate_limited`, `core/rust-lib/src/social_dl.rs`) plus the "matches neither
 * classifier" fall-through, extended with explicit private / region / network buckets that
 * inspector-rust folded into a generic "download failed: {last line}".
 *
 * All matching is case-insensitive substring matching, kept pure and order-sensitive.
 */
object ErrorClassifier {
    // --- inspector-rust is_bot_block() ---
    private val BOT_BLOCK =
        listOf(
            "not a bot",
            "sign in to confirm",
            "--cookies-from-browser",
            "--cookies",
            "login required",
            "requires authentication",
            "you need to log in",
            "log in to view",
        )

    // --- inspector-rust looks_stale_or_rate_limited() ---
    private val STALE_OR_RATE_LIMITED =
        listOf(
            "requested format is not available",
            "nsig",
            "signature extraction failed",
            "rate-limited",
            "rate limited",
            "too many requests",
            "please report this issue",
        )

    private val PRIVATE =
        listOf(
            "this video is private",
            "video is private",
            "private video",
            "this post is private",
            "this account is private",
        )

    private val REGION =
        listOf(
            "not available in your country",
            "not available in your region",
            "not available from your location",
            "geo restricted",
            "geo-restricted",
            "blocked it in your country",
        )

    private val UNAVAILABLE =
        listOf(
            "video unavailable",
            "content isn't available",
            "content isn’t available", // curly apostrophe variant, as in inspector-rust
            "has been removed",
            "no longer available",
            "this video has been removed",
            "removed by the uploader",
            "account has been terminated",
            "unable to find video",
        )

    private val NETWORK =
        listOf(
            "unable to download webpage",
            "urlopen error",
            "getaddrinfo",
            "temporary failure in name resolution",
            "network is unreachable",
            "connection reset",
            "connection refused",
            "timed out",
            "read timeout",
            "failed to establish a new connection",
        )

    /** True when stderr indicates an auth/anti-bot wall (drives cookie-retry logic upstream). */
    fun isBotBlock(stderr: String): Boolean {
        val s = stderr.lowercase()
        return BOT_BLOCK.any { s.contains(it) }
    }

    /** True when the extractor looks stale or the request was rate-limited. */
    fun looksStaleOrRateLimited(stderr: String): Boolean {
        val s = stderr.lowercase()
        return STALE_OR_RATE_LIMITED.any { s.contains(it) }
    }

    /**
     * Map stderr to a typed error. Precedence: auth → private → region → unavailable →
     * stale/rate-limited → network → unknown. (Private/region/unavailable are checked before the
     * stale bucket because "content isn't available" overlaps.)
     */
    fun classify(stderr: String): DownloadError {
        val s = stderr.lowercase()
        val lastLine = stderr.lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() }.orEmpty()

        return when {
            BOT_BLOCK.any { s.contains(it) } ->
                // Deliberately not phrased as "behind a login wall": the platform frequently answers
                // this way to a request it does not recognise as a real browser, even for content that
                // is fully public. Saying it needs a sign-in sent people looking for an account they
                // do not need, when switching to the server (which can present a browser TLS
                // fingerprint) downloads the very same video.
                DownloadError.LoginRequired(
                    "The platform refused this request. It may need a sign-in — but public content is " +
                        "often refused too. Switching to the server under Settings → Download source " +
                        "usually gets it.",
                )

            PRIVATE.any { s.contains(it) } ->
                DownloadError.PrivateVideo("This video is private and cannot be downloaded.")

            REGION.any { s.contains(it) } ->
                DownloadError.RegionBlocked("This video is not available in your region.")

            UNAVAILABLE.any { s.contains(it) } ->
                DownloadError.Unavailable("This video is unavailable or has been removed.")

            STALE_OR_RATE_LIMITED.any { s.contains(it) } ->
                DownloadError.RateLimitedOrStale(
                    "Temporarily unavailable — rate-limited or the extractor is out of date. " +
                        "Update the engine or try again in a few minutes.",
                )

            NETWORK.any { s.contains(it) } -> DownloadError.Network()

            else -> DownloadError.Unknown(if (lastLine.isNotEmpty()) "Download failed: $lastLine" else "Download failed.")
        }
    }
}
