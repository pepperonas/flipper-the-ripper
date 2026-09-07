package io.celox.flipperripper.data.engine

import io.celox.flipperripper.domain.model.Platform

/** The engines available to route between. */
enum class EngineKind { ON_DEVICE, WEB_VIEW, SERVER }

/**
 * Which engines to try, in order, for a platform — the pure decision behind [RoutingYtDlpEngine].
 *
 * The two on-device engines fail in opposite ways, so the platform decides the primary:
 *  - **YouTube** downloads on the device (bundled yt-dlp + QuickJS), but the *server's* datacenter IP
 *    is blocked by YouTube — so on-device first.
 *  - **X / Dailymotion** are served by yt-dlp's guest APIs (X: guest token + GraphQL, Dailymotion: a
 *    public HLS manifest) — no browser fingerprint check, no JS challenge — so the bundled yt-dlp is
 *    the primary here too (verified with a PATH-stripped desktop yt-dlp = the on-device toolchain).
 *  - **Instagram / TikTok / Facebook** fingerprint the TLS handshake and hydrate the URL with JS; only a
 *    real browser gets through, which on the device means the WebView — so WebView first. The yt-dlp
 *    bundle cannot do these at all (no curl_cffi).
 *
 * The server is appended as a fallback whenever one is configured: it runs the full toolchain
 * (curl_cffi impersonation, a JS runtime, ffmpeg), so it can catch what the primary missed — except
 * where its IP is the very thing being blocked, which is why it is never the primary for YouTube.
 */
object EngineRouting {
    /** The engine a platform is tried on first when the user has expressed no preference. */
    fun primary(platform: Platform): EngineKind =
        when (platform) {
            Platform.YOUTUBE, Platform.X, Platform.DAILYMOTION -> EngineKind.ON_DEVICE
            Platform.INSTAGRAM, Platform.TIKTOK, Platform.FACEBOOK -> EngineKind.WEB_VIEW
        }

    /**
     * Whether the platform is extracted by the bundled yt-dlp — the set that a yt-dlp update can fix
     * (see [StaleEngineRetry]) and that the WebView machinery never touches.
     */
    fun usesYtDlp(platform: Platform): Boolean = primary(platform) == EngineKind.ON_DEVICE

    /**
     * @param serverConfigured a server URL + key are set, so the server may be used as a fallback.
     * @param preferServer the user explicitly picked the server as their source, so it goes first.
     */
    fun order(platform: Platform, serverConfigured: Boolean, preferServer: Boolean): List<EngineKind> {
        val primary = primary(platform)
        return when {
            preferServer -> listOf(EngineKind.SERVER, primary)
            serverConfigured -> listOf(primary, EngineKind.SERVER)
            else -> listOf(primary)
        }
    }
}
