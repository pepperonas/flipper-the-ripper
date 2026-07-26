package io.celox.flipperripper.domain.repository

import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.ThemeMode
import io.celox.flipperripper.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

/** Persists and observes user preferences. */
interface SettingsRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setAutoDownloadOnShare(enabled: Boolean)

    suspend fun setDefaultMode(mode: DownloadMode)

    suspend fun setClipboardDetection(enabled: Boolean)
}
