package io.celox.flipperripper.domain.repository

import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.model.VideoInfo

/** Resolves metadata for a URL without downloading the media. */
interface VideoRepository {
    /**
     * Fetch title/uploader/thumbnail/duration for [url] via the engine's JSON dump.
     * Fails fast with a typed [io.celox.flipperripper.domain.model.DownloadError].
     */
    suspend fun resolve(url: String, mode: DownloadMode): EngineResult<VideoInfo>
}
