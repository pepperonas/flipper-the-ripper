package io.celox.flipperripper.data.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Media URLs are scraped out of JSON embedded in HTML, so they arrive escaped. The scraper used to
 * undo three escapes it had happened to meet — `&`, `%` and `\/` — and `/` was not among them, so a
 * TikTok URL reached the downloader with its slashes intact as `/` and the host parsed as the
 * literal string "u002f".
 */
class MediaUrlTest {
    @Test
    fun `the escape that broke TikTok downloads is decoded`() {
        val scraped = "https:\\u002F\\u002Fv16-webapp-prime.tiktok.com\\u002Fvideo\\u002Fabc.mp4"
        assertThat(MediaUrl.decodeEscapes(scraped))
            .isEqualTo("https://v16-webapp-prime.tiktok.com/video/abc.mp4")
    }

    @Test
    fun `the escapes that were already handled still are`() {
        assertThat(MediaUrl.decodeEscapes("https://h/p?a=1\\u0026b=2")).isEqualTo("https://h/p?a=1&b=2")
        assertThat(MediaUrl.decodeEscapes("https://h/p?t=50\\u0025")).isEqualTo("https://h/p?t=50%")
        assertThat(MediaUrl.decodeEscapes("https:\\/\\/h/p")).isEqualTo("https://h/p")
    }

    @Test
    fun `any four-digit escape decodes, not a hand-picked list`() {
        // The point of the fix: the next platform to escape some other character needs no code change.
        assertThat(MediaUrl.decodeEscapes("a\\u003Db")).isEqualTo("a=b")
        assertThat(MediaUrl.decodeEscapes("a\\u002Db")).isEqualTo("a-b")
        assertThat(MediaUrl.decodeEscapes("a\\u003Fb")).isEqualTo("a?b")
        // Lower-case hex digits too — JSON producers use both.
        assertThat(MediaUrl.decodeEscapes("a\\u002fb")).isEqualTo("a/b")
    }

    @Test
    fun `a clean URL is left exactly as it is`() {
        val clean = "https://v16-webapp-prime.tiktok.com/video/abc.mp4?a=1&b=2"
        assertThat(MediaUrl.decodeEscapes(clean)).isEqualTo(clean)
    }

    @Test
    fun `something that only looks like an escape is left alone`() {
        // Not an escape: no backslash, or too few hex digits. Decoding these would corrupt the URL.
        assertThat(MediaUrl.decodeEscapes("https://h/u002Fpath")).isEqualTo("https://h/u002Fpath")
        assertThat(MediaUrl.decodeEscapes("https://h/\\u02Fx")).isEqualTo("https://h/\\u02Fx")
    }
}
