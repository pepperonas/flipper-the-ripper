package io.celox.flipperripper.data.work

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.celox.flipperripper.data.local.DownloadDao
import io.celox.flipperripper.di.ApplicationScope
import io.celox.flipperripper.domain.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gets the download queue moving again when the process starts.
 *
 * Two jobs, both once per process and both off the main thread:
 *
 * 1. **Retire the pre-1.10 workers.** Every download used to be its own WorkManager job. Those jobs
 *    survive an app update, so without this an upgrading user would have the old parallel workers
 *    and the new queue running the same downloads at the same time.
 * 2. **Pick up what was interrupted.** A download left mid-flight by a killed process or a reboot
 *    should continue without the user having to find it and tap something. The queue worker itself
 *    puts such rows back to QUEUED; this only makes sure the worker is scheduled at all.
 */
@Singleton
class DownloadQueueBootstrap
@Inject
constructor(
    private val dao: DownloadDao,
    private val workManager: WorkManager,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            workManager.cancelAllWorkByTag(DownloadQueueWorker.LEGACY_TAG)
            if (dao.getByStatus(UNFINISHED).isNotEmpty()) scheduleQueue()
        }
    }

    private fun scheduleQueue() {
        val work =
            OneTimeWorkRequestBuilder<DownloadQueueWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
        workManager.enqueueUniqueWork(
            DownloadQueueWorker.WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            work,
        )
    }

    private companion object {
        /**
         * PAUSED is deliberately absent: a paused download is waiting for the user, not for the
         * queue, and starting a worker for it would only have it skip past and stop again.
         */
        val UNFINISHED =
            listOf(
                DownloadStatus.QUEUED.name,
                DownloadStatus.PREPARING.name,
                DownloadStatus.RUNNING.name,
                DownloadStatus.PROCESSING.name,
            )
    }
}
