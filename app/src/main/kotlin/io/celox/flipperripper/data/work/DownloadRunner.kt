package io.celox.flipperripper.data.work

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.celox.flipperripper.data.engine.DownloadNaming
import io.celox.flipperripper.data.engine.DownloadSpec
import io.celox.flipperripper.data.engine.DownloadedFile
import io.celox.flipperripper.data.engine.EngineOutput
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
import javax.inject.Inject
import javax.inject.Singleton

/** What one download did, as far as the queue runner cares. */
enum class RunOutcome {
    COMPLETED,

    /** Terminal for this attempt; the queue moves on. */
    FAILED,

    /** Cancelled or paused by the user — the queue moves on, the record keeps whatever it was set to. */
    STOPPED,

    /** A network failure worth another attempt later; the queue should back off rather than spin. */
    RETRYABLE,
}

/**
 * Runs exactly one download: metadata, transfer, post-processing, save.
 *
 * This used to be the body of `DownloadWorker`, one WorkManager job per download, all of them
 * running at once. It is a plain class now so that a single [DownloadQueueWorker] can drive it one
 * record at a time — which is what makes the queue position mean anything.
 *
 * The record in the database is the only place the state lives: the History card and the
 * notification both read it, which is why every phase change goes through [applyPhase] — it writes
 * the record and hands the same values to [onPhase], in that order.
 */
