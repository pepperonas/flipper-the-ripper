package io.celox.flipperripper.data.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The rule that keeps the hidden extractor on the page it was asked to read.
 *
 * TikTok broke downloads by answering a mobile browser with a redirect to
 * `snssdk1340://aweme/detail/…`, a deep link meant to hand the visit to the native app. The WebView
 * followed it, could not load the scheme, and ended up on `chrome-error://chromewebdata` — the video
 * page gone, the only symptom a timeout with no cause named.
 */
class WebNavigationTest {
    @Test
    fun `web pages load`() {
        listOf("http", "https", "HTTPS", "Http").forEach {
            assertThat(WebNavigation.isLoadablePage(it)).isTrue()
        }
    }

    @Test
    fun `the TikTok app deep link is refused`() {
        // The exact scheme that broke TikTok downloads.
        assertThat(WebNavigation.isLoadablePage("snssdk1340")).isFalse()
    }

    @Test
    fun `no scheme hands the visit to another app`() {
        // A hidden extractor exists to read one page. Nothing here may open anything on the user's
        // behalf, and every one of these would abandon the page we are reading.
        listOf("intent", "market", "tel", "mailto", "sms", "fb", "instagram", "vnd.youtube", "tiktok")
            .forEach { assertThat(WebNavigation.isLoadablePage(it)).isFalse() }
    }

    @Test
    fun `a missing scheme is not loadable`() {
        assertThat(WebNavigation.isLoadablePage(null)).isFalse()
        assertThat(WebNavigation.isLoadablePage("")).isFalse()
    }

    @Test
    fun `the error page the WebView falls back to is itself not loadable`() {
        // chrome-error:// is where following an unloadable scheme lands; it must never count as a page.
        assertThat(WebNavigation.isLoadablePage("chrome-error")).isFalse()
        assertThat(WebNavigation.isLoadablePage("about")).isFalse()
        assertThat(WebNavigation.isLoadablePage("data")).isFalse()
    }
}
