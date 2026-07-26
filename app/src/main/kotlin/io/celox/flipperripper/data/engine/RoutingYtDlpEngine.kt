package io.celox.flipperripper.data.engine

import io.celox.flipperripper.di.ApplicationScope
import io.celox.flipperripper.di.OnDeviceEngine
import io.celox.flipperripper.di.RemoteEngine
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.DownloadProgress
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.model.VideoInfo
import io.celox.flipperripper.domain.repository.BackendConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The engine the rest of the app talks to. It delegates each call to either the on-device engine or
 * the server backend, chosen live from [BackendConfigRepository] — so switching the source in Settings
 * takes effect immediately, no restart.
 */
@Singleton
class RoutingYtDlpEngine
@Inject
constructor(
    @OnDeviceEngine private val local: YtDlpEngine,
    @RemoteEngine private val remote: YtDlpEngine,
    private val configRepository: BackendConfigRepository,
    @ApplicationScope scope: CoroutineScope,
) : YtDlpEngine {
    override val isReady: StateFlow<Boolean> =
        combine(configRepository.config, local.isReady, remote.isReady) { cfg, localReady, remoteReady ->
            if (cfg.isServerUsable) remoteReady else localReady
        }.stateIn(scope, SharingStarted.Eagerly, false)

    private suspend fun active(): YtDlpEngine =
        if (configRepository.config.first().isServerUsable) remote else local

    override suspend fun ensureInitialized(): EngineResult<Unit> = active().ensureInitialized()

    override suspend fun fetchInfo(url: String, mode: DownloadMode): EngineResult<VideoInfo> =
        active().fetchInfo(url, mode)

    override suspend fun download(
        spec: DownloadSpec,
        onProgress: (DownloadProgress) -> Unit,
    ): EngineResult<DownloadedFile> = active().download(spec, onProgress)

    // Cancel on both — the worker holds one processId and only one engine is running it.
    override suspend fun cancel(processId: String) {
        local.cancel(processId)
        remote.cancel(processId)
    }

    override suspend fun update(): EngineResult<String> = active().update()
}
