package io.celox.flipperripper.data.engine

import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.DownloadError
import org.junit.Test

class ErrorClassifierTest {
    // --- isBotBlock (port of is_bot_block) ---

    @Test
    fun `isBotBlock matches known auth walls`() {
        val samples =
            listOf(
                "ERROR: Sign in to confirm you're not a bot",
                "Please sign in to confirm your age",
                "Use --cookies-from-browser or --cookies for the authentication",
                "ERROR: login required",
                "This video requires authentication",
                "You need to log in to view this",
            )
        for (s in samples) {
            assertThat(ErrorClassifier.isBotBlock(s)).isTrue()
        }
    }

    @Test
    fun `isBotBlock is false for unrelated errors`() {
        assertThat(ErrorClassifier.isBotBlock("ERROR: Video unavailable")).isFalse()
        assertThat(ErrorClassifier.isBotBlock("HTTP Error 404: Not Found")).isFalse()
    }

    // --- looksStaleOrRateLimited (port of looks_stale_or_rate_limited) ---

    @Test
    fun `looksStaleOrRateLimited matches stale and rate-limit signals`() {
        val samples =
            listOf(
                "ERROR: requested format is not available",
                "nsig extraction failed: Some(...)",
                "Signature extraction failed",
                "HTTP Error 429: Too Many Requests, rate-limited",
                "Please report this issue on the yt-dlp issue tracker",
            )
        for (s in samples) {
            assertThat(ErrorClassifier.looksStaleOrRateLimited(s)).isTrue()
        }
    }

    // --- classify() precedence and buckets ---

    @Test
    fun `classify auth wall to LoginRequired`() {
        assertThat(ErrorClassifier.classify("ERROR: Sign in to confirm you're not a bot"))
            .isInstanceOf(DownloadError.LoginRequired::class.java)
    }

    @Test
    fun `classify private video`() {
        assertThat(ErrorClassifier.classify("ERROR: Video unavailable. This video is private"))
            .isInstanceOf(DownloadError.PrivateVideo::class.java)
    }

    @Test
    fun `classify region block`() {
        assertThat(ErrorClassifier.classify("ERROR: This video is not available in your country"))
            .isInstanceOf(DownloadError.RegionBlocked::class.java)
    }

    @Test
    fun `classify removed or unavailable`() {
        assertThat(ErrorClassifier.classify("ERROR: This video has been removed by the uploader"))
            .isInstanceOf(DownloadError.Unavailable::class.java)
    }

    @Test
    fun `classify curly-apostrophe unavailable variant`() {
        assertThat(ErrorClassifier.classify("ERROR: This content isn’t available anymore"))
            .isInstanceOf(DownloadError.Unavailable::class.java)
    }

    @Test
    fun `classify rate-limited or stale`() {
        assertThat(ErrorClassifier.classify("ERROR: requested format is not available"))
            .isInstanceOf(DownloadError.RateLimitedOrStale::class.java)
    }

    @Test
    fun `classify network failure`() {
        assertThat(ErrorClassifier.classify("ERROR: unable to download webpage: <urlopen error timed out>"))
            .isInstanceOf(DownloadError.Network::class.java)
    }

    @Test
    fun `classify unknown carries last non-empty stderr line`() {
        val error = ErrorClassifier.classify("some noise\n\nHTTP Error 403: Forbidden\n")
        assertThat(error).isInstanceOf(DownloadError.Unknown::class.java)
        assertThat(error.message).contains("HTTP Error 403: Forbidden")
    }

    @Test
    fun `auth precedence beats unavailable when both present`() {
        // "video unavailable" + a sign-in wall -> auth wins (matches inspector-rust ordering intent).
        val error = ErrorClassifier.classify("Video unavailable. Sign in to confirm you're not a bot")
        assertThat(error).isInstanceOf(DownloadError.LoginRequired::class.java)
    }
}
