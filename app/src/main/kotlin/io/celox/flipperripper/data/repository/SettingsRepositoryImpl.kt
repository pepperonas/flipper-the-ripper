package io.celox.flipperripper.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.ThemeMode
import io.celox.flipperripper.domain.model.UserPreferences
import io.celox.flipperripper.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl
@Inject
constructor(@ApplicationContext private val context: Context) : SettingsRepository {
    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val AUTO_DOWNLOAD = booleanPreferencesKey("auto_download_on_share")
        val DEFAULT_MODE = stringPreferencesKey("default_mode")
        val CLIPBOARD = booleanPreferencesKey("clipboard_detection")
        val LAST_ENGINE_UPDATE = longPreferencesKey("last_engine_update_ms")
    }

    override val preferences: Flow<UserPreferences> =
        context.dataStore.data.map { prefs ->
            UserPreferences(
                themeMode = prefs[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: ThemeMode.SYSTEM,
                useDynamicColor = prefs[Keys.DYNAMIC] ?: true,
                autoDownloadOnShare = prefs[Keys.AUTO_DOWNLOAD] ?: true,
                defaultMode = prefs[Keys.DEFAULT_MODE]?.let { runCatching { DownloadMode.valueOf(it) }.getOrNull() }
                    ?: DownloadMode.VIDEO,
                clipboardDetection = prefs[Keys.CLIPBOARD] ?: true,
            )
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC] = enabled }
    }

    override suspend fun setAutoDownloadOnShare(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_DOWNLOAD] = enabled }
    }

    override suspend fun setDefaultMode(mode: DownloadMode) {
        context.dataStore.edit { it[Keys.DEFAULT_MODE] = mode.name }
    }

    override suspend fun setClipboardDetection(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CLIPBOARD] = enabled }
    }

    override val lastEngineUpdateMs: Flow<Long> =
        context.dataStore.data.map { it[Keys.LAST_ENGINE_UPDATE] ?: 0L }

    override suspend fun setLastEngineUpdateMs(epochMs: Long) {
        context.dataStore.edit { it[Keys.LAST_ENGINE_UPDATE] = epochMs }
    }
}
