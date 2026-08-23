package io.celox.flipperripper.data.engine

import io.celox.flipperripper.domain.model.DownloadError
import io.celox.flipperripper.domain.model.Platform

/**
 * Decides whether a failed download looks like the on-device yt-dlp being **out of date** — in
 * which case a forced engine update plus one retry can heal it without the user doing anything.
 *
 * YouTube breaks old extractors within months, and each break wears a NEW message — seen in the
 * field so far: "Sign in to confirm you're not a bot" ([DownloadError.LoginRequired]), "unable to
 * download video data: HTTP Error 403: Forbidden" mid-stream, "The page needs to be reloaded",
 * "No video formats found" (all [DownloadError.Unknown]). Enumerating messages is itself the rot
 * pattern, so every **non-terminal** YouTube failure qualifies; the guard against pointless loops
 * is [updateJustifiesRetry] — no fresh yt-dlp, no retry.
 *
 * Only yt-dlp platforms qualify: a WebView-extracted failure (Instagram/TikTok/Facebook) has
 * nothing to gain from a yt-dlp update.
 */
object StaleEngineRetry {
    fun shouldUpdateAndRetry(platform: Platform, error: DownloadError): Boolean {
        if (platform != Platform.YOUTUBE) return false
        return when (error) {
            is DownloadError.LoginRequired -> true
            is DownloadError.RateLimitedOrStale -> true
            is DownloadError.Unknown -> true
            // Terminal (the content is the problem), local (network/storage/engine-init), or
            // user-driven — a newer extractor changes none of these.
            else -> false
        }
    }

    /** Only a real update warrants the retry — if yt-dlp was already current, staleness wasn't it. */
    fun updateJustifiesRetry(updateStatus: String): Boolean = updateStatus == "DONE"
}
