package io.celox.flipperripper.data.engine

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import io.celox.flipperripper.domain.model.Platform
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** What the WebView managed to pull off a page. */
data class ExtractedMedia(val mediaUrl: String, val title: String?, val thumbnailUrl: String?)

/**
 * Extracts a direct video URL from platforms that only serve one to a real browser.
 *
 * Instagram, TikTok and Facebook fingerprint the TLS handshake and hydrate the media URL with
 * JavaScript, so no plain HTTP client — including the yt-dlp bundle on the device — can reach the
 * video: they all get the login wall. An Android [WebView] *is* Chromium, so it presents the genuine
 * Chrome fingerprint and runs the page's JS. It is the only on-device tool that gets past the wall.
 *
 * The signed CDN URL it yields is then downloadable with an ordinary client (the signature is the
 * authorisation), which is why extraction and download can be split.
 *
 * Two independent capture paths race, and whichever fires first wins:
 *  1. request interception — the `<video>` element's fetch of the `.mp4` from the CDN, and
 *  2. DOM/HTML scraping — the `video_url` Instagram hydrates into the page, read back via injected JS.
 */
@Singleton
class WebViewExtractor
@Inject
constructor(@ApplicationContext private val context: Context) {
    /**
     * Load [url] in a hidden WebView and return the first usable media URL, or null on timeout/failure.
     * Must be safe to call from any thread; it hops to the main thread for all WebView work.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extract(url: String, platform: Platform): ExtractedMedia? =
        withContext(Dispatchers.Main) {
            val found = CompletableDeferred<String>()
            // Metadata comes from the page's JS; it often resolves a beat after the media URL, so it is
            // gathered independently and merged with a short grace period rather than racing the media.
            val metadata = CompletableDeferred<Pair<String?, String?>>()
            val pageUrl = embedUrlFor(url, platform)

            val webView =
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.userAgentString = MOBILE_CHROME_UA
                    webViewClient =
                        object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): WebResourceResponse? {
                                request?.url?.toString()?.let { req ->
                                    if (isVideoCdnUrl(req) && !found.isCompleted) found.complete(req)
                                }
                                return null
                            }

                            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                // Nudge the video to play (so the CDN request fires) and read the
                                // hydrated URL + metadata straight out of the rendered page.
                                view?.evaluateJavascript(EXTRACT_JS) { raw ->
                                    parseJsResult(raw)?.let { (media, title, thumb) ->
                                        if (media != null && !found.isCompleted) found.complete(media)
                                        if (!metadata.isCompleted) metadata.complete(title to thumb)
                                    }
                                }
                            }
                        }
                }

            webView.loadUrl(pageUrl)

            val mediaUrl = withTimeoutOrNull(TIMEOUT_MS) { found.await() }
            // Give the title/thumbnail a brief moment even though the media URL already arrived.
            val (title, thumb) =
                if (mediaUrl != null) {
                    withTimeoutOrNull(METADATA_GRACE_MS) { metadata.await() } ?: (null to null)
                } else {
                    null to null
                }

            webView.stopLoading()
            webView.destroy()

            mediaUrl?.let { ExtractedMedia(it, title, thumb) }
        }

    /** A public reel/post renders without login on its embed route; that is what we load. */
    private fun embedUrlFor(url: String, platform: Platform): String =
        when (platform) {
            Platform.INSTAGRAM -> {
                val code = INSTAGRAM_CODE.find(url)?.groupValues?.getOrNull(2)
                if (code != null) "https://www.instagram.com/reel/$code/embed/captioned/" else url
            }
            else -> url
        }

    private fun isVideoCdnUrl(u: String): Boolean {
        val lower = u.lowercase()
        // Require an actual .mp4 on a known video CDN. A looser match grabbed cover images and tracking
        // pixels (e.g. a 13 KB file that is plainly not a video), so keep it strict.
        return lower.contains(".mp4") && VIDEO_CDN_HOSTS.any { lower.contains(it) }
    }

    /** JS result is a JSON-ish string; pull the three fields without a JSON dependency on the UI thread. */
    private fun parseJsResult(raw: String?): Triple<String?, String?, String?>? {
        if (raw == null || raw == "null") return null
        val unescaped = raw.trim('"').replace("\\\"", "\"").replace("\\/", "/").replace("\\u0026", "&")
        val media = FIELD_VIDEO.find(unescaped)?.groupValues?.getOrNull(1).clean()
        val title = FIELD_TITLE.find(unescaped)?.groupValues?.getOrNull(1).clean()
        val thumb = FIELD_THUMB.find(unescaped)?.groupValues?.getOrNull(1).clean()
        if (media == null && title == null && thumb == null) return null
        return Triple(media, title, thumb)
    }

    /** A field captured from the JSON-ish blob is unusable if empty or the literal `null`/`undefined`. */
    private fun String?.clean(): String? =
        this?.trim()?.takeIf { it.isNotBlank() && it != "null" && it != "undefined" }

    private companion object {
        const val TIMEOUT_MS = 25_000L
        const val METADATA_GRACE_MS = 4_000L
        const val MOBILE_CHROME_UA =
            "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Mobile Safari/537.36"

        // Instagram/Facebook video CDN hosts.
        val VIDEO_CDN_HOSTS = listOf("cdninstagram.com", "fbcdn.net", "tiktokcdn", "muscdn.com")

        val INSTAGRAM_CODE = Regex("""instagram\.com/(reel|reels|p|tv)/([A-Za-z0-9_-]+)""")

        // Read the hydrated data back out of the rendered page and nudge the video to play.
        val EXTRACT_JS =
            """
            (function() {
              try {
                var v = document.querySelector('video');
                if (v) { try { v.muted = true; v.play(); } catch (e) {} }
                var html = document.documentElement.innerHTML;
                var vurl = null;
                var m = html.match(/"video_url":"([^"]+)"/) || html.match(/"contentUrl":"([^"]+\.mp4[^"]*)"/);
                if (m) vurl = m[1];
                if (!vurl && v && v.src && v.src.indexOf('http') === 0) vurl = v.src;
                var title = '';
                var og = document.querySelector('meta[property="og:title"]');
                if (og && og.content) title = og.content;
                if (!title) {
                  // The embed page shows the poster's handle rather than an og:title.
                  var u = document.querySelector('.UsernameText, .Username, a[href*="instagram.com/"] span');
                  if (u && u.textContent) title = 'Video by ' + u.textContent.trim();
                }
                var thm = document.querySelector('meta[property="og:image"]');
                var poster = v ? v.getAttribute('poster') : null;
                return JSON.stringify({
                  video_url: vurl || '',
                  title: title || '',
                  thumb: (thm ? thm.content : (poster || ''))
                });
              } catch (e) { return null; }
            })();
            """.trimIndent()

        val FIELD_VIDEO = Regex(""""video_url":"?([^",}]+)""")
        val FIELD_TITLE = Regex(""""title":"?([^",}]+)""")
        val FIELD_THUMB = Regex(""""thumb":"?([^",}]+)""")
    }
}
