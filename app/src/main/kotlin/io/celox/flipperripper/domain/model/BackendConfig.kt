package io.celox.flipperripper.domain.model

/** Where downloads run: the on-device bundled yt-dlp, or a server backend. */
enum class DownloadSource { ON_DEVICE, SERVER }

/**
 * Optional server-backend configuration. When [source] is [DownloadSource.SERVER] and a URL + key are
 * set, extraction/downloading is delegated to the backend (which runs the full yt-dlp toolchain, so it
 * handles YouTube's PO-token / n-sig and TikTok impersonation that stock Android can't).
 */
data class BackendConfig(val source: DownloadSource, val url: String, val apiKey: String) {
    val isServerUsable: Boolean
        get() = source == DownloadSource.SERVER && url.isNotBlank() && apiKey.isNotBlank()
}
