package io.celox.flipperripper.data.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The page script emitted into the extractor WebView. These guard the contract the on-device fix depends
 * on: the authenticated media API is queried with the right app id when a media id is known, and is
 * skipped (leaving only the public scrape) when it is not.
 */
class BuildExtractJsTest {
    @Test
    fun `bakes the media id in and queries the authenticated media API`() {
        val js = buildExtractJs("3946003153276453654")

        assertThat(js).contains("var MEDIA_ID = '3946003153276453654';")
        assertThat(js).contains("/api/v1/media/'+MEDIA_ID+'/info/")
        // The web app id is what makes the API return JSON instead of the login HTML.
        assertThat(js).contains("'X-IG-App-ID':'936619743392459'")
        // Must send the session and prefer the authorized video_versions URL.
        assertThat(js).contains("credentials: 'include'")
        assertThat(js).contains("video_versions")
    }

    @Test
    fun `skips the api call when there is no media id`() {
        val js = buildExtractJs(null)

        assertThat(js).contains("var MEDIA_ID = null;")
        // apiGrab bails immediately on a null id, so the scrape path is the only source.
        assertThat(js).contains("if (!MEDIA_ID) return;")
    }

    @Test
    fun `always keeps the public scrape fallback and its play nudge`() {
        // Both a signed-in (id present) and signed-out (id null) build must retain the embed scrape.
        for (js in listOf(buildExtractJs("123"), buildExtractJs(null))) {
            assertThat(js).contains("\"video_url\":\"")
            assertThat(js).contains("document.querySelector('video')")
            assertThat(js).contains(".play()")
            // The single-report guard keeps the API winner from being overwritten by the later scrape.
            assertThat(js).contains("if (done || !url) return;")
        }
    }

    @Test
    fun `gives the api a head start over the scrape`() {
        val js = buildExtractJs("123")

        // apiGrab is invoked immediately; the scrape is deferred so the authorized URL can win.
        assertThat(js).contains("setTimeout(scrapeGrab, 1000)")
    }
}
