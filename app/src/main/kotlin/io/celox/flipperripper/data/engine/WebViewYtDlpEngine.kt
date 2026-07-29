package io.celox.flipperripper.data.engine

import android.webkit.CookieManager
import io.celox.flipperripper.di.IoDispatcher
import io.celox.flipperripper.domain.model.DownloadError
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.DownloadProgress
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.model.VideoInfo
import io.celox.flipperripper.domain.util.UrlParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device engine for the platforms that only serve a real browser: it drives [WebViewExtractor] to
 * obtain a direct video URL through Android's own Chromium, then streams that (signed, CDN-hosted) URL
 * to disk with an ordinary HTTP client. This is what lets Instagram — which the yt-dlp bundle cannot
 * reach without curl_cffi — download on the device with no server involved.
 */
@Singleton
class WebViewYtDlpEngine
@Inject
constructor(
    private val extractor: WebViewExtractor,
    private val instagramSession: InstagramSession,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : YtDlpEngine {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()

    private val _isReady = MutableStateFlow(true)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /** processId -> flag, so cancel() can abort an in-flight stream. */
    private val cancelled = ConcurrentHashMap<String, Boolean>()

    override suspend fun ensureInitialized(): EngineResult<Unit> = EngineResult.Success(Unit)

    override suspend fun fetchInfo(url: String, mode: DownloadMode): EngineResult<VideoInfo> {
        val platform =
            UrlParser.detectPlatform(url) ?: return EngineResult.Failure(DownloadError.InvalidUrl())
        val media =
            extractor.extract(url, platform)
                ?: return EngineResult.Failure(browserExtractionFailed())
        return EngineResult.Success(
            VideoInfo(
                sourceUrl = url,
                platform = platform,
                title = media.title?.takeIf { it.isNotBlank() } ?: "Untitled",
                uploader = null,
                thumbnailUrl = media.thumbnailUrl,
                durationSeconds = null,
                id = null,
            ),
        )
    }

    override suspend fun download(
        spec: DownloadSpec,
        onProgress: (DownloadProgress) -> Unit,
    ): EngineResult<DownloadedFile> {
        val media =
            extractor.extract(spec.url, spec.platform)
                ?: return EngineResult.Failure(browserExtractionFailed())

        return withContext(ioDispatcher) {
            prepareDir(spec.workingDir)
            val target = File(spec.workingDir, "${spec.processId}.mp4")
            try {
                streamToFile(spec, media.mediaUrl, target, onProgress)
            } catch (e: IOException) {
                return@withContext EngineResult.Failure(
                    DownloadError.Network("Could not download the video file: ${e.message}"),
                )
            } finally {
                cancelled.remove(spec.processId)
            }
            when {
                cancelled.remove(spec.processId) == true -> EngineResult.Failure(DownloadError.Cancelled())
                // Below a plausible video size the extractor grabbed the wrong asset (a cover image or
                // pixel). Fail rather than save junk, so the routing can fall through to the server.
                target.length() >= MIN_VIDEO_BYTES -> EngineResult.Success(DownloadedFile(target, "mp4"))
                else -> EngineResult.Failure(browserExtractionFailed())
            }
        }
    }

    private fun streamToFile(
        spec: DownloadSpec,
        mediaUrl: String,
        target: File,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        // Replicate exactly what the page's own <video> element sends. Instagram's CDN returns 403 to a
        // bare GET for a logged-in/gated clip: a real cross-site video request carries the instagram.com
        // Referer, a Range header (video is always range-requested), the Sec-Fetch metadata a browser
        // attaches, and — for gated media — the account's session cookies. Public reels tolerated a bare
        // GET; gated ones do not.
        val request =
            Request.Builder()
                .url(mediaUrl)
                .header("User-Agent", MOBILE_CHROME_UA)
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "identity")
                .header("Referer", "https://www.instagram.com/")
                .header("Range", "bytes=0-")
                .header("Sec-Fetch-Dest", "video")
                .header("Sec-Fetch-Mode", "no-cors")
                .header("Sec-Fetch-Site", "cross-site")
                .apply {
                    cookieHeaderFor(mediaUrl).takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
                }
                .get()
                .build()
        client.newCall(request).execute().use { resp ->
            val body = resp.body ?: throw IOException("empty response (${resp.code})")
            if (!resp.isSuccessful) {
                // Surface enough to tell apart the failure modes on-device without a logcat: the status
                // and which host refused it (a CDN 403 vs an instagram.com login redirect look different).
                val host = resp.request.url.host
                throw IOException("HTTP ${resp.code} from $host")
            }
            body.byteStream().use { input ->
                target.outputStream().use { out -> copyStream(spec, input, out, body.contentLength(), onProgress) }
            }
        }
    }

    /**
     * The cookies to send with the CDN media request: the CDN host's own cookies merged with the
     * instagram.com account session (see [InstagramCookies], which holds the reasoning and is unit-tested).
     */
    private fun cookieHeaderFor(mediaUrl: String): String {
        val cm = CookieManager.getInstance()
        return InstagramCookies.merge(cm.getCookie(mediaUrl), cm.getCookie("https://www.instagram.com"))
    }

    private fun copyStream(
        spec: DownloadSpec,
        input: java.io.InputStream,
        out: java.io.OutputStream,
        total: Long,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        val buffer = ByteArray(BUFFER)
        var copied = 0L
        var read = input.read(buffer)
        while (read >= 0 && cancelled[spec.processId] != true) {
            out.write(buffer, 0, read)
            copied += read
            if (total > 0) {
                onProgress(DownloadProgress((copied * 100f / total).coerceIn(0f, 100f), null, "transfer"))
            }
            read = input.read(buffer)
        }
    }

    override suspend fun cancel(processId: String) {
        cancelled[processId] = true
    }

    override suspend fun update(): EngineResult<String> = EngineResult.Success("WEBVIEW")

    private fun prepareDir(dir: File) {
        if (dir.exists()) dir.deleteRecursively()
        dir.mkdirs()
    }

    private fun browserExtractionFailed(): DownloadError =
        if (instagramSession.isLoggedIn()) {
            DownloadError.LoginRequired(
                "Could not read this video. The post may be unavailable, or the platform changed its page.",
            )
        } else {
            DownloadError.LoginRequired(
                "This video isn't available without signing in. Sign in to Instagram under " +
                    "Settings → Instagram, then try again.",
            )
        }

    private companion object {
        const val BUFFER = 64 * 1024

        /** A real short clip is comfortably above this; anything smaller is a cover image or an error. */
        const val MIN_VIDEO_BYTES = 50_000L
    }
}
