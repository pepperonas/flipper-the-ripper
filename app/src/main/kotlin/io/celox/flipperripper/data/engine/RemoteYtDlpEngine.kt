package io.celox.flipperripper.data.engine

import io.celox.flipperripper.di.IoDispatcher
import io.celox.flipperripper.domain.model.BackendConfig
import io.celox.flipperripper.domain.model.DownloadError
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.DownloadProgress
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.domain.model.VideoInfo
import io.celox.flipperripper.domain.repository.BackendConfigRepository
import io.celox.flipperripper.domain.util.UrlParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * A [YtDlpEngine] that delegates extraction/downloading to the server backend (see `backend/`). The
 * backend runs the full yt-dlp toolchain — a JS runtime for YouTube's n-sig, curl_cffi impersonation
 * for TikTok, ffmpeg for merging — so it downloads what stock Android can't, then streams the file
 * back to the device.
 */
@Singleton
class RemoteYtDlpEngine
@Inject
constructor(
    private val configRepository: BackendConfigRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : YtDlpEngine {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.MINUTES) // long downloads stream through
            .build()

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /** processId -> server jobId, so cancel() can reach the running job. */
    private val jobIds = ConcurrentHashMap<String, String>()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun ensureInitialized(): EngineResult<Unit> =
        withContext(ioDispatcher) {
            val cfg = configRepository.config.first()
            try {
                val req = Request.Builder().url("${cfg.url.trimEnd('/')}/health").get().build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        _isReady.value = true
                        EngineResult.Success(Unit)
                    } else {
                        _isReady.value = false
                        EngineResult.Failure(DownloadError.EngineNotReady("Server returned ${resp.code}."))
                    }
                }
            } catch (e: IOException) {
                _isReady.value = false
                EngineResult.Failure(DownloadError.Network("Cannot reach the download server: ${e.message}"))
            }
        }

    override suspend fun fetchInfo(url: String, mode: DownloadMode): EngineResult<VideoInfo> =
        withContext(ioDispatcher) {
            val cfg = configRepository.config.first()
            val platform = UrlParser.detectPlatform(url)
                ?: return@withContext EngineResult.Failure(DownloadError.InvalidUrl())
            try {
                val body = JSONObject().put("url", url).toString().toRequestBody(jsonMedia)
                val req = request(cfg, "/api/resolve").post(body).build()
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        val obj = runCatching { JSONObject(text) }.getOrNull()
                        return@use EngineResult.Failure(
                            mapError(obj?.optString("errorKind"), obj?.optString("error")),
                        )
                    }
                    val obj = JSONObject(text)
                    EngineResult.Success(
                        VideoInfo(
                            sourceUrl = url,
                            platform = platform,
                            title = obj.optString("title", "Untitled").ifBlank { "Untitled" },
                            uploader = obj.optString("uploader").ifBlank { null },
                            thumbnailUrl = obj.optString("thumbnail").ifBlank { null },
                            durationSeconds = if (obj.isNull("durationSeconds")) null else obj.optLong("durationSeconds"),
                            id = obj.optString("id").ifBlank { null },
                        ),
                    )
                }
            } catch (e: IOException) {
                EngineResult.Failure(DownloadError.Network("Server request failed: ${e.message}"))
            }
        }

    override suspend fun download(
        spec: DownloadSpec,
        onProgress: (DownloadProgress) -> Unit,
    ): EngineResult<DownloadedFile> =
        withContext(ioDispatcher) {
            val cfg = configRepository.config.first()
            prepareDir(spec.workingDir)
            try {
                val jobId = startJob(cfg, spec) ?: return@withContext EngineResult.Failure(
                    DownloadError.Unknown("Server did not accept the job."),
                )
                jobIds[spec.processId] = jobId
                try {
                    pollAndDownload(cfg, jobId, spec, onProgress)
                } finally {
                    jobIds.remove(spec.processId)
                }
            } catch (e: IOException) {
                EngineResult.Failure(DownloadError.Network("Server download failed: ${e.message}"))
            }
        }

    private fun startJob(cfg: BackendConfig, spec: DownloadSpec): String? {
        val mode = if (spec.mode == DownloadMode.AUDIO) "audio" else "video"
        val body = JSONObject().put("url", spec.url).put("mode", mode).toString().toRequestBody(jsonMedia)
        client.newCall(request(cfg, "/api/jobs").post(body).build()).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return JSONObject(resp.body?.string().orEmpty()).optString("jobId").ifBlank { null }
        }
    }

    private suspend fun pollAndDownload(
        cfg: BackendConfig,
        jobId: String,
        spec: DownloadSpec,
        onProgress: (DownloadProgress) -> Unit,
    ): EngineResult<DownloadedFile> {
        while (coroutineContext.isActive) {
            val status = client.newCall(request(cfg, "/api/jobs/$jobId").get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return EngineResult.Failure(DownloadError.Network("Server status ${resp.code}."))
                JSONObject(resp.body?.string().orEmpty())
            }
            when (status.optString("status")) {
                "completed" -> return fetchFile(cfg, jobId, status, spec, onProgress)
                "failed" -> return EngineResult.Failure(
                    mapError(status.optString("errorKind"), status.optString("error")),
                )
                else -> {
                    val pct = status.optDouble("progress", 0.0).toFloat()
                    onProgress(DownloadProgress(percent = pct.coerceIn(0f, 99f), etaSeconds = null, line = "server"))
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
        return EngineResult.Failure(DownloadError.Cancelled())
    }

    private fun fetchFile(
        cfg: BackendConfig,
        jobId: String,
        status: JSONObject,
        spec: DownloadSpec,
        onProgress: (DownloadProgress) -> Unit,
    ): EngineResult<DownloadedFile> {
        val fileName = status.optString("fileName").ifBlank { "video.mp4" }
        val target = File(spec.workingDir, fileName)
        client.newCall(request(cfg, "/api/jobs/$jobId/file").get().build()).execute().use { resp ->
            val bodyStream = resp.body?.byteStream()
            if (!resp.isSuccessful || bodyStream == null) {
                return EngineResult.Failure(DownloadError.Network("Could not fetch the file (${resp.code})."))
            }
            streamToFile(bodyStream, target, resp.body?.contentLength() ?: -1L, onProgress)
        }
        // Best-effort server cleanup.
        runCatching { client.newCall(request(cfg, "/api/jobs/$jobId").delete().build()).execute().close() }
        return if (target.length() > 0) {
            EngineResult.Success(DownloadedFile(target, target.extension))
        } else {
            EngineResult.Failure(DownloadError.Storage("The transferred file was empty."))
        }
    }

    private fun streamToFile(
        input: java.io.InputStream,
        target: File,
        total: Long,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        target.outputStream().use { out ->
            val buffer = ByteArray(BUFFER)
            var copied = 0L
            var read = input.read(buffer)
            while (read >= 0) {
                out.write(buffer, 0, read)
                copied += read
                if (total > 0) {
                    onProgress(DownloadProgress((copied * 100f / total).coerceIn(0f, 100f), null, "transfer"))
                }
                read = input.read(buffer)
            }
        }
    }

    override suspend fun cancel(processId: String) {
        val jobId = jobIds[processId] ?: return
        val cfg = configRepository.config.first()
        withContext(ioDispatcher) {
            runCatching { client.newCall(request(cfg, "/api/jobs/$jobId").delete().build()).execute().close() }
        }
    }

    /** The server keeps its own yt-dlp current; nothing to update from the client. */
    override suspend fun update(): EngineResult<String> = EngineResult.Success("SERVER")

    private fun request(cfg: BackendConfig, path: String): Request.Builder =
        Request.Builder().url("${cfg.url.trimEnd('/')}$path").header("X-API-Key", cfg.apiKey)

    private fun prepareDir(dir: File) {
        if (dir.exists()) dir.deleteRecursively()
        dir.mkdirs()
    }

    private fun mapError(kind: String?, message: String?): DownloadError {
        val msg = message?.ifBlank { null }
        return when (kind) {
            "LoginRequired" -> DownloadError.LoginRequired(msg ?: "Sign-in required for this content.")
            "PrivateVideo" -> DownloadError.PrivateVideo(msg ?: "This video is private.")
            "RegionBlocked" -> DownloadError.RegionBlocked(msg ?: "Not available in your region.")
            "Unavailable" -> DownloadError.Unavailable(msg ?: "This video is unavailable.")
            "RateLimitedOrStale" -> DownloadError.RateLimitedOrStale(msg ?: "Temporarily unavailable — try again.")
            "Network" -> DownloadError.Network()
            "InvalidUrl" -> DownloadError.InvalidUrl()
            else -> DownloadError.Unknown(msg ?: "Download failed on the server.")
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 1000L
        const val BUFFER = 64 * 1024
    }
}
