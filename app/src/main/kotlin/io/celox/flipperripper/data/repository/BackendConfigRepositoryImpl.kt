package io.celox.flipperripper.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.celox.flipperripper.BuildConfig
import io.celox.flipperripper.domain.model.BackendConfig
import io.celox.flipperripper.domain.model.DownloadSource
import io.celox.flipperripper.domain.repository.BackendConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.backendStore: DataStore<Preferences> by preferencesDataStore(name = "backend")

@Singleton
class BackendConfigRepositoryImpl
@Inject
constructor(@ApplicationContext private val context: Context) : BackendConfigRepository {
    private object Keys {
        val SOURCE = stringPreferencesKey("download_source")
        val URL = stringPreferencesKey("backend_url")
        val KEY = stringPreferencesKey("backend_key")
    }

    // Defaults come from the (git-ignored) backend.properties baked into BuildConfig. When a server
    // URL is baked in, default to using it; otherwise default to on-device.
    private val defaultUrl = BuildConfig.DEFAULT_BACKEND_URL
    private val defaultKey = BuildConfig.DEFAULT_BACKEND_KEY
    private val defaultSource =
        if (defaultUrl.isNotBlank() && defaultKey.isNotBlank()) DownloadSource.SERVER else DownloadSource.ON_DEVICE

    override val config: Flow<BackendConfig> =
        context.backendStore.data.map { prefs ->
            BackendConfig(
                source = prefs[Keys.SOURCE]?.let { runCatching { DownloadSource.valueOf(it) }.getOrNull() }
                    ?: defaultSource,
                url = prefs[Keys.URL] ?: defaultUrl,
                apiKey = prefs[Keys.KEY] ?: defaultKey,
            )
        }

    override suspend fun setSource(source: DownloadSource) {
        context.backendStore.edit { it[Keys.SOURCE] = source.name }
    }

    override suspend fun setServer(url: String, apiKey: String) {
        context.backendStore.edit {
            it[Keys.URL] = url.trim()
            it[Keys.KEY] = apiKey.trim()
        }
    }
}
