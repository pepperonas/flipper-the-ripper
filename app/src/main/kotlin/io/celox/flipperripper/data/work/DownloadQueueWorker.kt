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
import io.celox.flipperripper.data.local.DownloadDao
import io.celox.flipperripper.domain.model.DownloadStatus
import io.celox.flipperripper.domain.model.QueueOrdering
import io.celox.flipperripper.domain.model.isActive
import io.celox.flipperripper.domain.model.isWaiting

/**
 * The one worker that runs downloads — the whole queue, one at a time, in [queue order].
 *
 * Before 1.10.0 every download was its own WorkManager job and WorkManager ran several at once, so
 * "queued" was a label with nothing behind it: what started next was whatever the scheduler chose,
 * the order in History was cosmetic, and reordering would have been a lie. A single unique work
 * makes the order real and gives the user one notification instead of one per download.
 *
 * It is a foreground (`dataSync`) worker so the transfer survives the app being minimised or the
 * screen locked — the state itself lives in the database, never in this object.
 */
@HiltWorker
class DownloadQueueWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: DownloadDao,
    private val runner: DownloadRunner,
    private val notifier: DownloadNotifier,
) : CoroutineWorker(appContext, params) {
    companion object {
        const val WORK_NAME = "download_queue"

        /** Tag of the pre-1.10 per-download work, cancelled once on upgrade. */
        const val LEGACY_TAG = "flipper_download"
    }

    private var lastForeground: ForegroundInfo = idleForeground()

    override suspend fun getForegroundInfo(): ForegroundInfo = lastForeground

    override suspend fun doWork(): Result {
        notifier.ensureChannels()
        // Nothing else runs downloads, so anything still claiming to be mid-flight is the wreckage
        // of a killed process (or of the pre-1.10 per-download workers). Put it back in the queue
        // rather than leaving a card spinning forever over a download nobody is performing.
        requeueInterrupted()

        var completed = 0
        var retryable = false
        var draining = true
        while (draining && !isStopped) {
            val pending = dao.getByStatus(PENDING_STATUSES).map { it.toDomain() }
            val next = QueueOrdering.next(pending)
            if (next == null) {
                draining = false
            } else {
                // The batch size counts what is still waiting plus what this run already did, so the
                // notification reads "2 of 5" rather than counting down to "1 of 1".
                val total = completed + pending.count { it.status.isActive || it.status.isWaiting }
                val outcome = runner.run(next.id) { update -> publish(update, completed + 1, total) }
                if (outcome == RunOutcome.RETRYABLE) {
                    retryable = true
                    draining = false
                } else {
                    completed++
                }
            }
        }
        return if (retryable) Result.retry() else Result.success()
    }

    private suspend fun requeueInterrupted() {
        val now = System.currentTimeMillis()
        dao.getByStatus(INTERRUPTED_STATUSES).forEach {
            dao.updateProgress(it.id, DownloadStatus.QUEUED.name, null, now)
        }
    }

    /**
     * Moves the ongoing notification to the download currently running.
     *
     * `setForeground` can fail — the process may be in the background without the foreground-service
     * permission the system wants at that instant — and a failed notification must never take a
     * running download down with it.
     */
    private suspend fun publish(update: DownloadRunner.PhaseUpdate, position: Int, total: Int) {
        val info =
            foregroundInfo(
                notifier.buildQueueProgress(update.status, update.title, update.percent, position, total),
            )
        lastForeground = info
        runCatching { setForeground(info) }
    }

    private fun idleForeground(): ForegroundInfo =
        foregroundInfo(notifier.buildProgress(DownloadStatus.QUEUED, "", null))

    private fun foregroundInfo(notification: android.app.Notification): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                DownloadNotificationIds.QUEUE_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(DownloadNotificationIds.QUEUE_PROGRESS, notification)
        }
}

/** Statuses the runner may still have to act on. PAUSED is listed so it counts, not so it runs. */
private val PENDING_STATUSES =
    listOf(
        DownloadStatus.QUEUED.name,
        DownloadStatus.PREPARING.name,
        DownloadStatus.RUNNING.name,
        DownloadStatus.PROCESSING.name,
        DownloadStatus.PAUSED.name,
    )

/** Mid-flight statuses that can only be left over from a process that died. */
private val INTERRUPTED_STATUSES =
    listOf(
        DownloadStatus.PREPARING.name,
        DownloadStatus.RUNNING.name,
        DownloadStatus.PROCESSING.name,
    )
