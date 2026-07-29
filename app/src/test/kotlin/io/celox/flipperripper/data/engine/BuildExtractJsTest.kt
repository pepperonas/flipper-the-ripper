package io.celox.flipperripper.data.engine

import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.domain.model.Platform
import org.junit.Test

/**
 * The page script emitted into the extractor WebView. These pin the contract each platform's on-device
 * extraction depends on: the right video-URL source is present, the media id / platform are baked in
 * correctly, and the public fallbacks are always retained.
 */
class BuildExtractJsTest {
    @Test
    fun `instagram bakes the media id in and queries the authenticated media API`() {
        val js = buildExtractJs(Platform.INSTAGRAM, "3946003153276453654")

        assertThat(js).contains("var MEDIA_ID = '3946003153276453654';")
        assertThat(js).contains("var PLATFORM = 'instagram';")
        assertThat(js).contains("/api/v1/media/'+MEDIA_ID+'/info/")
        // The web app id is what makes the API return JSON instead of the login HTML.
        assertThat(js).contains("'X-IG-App-ID':'936619743392459'")
        assertThat(js).contains("credentials: 'include'")
        assertThat(js).contains("video_versions")
    }

    @Test
    fun `skips the api call when there is no media id`() {
        val js = buildExtractJs(Platform.INSTAGRAM, null)

        assertThat(js).contains("var MEDIA_ID = null;")
        // apiGrab bails immediately on a null id, so the scrape path is the only source.
        assertThat(js).contains("if (!MEDIA_ID) return;")
    }

    @Test
    fun `tiktok parses the rehydration blob for the play address`() {
        val js = buildExtractJs(Platform.TIKTOK, null)

        assertThat(js).contains("var PLATFORM = 'tiktok';")
        assertThat(js).contains("__UNIVERSAL_DATA_FOR_REHYDRATION__")
        assertThat(js).contains("webapp.video-detail")
        assertThat(js).contains("playAddr")
        // The blob is only consulted for TikTok.
        assertThat(js).contains("if (PLATFORM === 'tiktok')")
    }

    @Test
    fun `facebook scrapes the native playable urls out of the page html`() {
        val js = buildExtractJs(Platform.FACEBOOK, null)

        assertThat(js).contains("var PLATFORM = 'facebook';")
        assertThat(js).contains("browser_native_hd_url")
        assertThat(js).contains("browser_native_sd_url")
        assertThat(js).contains("playable_url")
        // Facebook escapes % and & in its embedded URLs.
        assertThat(js).contains("\\\\u0025")
    }

    @Test
    fun `every platform keeps the scrape fallback, play nudge and single-report guard`() {
        for (p in listOf(Platform.INSTAGRAM, Platform.TIKTOK, Platform.FACEBOOK)) {
            val js = buildExtractJs(p, null)
            assertThat(js).contains("document.querySelector('video')")
            assertThat(js).contains(".play()")
            assertThat(js).contains("if (done || !url) return;")
            // Generic mp4 sweep is the last resort everywhere.
            assertThat(js).contains(".mp4")
        }
    }

    @Test
    fun `gives the api a head start over the scrape`() {
        val js = buildExtractJs(Platform.INSTAGRAM, "123")

        assertThat(js).contains("setTimeout(scrapeGrab, 1000)")
    }
}
