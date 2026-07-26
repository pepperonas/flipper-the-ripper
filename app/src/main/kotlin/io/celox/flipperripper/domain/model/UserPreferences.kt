package io.celox.flipperripper.domain.model

/** Persisted user settings. */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    /** Automatically start the download once a shared/clipboard URL is resolved. */
    val autoDownloadOnShare: Boolean = true,
    val defaultMode: DownloadMode = DownloadMode.VIDEO,
    /** Offer to download a URL detected on the clipboard at app start. */
    val clipboardDetection: Boolean = true,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }
