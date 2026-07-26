package io.celox.flipperripper.domain.usecase

import io.celox.flipperripper.domain.model.DownloadError
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.model.VideoInfo
import io.celox.flipperripper.domain.repository.EngineRepository
import io.celox.flipperripper.domain.repository.VideoRepository
import io.celox.flipperripper.domain.util.UrlParser
import javax.inject.Inject

/** Validate a URL, ensure the engine is ready, then resolve its metadata. */
class ResolveVideoInfoUseCase
@Inject
constructor(
    private val engineRepository: EngineRepository,
    private val videoRepository: VideoRepository,
) {
    suspend operator fun invoke(url: String, mode: DownloadMode): EngineResult<VideoInfo> {
        if (UrlParser.detectPlatform(url) == null) {
            return EngineResult.Failure(DownloadError.InvalidUrl())
        }
        return when (val init = engineRepository.ensureInitialized()) {
            is EngineResult.Failure -> init
            is EngineResult.Success -> videoRepository.resolve(url, mode)
        }
    }
}
