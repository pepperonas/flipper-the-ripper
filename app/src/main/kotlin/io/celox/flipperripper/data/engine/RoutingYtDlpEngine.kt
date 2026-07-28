package io.celox.flipperripper.data.engine

import io.celox.flipperripper.di.ApplicationScope
import io.celox.flipperripper.di.OnDeviceEngine
import io.celox.flipperripper.di.RemoteEngine
import io.celox.flipperripper.di.WebViewEngine
import io.celox.flipperripper.domain.model.DownloadError
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.DownloadProgress
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.model.VideoInfo
import io.celox.flipperripper.domain.repository.BackendConfigRepository
import io.celox.flipperripper.domain.util.UrlParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The engine the rest of the app talks to. It picks the right engine per platform and falls back
 * automatically, so downloads work without the user choosing a source by hand:
 *
 *  - YouTube runs on the device (the server's IP is blocked by YouTube); a configured server is a
 *    fallback.
 *  - Instagram / TikTok run through the on-device WebView (the only on-device tool that clears their
 *    browser check); a configured server is a fallback for when the page changes or the post is walled.
 *
 * The order is decided by [EngineRouting]; each engine in it is tried until one succeeds. A genuinely
 * terminal result (private/unavailable) stops the chain — there is no point retrying those elsewhere.
 */
@Singleton
class RoutingYtDlpEngine
@Inject
constructor(
    @OnDeviceEngine private val local: YtDlpEngine,
    @WebViewEngine private val webView: YtDlpEngine,
    @RemoteEngine private val remote: YtDlpEngine,
    private val configRepository: BackendConfigRepository,
    @ApplicationScope scope: CoroutineScope,
) : YtDlpEngine {
    override val isReady: StateFlow<Boolean> =
        combine(local.isReady, webView.isReady) { l, w -> l || w }
            .stateIn(scope, SharingStarted.Eagerly, false)

    override suspend fun ensureInitialized(): EngineResult<Unit> {
        // The device is usable if either on-device path is; warm up yt-dlp but do not fail on it, since
        // WebView platforms do not need it.
        val local = local.ensureInitialized()
        return if (local is EngineResult.Success) local else webView.ensureInitialized()
    }

    override suspend fun fetchInfo(url: String, mode: DownloadMode): EngineResult<VideoInfo> =
        runChain(url) { engine -> engine.fetchInfo(url, mode) }

    override suspend fun download(
        spec: DownloadSpec,
        onProgress: (DownloadProgress) -> Unit,
    ): EngineResult<DownloadedFile> =
        runChain(spec.url) { engine -> engine.download(spec, onProgress) }

    /** Try each engine the routing selects until one succeeds or a terminal error stops the chain. */
    private suspend fun <T> runChain(
        url: String,
        action: suspend (YtDlpEngine) -> EngineResult<T>,
    ): EngineResult<T> {
        val platform =
            UrlParser.detectPlatform(url) ?: return EngineResult.Failure(DownloadError.InvalidUrl())
        val cfg = configRepository.config.first()
        val kinds = EngineRouting.order(platform, cfg.isServerConfigured, cfg.prefersServer)

        var last: EngineResult<T>? = null
        for (kind in kinds) {
            val result = action(engineFor(kind))
            if (result is EngineResult.Success) return result
            last = result
            if (result is EngineResult.Failure && result.error.isTerminal()) return result
        }
        return last ?: EngineResult.Failure(DownloadError.Unknown("No download engine was available."))
    }

    private fun engineFor(kind: EngineKind): YtDlpEngine =
        when (kind) {
            EngineKind.ON_DEVICE -> local
            EngineKind.WEB_VIEW -> webView
            EngineKind.SERVER -> remote
        }

    /** Errors where trying another engine cannot help — the content itself is the problem. */
    private fun DownloadError.isTerminal(): Boolean =
        this is DownloadError.PrivateVideo ||
            this is DownloadError.Unavailable ||
            this is DownloadError.RegionBlocked ||
            this is DownloadError.InvalidUrl ||
            this is DownloadError.Cancelled

    // Cancel every engine — the worker holds one processId and only the running one acts on it.
    override suspend fun cancel(processId: String) {
        local.cancel(processId)
        webView.cancel(processId)
        remote.cancel(processId)
    }

    override suspend fun update(): EngineResult<String> = local.update()
}
