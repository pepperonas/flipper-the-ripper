package io.celox.flipperripper.data.engine

import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.DownloadProgress
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.model.Platform
import io.celox.flipperripper.domain.model.VideoInfo
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** A fully downloaded media file produced by the engine. */
data class DownloadedFile(val file: File, val extension: String)

/** Everything the engine needs to run one download. */
data class DownloadSpec(
    val url: String,
    val platform: Platform,
    val mode: DownloadMode,
    /** Per-download working directory; the produced file is written here then moved to MediaStore. */
    val workingDir: File,
    /** Stable id used to address (and cancel) the running process. */
    val processId: String,
    /** Use a single pre-muxed format (no ffmpeg merge) — the fallback retry after a merge failure. */
    val preferProgressive: Boolean = false,
)

/**
 * Thin seam over the yt-dlp runtime. The real implementation wraps youtubedl-android; tests use a
 * fake. All calls are suspending and safe to invoke off the main thread.
 */
interface YtDlpEngine {
    /** True once the native payload is unpacked and the engine is usable. */
    val isReady: StateFlow<Boolean>

    /** Idempotent one-time initialisation (unpacks Python/yt-dlp/ffmpeg). */
    suspend fun ensureInitialized(): EngineResult<Unit>

    /** Resolve metadata for [url] without downloading. */
    suspend fun fetchInfo(url: String, mode: DownloadMode): EngineResult<VideoInfo>

    /** Run a download, reporting progress. Returns the produced file on success. */
    suspend fun download(spec: DownloadSpec, onProgress: (DownloadProgress) -> Unit): EngineResult<DownloadedFile>

    /** Cancel a running download by its [DownloadSpec.processId]. */
    suspend fun cancel(processId: String)

    /** Update the bundled yt-dlp to the latest release. Returns a short status string. */
    suspend fun update(): EngineResult<String>
}
