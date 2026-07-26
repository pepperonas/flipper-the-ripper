package io.celox.flipperripper.data.repository

import io.celox.flipperripper.data.engine.YtDlpEngine
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.model.VideoInfo
import io.celox.flipperripper.domain.repository.VideoRepository
import javax.inject.Inject

class VideoRepositoryImpl
@Inject
constructor(private val engine: YtDlpEngine) : VideoRepository {
    override suspend fun resolve(url: String, mode: DownloadMode): EngineResult<VideoInfo> =
        engine.fetchInfo(url, mode)
}
