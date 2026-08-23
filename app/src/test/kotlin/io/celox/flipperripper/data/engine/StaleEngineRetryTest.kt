package io.celox.flipperripper.data.engine

import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.DownloadError
import io.celox.flipperripper.domain.model.Platform
import org.junit.Test

class StaleEngineRetryTest {
    @Test
    fun `stale-shaped youtube failures trigger an update-and-retry`() {
        // The three shapes YouTube breakage has actually worn: bot-block, stale/rate-limit, and the
        // unclassified bucket (mid-stream 403, "page needs to be reloaded", "no formats found").
        listOf(
            DownloadError.LoginRequired("Sign in to confirm you're not a bot"),
            DownloadError.RateLimitedOrStale("extractor is out of date"),
            DownloadError.Unknown("Download failed: ERROR: unable to download video data: HTTP Error 403: Forbidden"),
            DownloadError.Unknown("The page needs to be reloaded."),
        ).forEach { error ->
            assertThat(StaleEngineRetry.shouldUpdateAndRetry(Platform.YOUTUBE, error)).isTrue()
        }
    }

    @Test
    fun `terminal and local failures never trigger the retry`() {
        listOf(
            DownloadError.PrivateVideo("private"),
            DownloadError.RegionBlocked("region"),
            DownloadError.Unavailable("gone"),
            DownloadError.InvalidUrl(),
            DownloadError.Cancelled(),
            DownloadError.Network(),
            DownloadError.Storage("disk full"),
            DownloadError.EngineNotReady(),
        ).forEach { error ->
            assertThat(StaleEngineRetry.shouldUpdateAndRetry(Platform.YOUTUBE, error)).isFalse()
        }
    }

    @Test
    fun `webview platforms gain nothing from a yt-dlp update`() {
        val error = DownloadError.Unknown("HTTP 403 from cdn.example")
        listOf(Platform.INSTAGRAM, Platform.TIKTOK, Platform.FACEBOOK).forEach { platform ->
            assertThat(StaleEngineRetry.shouldUpdateAndRetry(platform, error)).isFalse()
        }
    }

    @Test
    fun `only a real update justifies the retry`() {
        // youtubedl-android reports DONE for an actual version change; anything else means yt-dlp
        // was already current, so staleness was not the problem and a retry would just fail again.
        assertThat(StaleEngineRetry.updateJustifiesRetry("DONE")).isTrue()
        assertThat(StaleEngineRetry.updateJustifiesRetry("ALREADY_UP_TO_DATE")).isFalse()
        assertThat(StaleEngineRetry.updateJustifiesRetry("UP_TO_DATE")).isFalse()
    }
}
