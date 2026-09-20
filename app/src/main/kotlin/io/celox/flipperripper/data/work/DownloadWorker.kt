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
import io.celox.flipperripper.data.engine.DownloadNaming
import io.celox.flipperripper.data.engine.DownloadSpec
import io.celox.flipperripper.data.engine.DownloadedFile
import io.celox.flipperripper.data.engine.FilenameSanitizer
import io.celox.flipperripper.data.engine.YtDlpEngine
import io.celox.flipperripper.data.local.DownloadDao
import io.celox.flipperripper.data.local.DownloadEntity
import io.celox.flipperripper.data.media.MediaStoreWriter
import io.celox.flipperripper.domain.model.DownloadError
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.DownloadPhases
import io.celox.flipperripper.domain.model.DownloadStatus
import io.celox.flipperripper.domain.model.EngineResult
import io.celox.flipperripper.util.MediaIntents
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * Runs one download in the background as a foreground service (via WorkManager), so it survives
 * screen-lock, app-minimise and rotation, and the OS keeps the process alive.
 *
 * The record in the database is the only place the state lives: the History card and the
 * notification both read it, which is why every phase change here goes through [applyPhase] — it
 * writes the record *and* refreshes the notification from the same values, in that order.
 *
 * Phases: PREPARING (metadata, nothing transferring) → RUNNING (bytes, real percentage) →
 * PROCESSING (ffmpeg merge, then the MediaStore copy) → COMPLETED / FAILED.
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

    /** Read by the progress pump, which runs on a different thread than the writer. */
    @Volatile private var currentTitle: String = ""

    /**
     * The last percentage the engine actually reported, kept so the post-processing phase can carry
     * it instead of blanking it: "every byte arrived, and there is still work to do" is the true
     * statement, and throwing the number away would make it look like the download restarted.
     */
    @Volatile private var measuredPercent: Float? = null

    /** One phase change on its way to the record and the notification. */
    private data class Phase(val status: DownloadStatus, val percent: Float?)

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(DownloadStatus.PREPARING, currentTitle, null)

    override suspend fun doWork(): Result {
        val id = recordId ?: return Result.failure()
        val record = dao.getById(id) ?: return Result.failure()
        notifier.ensureChannels()

        currentTitle = record.title
        // Resolving metadata is not downloading — it can take seconds and moves no bytes, so it gets
        // its own phase and no percentage rather than a bar frozen at 0 %.
        applyPhase(id, DownloadStatus.PREPARING, null)

        val info = engine.fetchInfo(record.sourceUrl, enumMode(record.mode)).getOrNull()
        if (info != null) {
            // Field-scoped: writing the whole row back here used to reset the record to QUEUED / 0 %,
            // because the in-memory copy was read before the phase was set (see DownloadDao).
            dao.updateMetadata(id, info.title, info.thumbnailUrl, now())
            currentTitle = info.title
        }

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
        var result = runEngine(id, specFor(progressive = false))

        // Self-heal: a stale extractor or a merge/ffmpeg failure is recoverable — update yt-dlp and
        // retry once with a single pre-muxed format that needs no ffmpeg.
        if (result is EngineResult.Failure && isRecoverable(result.error)) {
            applyPhase(id, DownloadStatus.PREPARING, null)
            engine.update()
            result = runEngine(id, specFor(progressive = true))
        }

        return when (result) {
            is EngineResult.Failure -> finishWithError(id, currentTitle, result.error, workingDir)
            is EngineResult.Success ->
                saveAndFinish(id, record, info?.title, result.value.file, workingDir)
        }
    }

    /**
     * Runs the engine and feeds its progress callbacks to a pump coroutine.
     *
     * The callback itself only hands over a value. It used to do the database write and the
     * notification update inside `runBlocking`, which parked the engine's own download thread on a
     * Room round-trip and a binder call about a hundred times per file. The channel is conflated: a
     * slow write can never make the engine wait, and the pump always applies the newest phase rather
     * than working through a backlog of stale ones.
     */
    private suspend fun runEngine(id: String, spec: DownloadSpec): EngineResult<DownloadedFile> =
        coroutineScope {
            val ticks = Channel<Phase>(Channel.CONFLATED)
            launch { for (phase in ticks) applyPhase(id, phase.status, phase.percent) }

            // A retry starts a fresh transfer, so the previous run's numbers must not leak into it.
            measuredPercent = null
            var lastPercent = -1f
            var wasPostProcessing = false
            try {
                engine.download(spec) { progress ->
                    val postProcessing = DownloadPhases.isPostProcessing(progress.line)
                    val percent = progress.percent
                    val advanced = percent != null && percent - lastPercent >= PROGRESS_STEP
                    if (advanced) {
                        lastPercent = percent!!
                        measuredPercent = percent
                    }
                    // A phase change always gets through; otherwise only a percent that actually moved,
                    // so a byte-by-byte stream does not queue thousands of identical updates.
                    if (postProcessing != wasPostProcessing || advanced) {
                        wasPostProcessing = postProcessing
                        ticks.trySend(
                            if (postProcessing) {
                                // The transfer is finished; the last measured value is the honest one
                                // to keep showing, but the phase says the work is not over.
                                Phase(DownloadStatus.PROCESSING, measuredPercent)
                            } else {
                                Phase(DownloadStatus.RUNNING, percent)
                            },
                        )
                    }
                }
            } finally {
                // Closing ends the pump's loop; `coroutineScope` then waits for it before returning,
                // so the last phase is always persisted before the result is acted on.
                ticks.close()
            }
        }

    /** The one place a phase reaches both surfaces, so they cannot drift apart. */
    private suspend fun applyPhase(id: String, status: DownloadStatus, percent: Float?) {
        dao.updateProgress(id, status.name, percent, now())
        runCatching { setForeground(foregroundInfo(status, currentTitle, percent)) }
    }

    private suspend fun saveAndFinish(
        id: String,
        record: DownloadEntity,
        resolvedTitle: String?,
        file: File,
        workingDir: File,
    ): Result {
        // Copying into MediaStore is real work on a large video. Reporting COMPLETED here is what
        // once made the app claim "saved" while the file was still being written.
        applyPhase(id, DownloadStatus.PROCESSING, measuredPercent)

        val nameTitle = preferredName(resolvedTitle, file, record)
        val displayName = FilenameSanitizer.sanitize(nameTitle, file.extension)
        // Metadata may have resolved only after the record was created from a bare shared link, so keep
        // the stored title in step with the name we actually save under.
        if (record.title != nameTitle) {
            dao.updateMetadata(id, nameTitle, null, now())
            currentTitle = nameTitle
        }
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
                    DownloadNotificationIds.terminal(id),
                    saved.value.displayName,
                    MediaIntents.viewIntent(saved.value.uri, enumMode(record.mode)),
                )
                workingDir.deleteRecursively()
                Result.success()
            }
            is EngineResult.Failure -> finishWithError(id, currentTitle, saved.error, workingDir)
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
            notifier.notifyFailed(DownloadNotificationIds.terminal(id), title, error.message)
        }
        workingDir.deleteRecursively()
        return when {
            error is DownloadError.Network && runAttemptCount < MAX_NETWORK_RETRIES -> Result.retry()
            else -> Result.failure()
        }
    }

    /**
     * A failure worth retrying after a yt-dlp update + progressive fallback: a stale/rate-limited
     * extractor, or an unclassified error (which is where a merge / "ffmpeg not found" failure lands).
     * Terminal errors (private/login/region/network/cancelled) are not retried.
     */
    private fun isRecoverable(error: DownloadError): Boolean =
        error is DownloadError.RateLimitedOrStale || error is DownloadError.Unknown

    private fun foregroundInfo(status: DownloadStatus, title: String, percent: Float?): ForegroundInfo {
        val notification = notifier.buildProgress(status, title, percent)
        val notifId = DownloadNotificationIds.progress(recordId ?: "download")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notifId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notifId, notification)
        }
    }

    private fun preferredName(resolvedTitle: String?, file: File, record: DownloadEntity): String =
        DownloadNaming.preferredTitle(resolvedTitle, file.nameWithoutExtension, record.title)

    private fun enumMode(name: String) =
        runCatching { DownloadMode.valueOf(name) }.getOrDefault(DownloadMode.VIDEO)

    private fun now() = System.currentTimeMillis()
}
