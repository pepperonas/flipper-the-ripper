package io.celox.flipperripper.domain.util

import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.Platform
import org.junit.Test

class UrlParserTest {
    @Test
    fun `detects youtube long and short hosts`() {
        assertThat(UrlParser.detectPlatform("https://www.youtube.com/watch?v=abc")).isEqualTo(Platform.YOUTUBE)
        assertThat(UrlParser.detectPlatform("https://youtu.be/abc")).isEqualTo(Platform.YOUTUBE)
        assertThat(UrlParser.detectPlatform("https://m.youtube.com/shorts/abc")).isEqualTo(Platform.YOUTUBE)
    }

    @Test
    fun `detects instagram and tiktok`() {
        assertThat(UrlParser.detectPlatform("https://www.instagram.com/reel/abc/")).isEqualTo(Platform.INSTAGRAM)
        assertThat(UrlParser.detectPlatform("https://vm.tiktok.com/ZMabc/")).isEqualTo(Platform.TIKTOK)
        assertThat(UrlParser.detectPlatform("https://www.tiktok.com/@user/video/123")).isEqualTo(Platform.TIKTOK)
    }

    @Test
    fun `rejects unsupported and non-http`() {
        assertThat(UrlParser.detectPlatform("https://vimeo.com/123")).isNull()
        assertThat(UrlParser.detectPlatform("ftp://youtube.com/x")).isNull()
        assertThat(UrlParser.detectPlatform("not a url")).isNull()
        assertThat(UrlParser.detectPlatform("")).isNull()
        assertThat(UrlParser.detectPlatform(null)).isNull()
    }

    @Test
    fun `extracts url from messy shared text`() {
        val text = "Check this out! https://youtu.be/dQw4w9WgXcQ?si=abc 🔥"
        val parsed = UrlParser.extractSupported(text)
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.platform).isEqualTo(Platform.YOUTUBE)
        assertThat(parsed.url).isEqualTo("https://youtu.be/dQw4w9WgXcQ?si=abc")
    }

    @Test
    fun `preserves query params but strips trailing punctuation`() {
        val parsed = UrlParser.extractSupported("(via https://www.instagram.com/reel/abc/?igsh=xyz).")
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.url).isEqualTo("https://www.instagram.com/reel/abc/?igsh=xyz")
    }

    @Test
    fun `picks the first supported url even if a non-supported one comes first`() {
        val text = "https://example.com/foo then https://www.tiktok.com/@a/video/9"
        val parsed = UrlParser.extractSupported(text)
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.platform).isEqualTo(Platform.TIKTOK)
    }

    @Test
    fun `returns null when no supported url present`() {
        assertThat(UrlParser.extractSupported("just some text https://example.com")).isNull()
        assertThat(UrlParser.extractSupported("")).isNull()
        assertThat(UrlParser.extractSupported(null)).isNull()
    }

    @Test
    fun `handles userinfo and port in authority`() {
        assertThat(UrlParser.detectPlatform("https://user@www.youtube.com:443/watch?v=x")).isEqualTo(Platform.YOUTUBE)
    }
}
