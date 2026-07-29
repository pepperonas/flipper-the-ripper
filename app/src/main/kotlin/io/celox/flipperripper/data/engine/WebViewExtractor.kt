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
            val mediaId = shortcode?.let { InstagramMediaId.fromShortcode(it) }
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
                                view?.evaluateJavascript(buildExtractJs(mediaId), null)
                            }
                        }
                }

            webView.loadUrl(pageUrl)

            // Prefer an explicitly resolved URL — the API's authorized URL when signed in, else the embed
            // scrape — because that is the one that actually downloads. Only fall back to a raw
            // intercepted CDN request if no resolved URL arrives within the grace window (the embed's
            // <video> can request an unauthorized/preview asset that 403s). The resolved path also
            // carries a title; interception does not.
            val media =
                withTimeoutOrNull(TIMEOUT_MS) {
                    withTimeoutOrNull(SCRAPE_GRACE_MS) { scraped.await() }
                        ?: ExtractedMedia(interception.await(), null, null)
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

        /** How long to wait for an authorized (API/scrape) URL before falling back to interception. */
        const val SCRAPE_GRACE_MS = 6_000L

        val VIDEO_CDN_HOSTS = listOf("cdninstagram.com", "fbcdn.net", "tiktokcdn", "muscdn.com")
        val INSTAGRAM_CODE = Regex("""instagram\.com/(reel|reels|p|tv)/([A-Za-z0-9_-]+)""")
    }
}

/**
 * The page script, with the Kotlin-computed [mediaId] baked in. It hands results back over the console
 * channel as a `FLIP:` line and draws from two sources, in order of authority:
 *  1. Instagram's own media API (`/api/v1/media/<id>/info/`). This is *same-origin* from the embed page,
 *     so the fetch carries the signed-in session, uses Chromium's TLS and isn't CORS-blocked — and it
 *     returns the correctly authorized `video_versions` URL. The embed's scraped URL is NOT authorized for
 *     logged-in/gated reels (it 403s on download); the API URL is. When signed out the API returns HTML,
 *     so this yields nothing and we fall back. Skipped when [mediaId] is null (non-reel / unparseable).
 *  2. Scraping the embed's hydrated JSON (`shortcode_media`, JSON-in-JSON → unescape first). This is what
 *     public reels use and needs no session.
 * The `<video>` is also nudged to play so its CDN request fires for the interception path.
 *
 * [mediaId] is always a validated base-10 number ([InstagramMediaId]) or null, so baking it straight into
 * the script carries no injection risk.
 */
internal fun buildExtractJs(mediaId: String?): String {
    val idLiteral = mediaId?.let { "'$it'" } ?: "null"
    return """
        (function() {
          var MEDIA_ID = $idLiteral;
          var done = false;
          function report(url, title, thumb) {
            if (done || !url) return;
            done = true;
            console.log('FLIP:' + JSON.stringify({video_url:url, title:title||'', thumb:thumb||''}));
          }
          function meta(p){ var e=document.querySelector('meta[property="'+p+'"]'); return e?e.content:''; }
          function apiGrab() {
            if (!MEDIA_ID) return;
            try {
              fetch('/api/v1/media/'+MEDIA_ID+'/info/', {
                headers: {'X-IG-App-ID':'936619743392459'}, credentials: 'include'
              }).then(function(r){
                var ct = r.headers.get('content-type')||'';
                if (ct.indexOf('json') < 0) return null;   // signed out -> HTML, ignore
                return r.json();
              }).then(function(j){
                if (!j) return;
                var it = j.items && j.items[0]; if (!it) return;
                var vv = it.video_versions && it.video_versions[0] && it.video_versions[0].url;
                if (!vv) return;
                var title = (it.user && it.user.username) ? 'Video by ' + it.user.username : '';
                var thumb = it.image_versions2 && it.image_versions2.candidates
                            && it.image_versions2.candidates[0] && it.image_versions2.candidates[0].url;
                report(vv, title, thumb);
              }).catch(function(){});
            } catch (e) {}
          }
          function scrapeGrab() {
            try {
              var v = document.querySelector('video');
              if (v) { try { v.muted = true; v.play(); } catch (e) {} }
              var raw = document.documentElement.innerHTML
                .replace(/\\u0026/g,'&').replace(/\\\//g,'/').replace(/\\"/g,'"');
              var m = raw.match(/"video_url":"([^"]+)"/)
                   || raw.match(/"video_versions":\[\{[^}]*?"url":"([^"]+)"/)
                   || raw.match(/(https:\/\/[^"'\s]+?\.mp4[^"'\s]*)/);
              if (!m && v && v.src && v.src.indexOf('http')===0) m=[null, v.src];
              if (!m) return;
              var u = raw.match(/"owner":\{[^}]*?"username":"([^"]+)"/) || raw.match(/"username":"([^"]+)"/);
              var title = meta('og:title') || (u ? 'Video by ' + u[1] : '');
              report(m[1], title, meta('og:image'));
            } catch (e) {}
          }
          // Give the authoritative same-origin API a ~1s head start; the `done` guard means the
          // embed scrape only reports if the API didn't (signed out / public reel).
          apiGrab();
          setTimeout(scrapeGrab, 1000);
          setTimeout(function(){ apiGrab(); scrapeGrab(); }, 3000);
        })();
    """.trimIndent()
}
