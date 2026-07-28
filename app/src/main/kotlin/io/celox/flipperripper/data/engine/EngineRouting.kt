package io.celox.flipperripper.data.engine

import io.celox.flipperripper.domain.model.Platform

/** The engines available to route between. */
enum class EngineKind { ON_DEVICE, WEB_VIEW, SERVER }

/**
 * Which engines to try, in order, for a platform — the pure decision behind [RoutingYtDlpEngine].
 *
 * The two on-device engines fail in opposite ways, so the platform decides the primary:
 *  - **YouTube** downloads on the device (the `android_vr` yt-dlp client), but the *server's* datacenter
 *    IP is blocked by YouTube — so on-device first.
 *  - **Instagram / TikTok / Facebook** fingerprint the TLS handshake and hydrate the URL with JS; only a
 *    real browser gets through, which on the device means the WebView — so WebView first. The yt-dlp
 *    bundle cannot do these at all (no curl_cffi).
 *
 * The server is appended as a fallback whenever one is configured: it runs the full toolchain
 * (curl_cffi impersonation, a JS runtime, ffmpeg), so it can catch what the primary missed — except
 * where its IP is the very thing being blocked, which is why it is never the primary for YouTube.
 */
object EngineRouting {
    /**
     * @param serverConfigured a server URL + key are set, so the server may be used as a fallback.
     * @param preferServer the user explicitly picked the server as their source, so it goes first.
     */
    fun order(platform: Platform, serverConfigured: Boolean, preferServer: Boolean): List<EngineKind> {
        val primary =
            when (platform) {
                Platform.YOUTUBE -> EngineKind.ON_DEVICE
                Platform.INSTAGRAM, Platform.TIKTOK -> EngineKind.WEB_VIEW
            }
        return when {
            preferServer -> listOf(EngineKind.SERVER, primary)
            serverConfigured -> listOf(primary, EngineKind.SERVER)
            else -> listOf(primary)
        }
    }
}
