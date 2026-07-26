package io.celox.flipperripper.domain.repository

import io.celox.flipperripper.domain.model.BackendConfig
import io.celox.flipperripper.domain.model.DownloadSource
import kotlinx.coroutines.flow.Flow

/** Persists and observes the optional server-backend configuration. */
interface BackendConfigRepository {
    val config: Flow<BackendConfig>

    suspend fun setSource(source: DownloadSource)

    suspend fun setServer(url: String, apiKey: String)
}
