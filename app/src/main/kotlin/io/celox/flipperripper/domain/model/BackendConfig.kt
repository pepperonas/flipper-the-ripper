package io.celox.flipperripper.domain.model

/** Where downloads run: the on-device bundled yt-dlp, or a server backend. */
enum class DownloadSource { ON_DEVICE, SERVER }

/**
 * Optional server-backend configuration. When [source] is [DownloadSource.SERVER] and a URL + key are
 * set, extraction/downloading is delegated to the backend (which runs the full yt-dlp toolchain, so it
 * handles YouTube's PO-token / n-sig and TikTok impersonation that stock Android can't).
 */
data class BackendConfig(val source: DownloadSource, val url: String, val apiKey: String) {
    /** A server is set up and can be used (as an automatic fallback, or as the explicit primary). */
    val isServerConfigured: Boolean
        get() = url.isNotBlank() && apiKey.isNotBlank()

    /** The user explicitly chose the server as their preferred source. */
    val prefersServer: Boolean
        get() = source == DownloadSource.SERVER && isServerConfigured

    @Deprecated("Routing is per-platform now; use isServerConfigured / prefersServer.")
    val isServerUsable: Boolean
        get() = prefersServer
}
