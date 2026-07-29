package io.celox.flipperripper.data.engine

import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.Platform
import org.junit.Test

/**
 * The download must present the media's *own* site as `Referer`; a mismatched Referer is a CDN 403. This
 * pins the mapping the WebView downloader relies on.
 */
class PlatformWebTest {
    @Test
    fun `each web platform maps to its own site`() {
        assertThat(PlatformWeb.referer(Platform.INSTAGRAM)).isEqualTo("https://www.instagram.com/")
        assertThat(PlatformWeb.referer(Platform.TIKTOK)).isEqualTo("https://www.tiktok.com/")
        assertThat(PlatformWeb.referer(Platform.FACEBOOK)).isEqualTo("https://www.facebook.com/")
    }

    @Test
    fun `every platform yields a usable https referer`() {
        Platform.entries.forEach { p ->
            assertThat(PlatformWeb.referer(p)).startsWith("https://")
        }
    }

    @Test
    fun `facebook extraction uses a desktop user-agent, others mobile`() {
        // Facebook only embeds the direct video URL in its desktop page; a mobile UA gets a page without it.
        assertThat(webViewUserAgent(Platform.FACEBOOK)).isEqualTo(DESKTOP_CHROME_UA)
        assertThat(webViewUserAgent(Platform.FACEBOOK)).contains("Windows NT")
        assertThat(webViewUserAgent(Platform.INSTAGRAM)).isEqualTo(MOBILE_CHROME_UA)
        assertThat(webViewUserAgent(Platform.TIKTOK)).isEqualTo(MOBILE_CHROME_UA)
        assertThat(webViewUserAgent(Platform.TIKTOK)).contains("Mobile")
    }
}
