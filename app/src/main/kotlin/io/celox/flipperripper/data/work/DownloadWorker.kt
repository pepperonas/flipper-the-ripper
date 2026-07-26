package io.celox.flipperripper.data.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.celox.flipperripper.data.engine.DownloadSpec
import io.celox.flipperripper.data.engine.FilenameSanitizer
import io.celox.flipperripper.data.engine.YtDlpEngine
import io.celox.flipperripper.data.local.DownloadDao
import io.celox.flipperripper.data.local.DownloadEntity
import io.celox.flipperripper.data.media.MediaStoreWriter
import io.celox.flipperripper.domain.model.DownloadError
import io.celox.flipperripper.domain.model.DownloadStatus
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.util.MediaIntents
import java.io.File

/**
 * Runs one download in the background as a foreground service (via WorkManager), so it survives
 * screen-lock, app-minimise and rotation, and the OS keeps the process alive.
 *
 * Flow (mirrors the required UX "analyse → detect platform → load info → download"): mark running →
 * best-effort resolve metadata → run the engine with live progress → copy the produced file into
 * MediaStore → mark completed. Every failure is persisted as a typed error on the record.
 */
@HiltWorker
class DownloadWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: DownloadDao,
    private val engine: YtDlpEngine,
    private val mediaWriter: MediaStoreWriter,
    private val notifier: DownloadNotifier,
) : CoroutineWorker(appContext, params) {
    companion object {
        const val KEY_RECORD_ID = "record_id"
        const val WORK_NAME_PREFIX = "download_"
        private const val PROGRESS_STEP = 1f
        private const val MAX_NETWORK_RETRIES = 3
    }

    private val recordId: String? get() = inputData.getString(KEY_RECORD_ID)

    override suspend fun getForegroundInfo(): ForegroundInfo = buildForegroundInfo("", null)

    override suspend fun doWork(): Result {
        val id = recordId ?: return Result.failure()
        val record = dao.getById(id) ?: return Result.failure()
        notifier.ensureChannels()

        setForeground(buildForegroundInfo(record.title, null))
        dao.updateProgress(id, DownloadStatus.RUNNING.name, 0f, now())

        // Best-effort metadata resolution for a proper title/thumbnail.
        val info = engine.fetchInfo(record.sourceUrl, enumMode(record.mode)).getOrNull()
        if (info != null) {
            dao.upsert(
                record.copy(
                    title = info.title,
                    thumbnailUrl = info.thumbnailUrl ?: record.thumbnailUrl,
                    updatedAtEpochMs = now(),
                ),
            )
        }
        val displayTitle = info?.title ?: record.title

        val workingDir = File(applicationContext.cacheDir, "downloads/$id")
        fun specFor(progressive: Boolean) =
            DownloadSpec(
                url = record.sourceUrl,
                platform = record.toDomain().platform,
                mode = enumMode(record.mode),
                workingDir = workingDir,
                processId = id,
                preferProgressive = progressive,
            )

        // Attempt 1: best quality (may need an ffmpeg merge).
        var result = runEngine(id, displayTitle, specFor(progressive = false))

        // Self-heal: a stale extractor or a merge/ffmpeg failure is recoverable — update yt-dlp and
        // retry once with a single pre-muxed format that needs no ffmpeg.
        if (result is EngineResult.Failure && isRecoverable(result.error)) {
            engine.update()
            dao.updateProgress(id, DownloadStatus.RUNNING.name, 0f, now())
            result = runEngine(id, displayTitle, specFor(progressive = true))
        }

        return when (result) {
            is EngineResult.Failure -> finishWithError(id, displayTitle, result.error, workingDir)
            is EngineResult.Success -> saveAndFinish(id, displayTitle, record, result.value.file, workingDir)
        }
    }

    private suspend fun saveAndFinish(
        id: String,
        displayTitle: String,
        record: DownloadEntity,
        file: File,
        workingDir: File,
    ): Result {
        val nameTitle = displayTitle.ifBlank { titleFromFile(file) }
        val displayName = FilenameSanitizer.sanitize(nameTitle, file.extension)
        return when (val saved = mediaWriter.save(file, displayName, enumMode(record.mode))) {
            is EngineResult.Success -> {
                dao.markCompleted(
                    id = id,
                    status = DownloadStatus.COMPLETED.name,
                    mediaUri = saved.value.uri,
                    fileName = saved.value.displayName,
                    sizeBytes = saved.value.sizeBytes,
                    updatedAt = now(),
                )
                notifier.notifyCompleted(
                    id.hashCode(),
                    saved.value.displayName,
                    MediaIntents.viewIntent(saved.value.uri, enumMode(record.mode)),
                )
                workingDir.deleteRecursively()
                Result.success()
            }
            is EngineResult.Failure -> finishWithError(id, displayTitle, saved.error, workingDir)
        }
    }

    private suspend fun finishWithError(
        id: String,
        title: String,
        error: DownloadError,
        workingDir: File,
    ): Result {
        val status =
            if (error is DownloadError.Cancelled) DownloadStatus.CANCELLED else DownloadStatus.FAILED
        dao.markFailed(id, status.name, error.kind, error.message, now())
        if (error !is DownloadError.Cancelled) {
            notifier.notifyFailed(id.hashCode(), title, error.message)
        }
        workingDir.deleteRecursively()
        return when {
            error is DownloadError.Network && runAttemptCount < MAX_NETWORK_RETRIES -> Result.retry()
            else -> Result.failure()
        }
    }

    private suspend fun runEngine(
        id: String,
        displayTitle: String,
        spec: DownloadSpec,
    ): EngineResult<io.celox.flipperripper.data.engine.DownloadedFile> {
        var lastReported = -1f
        return engine.download(spec) { progress ->
            val pct = progress.percent
            if (pct != null && pct - lastReported >= PROGRESS_STEP) {
                lastReported = pct
                blockingUpdate(id, pct, displayTitle)
            }
        }
    }

    /**
     * A failure worth retrying after a yt-dlp update + progressive fallback: a stale/rate-limited
     * extractor, or an unclassified error (which is where a merge / "ffmpeg not found" failure lands).
     * Terminal errors (private/login/region/network/cancelled) are not retried.
     */
    private fun isRecoverable(error: DownloadError): Boolean =
        error is DownloadError.RateLimitedOrStale || error is DownloadError.Unknown

    private fun blockingUpdate(id: String, percent: Float, title: String) {
        // Called from the engine's progress callback (already off the main thread).
        kotlinx.coroutines.runBlocking {
            dao.updateProgress(id, DownloadStatus.RUNNING.name, percent, now())
            runCatching { setForeground(buildForegroundInfo(title, percent)) }
        }
    }

    private fun buildForegroundInfo(title: String, percent: Float?): ForegroundInfo {
        val notification = notifier.buildProgress(title, percent)
        val notifId = (recordId ?: "download").hashCode()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notifId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notifId, notification)
        }
    }

    private fun titleFromFile(file: File): String =
        file.nameWithoutExtension.replace(Regex("""\s*\[[^\]]+\]\s*$"""), "").trim()

    private fun enumMode(name: String) =
        runCatching { io.celox.flipperripper.domain.model.DownloadMode.valueOf(name) }
            .getOrDefault(io.celox.flipperripper.domain.model.DownloadMode.VIDEO)

    private fun now() = System.currentTimeMillis()
}
