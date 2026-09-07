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
    fun `detects facebook across its host variants`() {
        assertThat(UrlParser.detectPlatform("https://www.facebook.com/page/videos/123/"))
            .isEqualTo(Platform.FACEBOOK)
        assertThat(UrlParser.detectPlatform("https://web.facebook.com/watch?v=123")).isEqualTo(Platform.FACEBOOK)
        assertThat(UrlParser.detectPlatform("https://m.facebook.com/reel/123")).isEqualTo(Platform.FACEBOOK)
        // Share short links come on their own hosts.
        assertThat(UrlParser.detectPlatform("https://fb.watch/abc123/")).isEqualTo(Platform.FACEBOOK)
        assertThat(UrlParser.detectPlatform("https://fb.com/x/videos/1")).isEqualTo(Platform.FACEBOOK)
    }

    @Test
    fun `detects x across its current and legacy hosts`() {
        assertThat(UrlParser.detectPlatform("https://x.com/SpaceX/status/2095087210479382726")).isEqualTo(Platform.X)
        assertThat(UrlParser.detectPlatform("https://www.x.com/SpaceX/status/1")).isEqualTo(Platform.X)
        assertThat(UrlParser.detectPlatform("https://twitter.com/SpaceX/status/1")).isEqualTo(Platform.X)
        assertThat(UrlParser.detectPlatform("https://mobile.twitter.com/SpaceX/status/1")).isEqualTo(Platform.X)
        assertThat(UrlParser.detectPlatform("https://x.com/i/status/1")).isEqualTo(Platform.X)
    }

    @Test
    fun `detects dailymotion long and short hosts`() {
        assertThat(UrlParser.detectPlatform("https://www.dailymotion.com/video/xb4g82i")).isEqualTo(Platform.DAILYMOTION)
        assertThat(UrlParser.detectPlatform("https://dailymotion.com/video/xb4g82i")).isEqualTo(Platform.DAILYMOTION)
        assertThat(UrlParser.detectPlatform("https://dai.ly/xb4g82i")).isEqualTo(Platform.DAILYMOTION)
    }

    @Test
    fun `a host is matched as a registrable domain, never as a substring`() {
        // "x.com" is a suffix of many unrelated hosts — a substring match would claim them all.
        assertThat(UrlParser.detectPlatform("https://www.netflix.com/watch/1")).isNull()
        assertThat(UrlParser.detectPlatform("https://www.dropbox.com/s/abc")).isNull()
        assertThat(UrlParser.detectPlatform("https://myx.com/1")).isNull()
        // A look-alike that merely *contains* a supported host must not pass either.
        assertThat(UrlParser.detectPlatform("https://youtube.com.evil.example/watch")).isNull()
        assertThat(UrlParser.detectPlatform("https://notyoutube.com/watch")).isNull()
        // …while genuine subdomains still do.
        assertThat(UrlParser.detectPlatform("https://music.youtube.com/watch?v=x")).isEqualTo(Platform.YOUTUBE)
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
