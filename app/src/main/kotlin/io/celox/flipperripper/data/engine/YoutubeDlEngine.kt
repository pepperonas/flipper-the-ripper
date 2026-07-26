package io.celox.flipperripper.data.engine

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import io.celox.flipperripper.di.IoDispatcher
import io.celox.flipperripper.domain.model.DownloadError
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.DownloadProgress
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.model.Platform
import io.celox.flipperripper.domain.model.VideoInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import com.yausername.youtubedl_android.mapper.VideoInfo as LibVideoInfo

/**
 * Real [YtDlpEngine] backed by youtubedl-android (bundled yt-dlp + ffmpeg + aria2c).
 *
 * Initialisation unpacks the native Python/yt-dlp payload once. All library calls are blocking and
 * are dispatched to [ioDispatcher]. Failures are mapped through [ErrorClassifier] into the typed
 * [DownloadError] taxonomy.
 */
@Singleton
class YoutubeDlEngine
@Inject
constructor(
    @ApplicationContext private val appContext: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : YtDlpEngine {
    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val initMutex = Mutex()

    override suspend fun ensureInitialized(): EngineResult<Unit> {
        if (_isReady.value) return EngineResult.Success(Unit)
        return initMutex.withLock {
            if (_isReady.value) {
                return@withLock EngineResult.Success(Unit)
            }
            withContext(ioDispatcher) {
                try {
                    YoutubeDL.getInstance().init(appContext)
                    FFmpeg.getInstance().init(appContext)
                    _isReady.value = true
                    EngineResult.Success(Unit)
                } catch (e: YoutubeDLException) {
                    EngineResult.Failure(
                        DownloadError.EngineNotReady(
                            "Could not initialise the download engine: ${e.message ?: "unknown error"}",
                        ),
                    )
                }
            }
        }
    }

    override suspend fun fetchInfo(url: String, mode: DownloadMode): EngineResult<VideoInfo> {
        val ready = ensureInitialized()
        if (ready is EngineResult.Failure) return ready
        val platform =
            io.celox.flipperripper.domain.util.UrlParser.detectPlatform(url)
                ?: return EngineResult.Failure(DownloadError.InvalidUrl())

        return withContext(ioDispatcher) {
            try {
                val request = YoutubeDLRequest(url)
                request.addOption("--no-playlist")
                if (platform == Platform.YOUTUBE) {
                    request.addOption("--extractor-args", "youtube:player_client=default,ios,web_safari")
                }
                val info: LibVideoInfo = YoutubeDL.getInstance().getInfo(request)
                EngineResult.Success(info.toDomain(url, platform))
            } catch (e: YoutubeDLException) {
                EngineResult.Failure(ErrorClassifier.classify(e.message.orEmpty()))
            } catch (e: InterruptedException) {
                EngineResult.Failure(DownloadError.Cancelled())
            }
        }
    }

    override suspend fun download(
        spec: DownloadSpec,
        onProgress: (DownloadProgress) -> Unit,
    ): EngineResult<DownloadedFile> {
        val ready = ensureInitialized()
        if (ready is EngineResult.Failure) return ready

        return withContext(ioDispatcher) {
            prepareWorkingDir(spec.workingDir)
            val template = java.io.File(spec.workingDir, YtDlpArgsBuilder.OUTPUT_TEMPLATE).absolutePath
            val options = YtDlpArgsBuilder.buildOptions(spec.platform, spec.mode, template)

            val request = YoutubeDLRequest(spec.url)
            options.forEach { request.addOption(it) }

            try {
                YoutubeDL.getInstance().execute(request, spec.processId) { progress, etaInSeconds, line ->
                    onProgress(
                        DownloadProgress(
                            percent = progress.takeIf { it >= 0f },
                            etaSeconds = etaInSeconds.takeIf { it >= 0 },
                            line = line,
                        ),
                    )
                }
                val produced =
                    spec.workingDir.listFiles()?.firstOrNull { it.isFile && it.length() > 0 }
                        ?: return@withContext EngineResult.Failure(
                            DownloadError.Unknown("Download produced no file."),
                        )
                EngineResult.Success(DownloadedFile(produced, produced.extension))
            } catch (e: YoutubeDL.CanceledException) {
                EngineResult.Failure(DownloadError.Cancelled())
            } catch (e: InterruptedException) {
                EngineResult.Failure(DownloadError.Cancelled())
            } catch (e: YoutubeDLException) {
                EngineResult.Failure(ErrorClassifier.classify(e.message.orEmpty()))
            }
        }
    }

    override suspend fun cancel(processId: String) {
        withContext(ioDispatcher) {
            runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
        }
    }

    override suspend fun update(): EngineResult<String> {
        val ready = ensureInitialized()
        if (ready is EngineResult.Failure) return ready
        return withContext(ioDispatcher) {
            try {
                val status = YoutubeDL.getInstance().updateYoutubeDL(appContext)
                EngineResult.Success(status?.name ?: "UP_TO_DATE")
            } catch (e: YoutubeDLException) {
                EngineResult.Failure(ErrorClassifier.classify(e.message.orEmpty()))
            }
        }
    }

    private fun prepareWorkingDir(dir: java.io.File) {
        if (dir.exists()) dir.deleteRecursively()
        dir.mkdirs()
    }

    private fun LibVideoInfo.toDomain(sourceUrl: String, platform: Platform): VideoInfo =
        VideoInfo(
            sourceUrl = sourceUrl,
            platform = platform,
            title = title?.takeIf { it.isNotBlank() } ?: "Untitled",
            uploader = uploader,
            thumbnailUrl = thumbnail,
            durationSeconds = duration.toLong().takeIf { it > 0 },
            id = id,
        )
}
