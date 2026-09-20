package io.celox.flipperripper.domain.model

/** Persisted user settings. */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    /** Automatically start the download once a shared/clipboard URL is resolved. */
    val autoDownloadOnShare: Boolean = true,
    /**
     * What a download asks for when nobody picks: a shared link, or the leading half of the split
     * button.
     *
     * This replaced a Video/Audio default. "Audio only" is a quality tier now, so one preference
     * covers what used to need two — and an existing Audio default is read forward rather than
     * quietly reset (see `SettingsRepositoryImpl`).
     */
    val defaultQuality: QualityChoice = QualityChoice.BEST,
    /** Offer to download a URL detected on the clipboard at app start. */
    val clipboardDetection: Boolean = true,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The collection a default download lands in — derived, never stored beside the quality. */
val UserPreferences.defaultMode: DownloadMode get() = FormatSelection.mode(defaultQuality)
