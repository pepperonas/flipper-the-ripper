package io.celox.flipperripper.domain.usecase

import io.celox.flipperripper.domain.model.DownloadError
import io.celox.flipperripper.domain.model.DownloadRequest
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.repository.DownloadRepository
import io.celox.flipperripper.domain.util.UrlParser
import javax.inject.Inject

/** Validate a request and enqueue a background download, returning the tracking record id. */
class StartDownloadUseCase
@Inject
constructor(private val downloadRepository: DownloadRepository) {
    suspend operator fun invoke(request: DownloadRequest): EngineResult<String> {
        if (UrlParser.detectPlatform(request.url) == null) {
            return EngineResult.Failure(DownloadError.InvalidUrl())
        }
        val id = downloadRepository.enqueue(request)
        return EngineResult.Success(id)
    }
}