@Singleton
class DownloadRunner
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val engine: YtDlpEngine,
    private val mediaWriter: MediaStoreWriter,
    private val notifier: DownloadNotifier,
) {
    /** One phase change on its way to the record and the notification. */
    private data class Phase(val status: DownloadStatus, val percent: Float?)

    /** Everything the caller needs to draw a notification for the download currently running. */
    data class PhaseUpdate(val id: String, val status: DownloadStatus, val title: String, val percent: Float?)

    private class RunState {
        @Volatile var title: String = ""

        /**
         * The last percentage the engine actually reported, kept so post-processing can carry it
         * instead of blanking it: "every byte arrived, and there is still work to do" is the true
         * statement, and dropping the number would look like a restart.
         */
        @Volatile var measuredPercent: Float? = null
    }

    suspend fun run(id: String, onPhase: suspend (PhaseUpdate) -> Unit): RunOutcome {
        val record = dao.getById(id) ?: return RunOutcome.FAILED
        val state = RunState()
        state.title = record.title

        // Resolving metadata is not downloading — it can take seconds and moves no bytes, so it gets
        // its own phase and no percentage rather than a bar frozen at 0 %.
        //
        // A refusal here means the row is already paused, and starting anyway would both discard
        // the user's decision and, on the way out, delete the partial file they kept.
        if (!applyPhase(id, state, DownloadStatus.PREPARING, null, onPhase)) return RunOutcome.STOPPED

        val info = engine.fetchInfo(record.sourceUrl, enumMode(record.mode)).getOrNull()
        if (info != null) {
            // Field-scoped: writing the whole row back here used to reset the record to QUEUED / 0 %,
            // because the in-memory copy was read before the phase was set (see DownloadDao).
            dao.updateMetadata(id, info.title, info.thumbnailUrl, now())
            state.title = info.title
        }

        val workingDir = workingDirFor(id)
        // Bytes left behind by a pause (the only thing that keeps this directory) — the run that
        // follows continues them instead of fetching the file again.
        val continuing = EngineOutput.hasPartialBytes(workingDir)
        fun specFor(progressive: Boolean, resume: Boolean = false) =
            DownloadSpec(
                url = record.sourceUrl,
                platform = record.toDomain().platform,
                mode = enumMode(record.mode),
                workingDir = workingDir,
                processId = id,
                preferProgressive = progressive,
                resume = resume,
            )

        // Attempt 1: best quality (may need an ffmpeg merge).
        var result = runEngine(id, state, specFor(progressive = false, resume = continuing), onPhase)

        // Self-heal: a stale extractor or a merge/ffmpeg failure is recoverable — update yt-dlp and
        // retry once with a single pre-muxed format that needs no ffmpeg.
        if (result is EngineResult.Failure && isRecoverable(result.error)) {
            applyPhase(id, state, DownloadStatus.PREPARING, null, onPhase)
            engine.update()
            result = runEngine(id, state, specFor(progressive = true), onPhase)
        }

        return when (result) {
            is EngineResult.Failure -> finishWithError(id, state.title, result.error, workingDir)
            is EngineResult.Success ->
                saveAndFinish(id, state, record, info?.title, result.value.file, workingDir, onPhase)
        }
    }

    /** The per-download scratch directory, kept across a pause so `.part` bytes survive. */
    fun workingDirFor(id: String): File = File(context.cacheDir, "downloads/$id")

    /**
     * Runs the engine and feeds its progress callbacks to a pump coroutine.
     *
     * The callback itself only hands over a value. It used to do the database write and the
     * notification update inside `runBlocking`, which parked the engine's own download thread on a
     * Room round-trip and a binder call about a hundred times per file. The channel is conflated: a
     * slow write can never make the engine wait, and the pump always applies the newest phase rather
     * than working through a backlog of stale ones.
     */
    private suspend fun runEngine(
        id: String,
        state: RunState,
        spec: DownloadSpec,
        onPhase: suspend (PhaseUpdate) -> Unit,
    ): EngineResult<DownloadedFile> =
        coroutineScope {
            val ticks = Channel<Phase>(Channel.CONFLATED)
            launch {
                for (phase in ticks) {
                    // A refused write means the user paused mid-transfer. Stopping the engine here
                    // turns that into a Cancelled result, which the caller recognises as a pause and
                    // leaves the `.part` file alone.
                    if (!applyPhase(id, state, phase.status, phase.percent, onPhase)) engine.cancel(id)
                }
            }

            // A retry starts a fresh transfer, so the previous run's numbers must not leak into it.
            state.measuredPercent = null
            var lastPercent = -1f
            var wasPostProcessing = false
            try {
                engine.download(spec) { progress ->
                    val postProcessing = DownloadPhases.isPostProcessing(progress.line)
                    val percent = progress.percent
                    val advanced = percent != null && percent - lastPercent >= PROGRESS_STEP
                    if (advanced) {
                        lastPercent = percent!!
                        state.measuredPercent = percent
                    }
                    // A phase change always gets through; otherwise only a percent that actually
                    // moved, so a byte-by-byte stream does not queue thousands of identical updates.
                    if (postProcessing != wasPostProcessing || advanced) {
                        wasPostProcessing = postProcessing
                        ticks.trySend(
                            if (postProcessing) {
                                // The transfer is finished; the last measured value is the honest one
                                // to keep showing, but the phase says the work is not over.
                                Phase(DownloadStatus.PROCESSING, state.measuredPercent)
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

    /**
     * The one place a phase reaches both surfaces, so they cannot drift apart.
     *
     * Returns false when the row is paused — the write is refused in SQL, and the caller uses that
     * to stop rather than carry on downloading something the user has already stopped.
     */
    private suspend fun applyPhase(
        id: String,
        state: RunState,
        status: DownloadStatus,
        percent: Float?,
        onPhase: suspend (PhaseUpdate) -> Unit,
    ): Boolean {
        val applied = dao.updateProgressUnlessPaused(id, status.name, percent, now()) > 0
        if (applied) onPhase(PhaseUpdate(id, status, state.title, percent))
        return applied
    }

    private suspend fun saveAndFinish(
        id: String,
        state: RunState,
        record: DownloadEntity,
        resolvedTitle: String?,
        file: File,
        workingDir: File,
        onPhase: suspend (PhaseUpdate) -> Unit,
    ): RunOutcome {
        // Copying into MediaStore is real work on a large video. Reporting COMPLETED here is what
        // once made the app claim "saved" while the file was still being written.
        applyPhase(id, state, DownloadStatus.PROCESSING, state.measuredPercent, onPhase)

        val nameTitle = DownloadNaming.preferredTitle(resolvedTitle, file.nameWithoutExtension, record.title)
        val displayName = FilenameSanitizer.sanitize(nameTitle, file.extension)
        // Metadata may have resolved only after the record was created from a bare shared link, so
        // keep the stored title in step with the name we actually save under.
        if (record.title != nameTitle) {
            dao.updateMetadata(id, nameTitle, null, now())
            state.title = nameTitle
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
                RunOutcome.COMPLETED
            }
            is EngineResult.Failure -> finishWithError(id, state.title, saved.error, workingDir)
        }
    }

    private suspend fun finishWithError(
        id: String,
        title: String,
        error: DownloadError,
        workingDir: File,
    ): RunOutcome {
        if (error is DownloadError.Cancelled) {
            // Pause and cancel both arrive here as a killed process. The record says which one it
            // was, because the repository writes the status *before* stopping the engine — and a
            // paused download must keep its working directory, that is the whole point of the
            // `.part` file it will resume from.
            val current = statusOf(id)
            if (current != DownloadStatus.PAUSED) {
                dao.markFailed(id, DownloadStatus.CANCELLED.name, error.kind, error.message, now())
                workingDir.deleteRecursively()
            }
            return RunOutcome.STOPPED
        }
        dao.markFailed(id, DownloadStatus.FAILED.name, error.kind, error.message, now())
        notifier.notifyFailed(DownloadNotificationIds.terminal(id), title, error.message)
        workingDir.deleteRecursively()
        return if (error is DownloadError.Network) RunOutcome.RETRYABLE else RunOutcome.FAILED
    }

    private suspend fun statusOf(id: String): DownloadStatus? =
        dao.getById(id)?.let { runCatching { DownloadStatus.valueOf(it.status) }.getOrNull() }

    /**
     * A failure worth retrying after a yt-dlp update + progressive fallback: a stale/rate-limited
     * extractor, or an unclassified error (which is where a merge / "ffmpeg not found" failure
     * lands). Terminal errors (private/login/region/network/cancelled) are not retried.
     */
    private fun isRecoverable(error: DownloadError): Boolean =
        error is DownloadError.RateLimitedOrStale || error is DownloadError.Unknown

    private fun enumMode(name: String) =
        runCatching { DownloadMode.valueOf(name) }.getOrDefault(DownloadMode.VIDEO)

    private fun now() = System.currentTimeMillis()

    private companion object {
        const val PROGRESS_STEP = 1f
    }
}
