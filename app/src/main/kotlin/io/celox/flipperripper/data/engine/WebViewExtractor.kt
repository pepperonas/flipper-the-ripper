package io.celox.flipperripper.data.engine

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import io.celox.flipperripper.domain.model.Platform
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.selects.selectUnbiased
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** What the WebView managed to pull off a page. */
data class ExtractedMedia(val mediaUrl: String, val title: String?, val thumbnailUrl: String?)

/**
 * Extracts a direct video URL from platforms that only serve one to a real browser.
 *
 * Instagram fingerprints the TLS handshake and hydrates the media URL with JavaScript, so no plain HTTP
 * client — including the yt-dlp bundle (no curl_cffi) — can reach the video; they all get the login
 * wall. An Android [WebView] is real Chromium on the device's residential connection: it presents the
 * genuine Chrome fingerprint, runs the page JS and plays the embedded video, so the signed CDN URL can
 * be captured. (The residential IP matters: Instagram serves the *server's* datacenter IP a degraded
 * response, so the server fails on some reels the device gets.)
 *
 * Some reels are only visible to a signed-in account. Because every WebView shares one cookie store,
 * once the user signs in (see [InstagramSession]) this hidden WebView carries that session and the same
 * embed then renders the video — no separate code path needed.
 *
 * Two capture paths race: the video's own `.mp4` fetch seen via request interception when the embed
 * plays, and the URL scraped out of the embed's hydrated JSON. The signed URL is downloadable
 * afterwards with an ordinary client (the signature is the authorisation).
 */
@Singleton
class WebViewExtractor
@Inject
constructor(@ApplicationContext private val context: Context) {
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extract(url: String, platform: Platform): ExtractedMedia? =
        withContext(Dispatchers.Main) {
            val scraped = CompletableDeferred<ExtractedMedia?>()
            val interception = CompletableDeferred<String>()
            val shortcode = shortcodeOf(url)
            val pageUrl = embedUrlFor(url, platform, shortcode)
            CookieManager.getInstance().setAcceptCookie(true)

            val webView =
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.userAgentString = MOBILE_CHROME_UA
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webChromeClient =
                        object : WebChromeClient() {
                            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                                val msg = m.message()
                                if (msg.startsWith("FLIP:") && !scraped.isCompleted) {
                                    // Only complete on a real result, so it can race the interception path.
                                    parseScrapeResult(msg.removePrefix("FLIP:"))?.let { scraped.complete(it) }
                                }
                                return true
                            }
                        }
                    webViewClient =
                        object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): WebResourceResponse? {
                                request?.url?.toString()?.let { req ->
                                    if (isVideoCdnUrl(req) && !interception.isCompleted) {
                                        interception.complete(req)
                                    }
                                }
                                return null
                            }

                            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                view?.evaluateJavascript(EXTRACT_JS, null)
                            }
                        }
                }

            webView.loadUrl(pageUrl)

            // Whichever path yields a URL first wins; the scrape carries a title, interception does not.
            val media =
                withTimeoutOrNull(TIMEOUT_MS) {
                    selectUnbiased {
                        scraped.onAwait { it }
                        interception.onAwait { ExtractedMedia(it, null, null) }
                    }
                }

            webView.stopLoading()
            webView.destroy()
            if (media == null) Log.w(TAG, "extraction failed for $pageUrl")
            media
        }

    private fun parseScrapeResult(json: String?): ExtractedMedia? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val obj = JSONObject(json)
            val media = obj.optString("video_url").ifBlank { null } ?: return null
            ExtractedMedia(
                mediaUrl = media,
                title = obj.optString("title").ifBlank { null },
                thumbnailUrl = obj.optString("thumb").ifBlank { null },
            )
        }.getOrNull()
    }

    /** A reel renders (and, when visible to this session, plays) on its embed route; that is what we load. */
    private fun embedUrlFor(url: String, platform: Platform, shortcode: String?): String =
        when (platform) {
            Platform.INSTAGRAM ->
                shortcode?.let { "https://www.instagram.com/reel/$it/embed/captioned/" } ?: url
            else -> url
        }

    private fun shortcodeOf(url: String): String? =
        INSTAGRAM_CODE.find(url)?.groupValues?.getOrNull(2)?.takeIf { it.isNotBlank() }

    private fun isVideoCdnUrl(u: String): Boolean {
        val lower = u.lowercase()
        return lower.contains(".mp4") && VIDEO_CDN_HOSTS.any { lower.contains(it) }
    }

    private companion object {
        const val TAG = "WebViewExtractor"
        const val TIMEOUT_MS = 25_000L

        val VIDEO_CDN_HOSTS = listOf("cdninstagram.com", "fbcdn.net", "tiktokcdn", "muscdn.com")
        val INSTAGRAM_CODE = Regex("""instagram\.com/(reel|reels|p|tv)/([A-Za-z0-9_-]+)""")

        /**
         * Runs in the page: force the embed to play (so the CDN request fires for interception) and
         * scrape the hydrated media URL + title. `shortcode_media` is JSON-in-JSON, so the escapes are
         * undone before matching. The result is handed back over the console channel as a `FLIP:` line.
         */
        val EXTRACT_JS =
            """
            (function() {
              function meta(p){ var e=document.querySelector('meta[property="'+p+'"]'); return e?e.content:''; }
              function grab() {
                try {
                  var v = document.querySelector('video');
                  if (v) { try { v.muted = true; v.play(); } catch (e) {} }
                  var raw = document.documentElement.innerHTML
                    .replace(/\\u0026/g,'&').replace(/\\\//g,'/').replace(/\\"/g,'"');
                  var m = raw.match(/"video_url":"([^"]+)"/)
                       || raw.match(/"video_versions":\[\{[^}]*?"url":"([^"]+)"/)
                       || raw.match(/(https:\/\/[^"'\s]+?\.mp4[^"'\s]*)/);
                  if (!m && v && v.src && v.src.indexOf('http')===0) m=[null, v.src];
                  if (!m) return false;
                  var u = raw.match(/"owner":\{[^}]*?"username":"([^"]+)"/) || raw.match(/"username":"([^"]+)"/);
                  var title = meta('og:title') || (u ? 'Video by ' + u[1] : '');
                  console.log('FLIP:' + JSON.stringify({video_url:m[1], title:title, thumb:meta('og:image')}));
                  return true;
                } catch (e) { return false; }
              }
              if (!grab()) { setTimeout(grab, 1500); setTimeout(grab, 3500); }
            })();
            """.trimIndent()
    }
}
