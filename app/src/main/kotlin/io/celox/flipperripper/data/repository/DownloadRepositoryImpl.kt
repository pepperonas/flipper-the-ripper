package io.celox.flipperripper.data.repository

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.celox.flipperripper.data.engine.YtDlpEngine
import io.celox.flipperripper.data.local.DownloadDao
import io.celox.flipperripper.data.local.DownloadEntity
import io.celox.flipperripper.data.work.DownloadQueueWorker
import io.celox.flipperripper.domain.model.DownloadRecord
import io.celox.flipperripper.domain.model.DownloadRequest
import io.celox.flipperripper.domain.model.DownloadStatus
import io.celox.flipperripper.domain.model.QueueOrdering
import io.celox.flipperripper.domain.model.isCancellable
import io.celox.flipperripper.domain.model.isPausable
import io.celox.flipperripper.domain.repository.DownloadRepository
import io.celox.flipperripper.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl
@Inject
constructor(
    private val dao: DownloadDao,
    private val workManager: WorkManager,
    private val engine: YtDlpEngine,
    private val idGenerator: IdGenerator,
) : DownloadRepository {
    override suspend fun enqueue(request: DownloadRequest): String {
        val id = idGenerator.newId()
        val now = System.currentTimeMillis()
        dao.upsert(
            DownloadEntity(
                id = id,
                sourceUrl = request.url,
                platform = request.platform.name,
                title = request.title?.takeIf { it.isNotBlank() } ?: request.url,
                mode = request.mode.name,
                thumbnailUrl = request.thumbnailUrl,
                status = DownloadStatus.QUEUED.name,
                mediaUri = null,
                fileName = null,
                sizeBytes = null,
                // Nothing has been measured yet; a stored 0 would be a claim, not a measurement.
                progressPercent = null,
                errorKind = null,
                errorMessage = null,
                queueOrder = QueueOrdering.nextOrder(listOfNotNull(dao.maxQueueOrder())),
                quality = request.quality.name,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
        ensureQueueRunning()
        return id
    }

    /**
     * Makes sure the one queue worker is scheduled.
     *
     * `APPEND_OR_REPLACE`, not `KEEP`, because of a race that would strand a download: with KEEP, a
     * link enqueued in the moment between the running worker's last look at the table and its return
     * would find the work still "running", keep it, and then never be picked up — the row would sit
     * QUEUED until something else happened to enqueue. Appending costs at most one worker run that
     * finds nothing to do.
     */
    private fun ensureQueueRunning() {
        val work =
            OneTimeWorkRequestBuilder<DownloadQueueWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        workManager.enqueueUniqueWork(
            DownloadQueueWorker.WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            work,
        )
    }

    override fun observeHistory(): Flow<List<DownloadRecord>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeRecord(id: String): Flow<DownloadRecord?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun cancel(id: String) {
        // Only the engine process for this download — never the queue work itself, which is shared
        // by every other download waiting behind it.
        engine.cancel(id)
        val status = statusOf(id) ?: return
        // Every in-flight phase, via the domain rule — listing phases by hand here is how PREPARING
        // and PROCESSING would quietly become uncancellable the moment they were added.
        if (status.isCancellable) {
            dao.markFailed(
                id = id,
                status = DownloadStatus.CANCELLED.name,
                errorKind = "Cancelled",
                errorMessage = "Download cancelled.",
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    /**
     * Stops the transfer but keeps the partly downloaded bytes.
     *
     * The status is written **before** the engine is stopped, and that order is load-bearing: the
     * runner sees a killed process and asks the record whether this was a pause or a cancel. The
     * wrong order would delete the working directory — the `.part` file resume depends on.
     */
    override suspend fun pause(id: String) {
        val status = statusOf(id) ?: return
        if (!status.isPausable) return
        dao.updateStatus(id, DownloadStatus.PAUSED.name, System.currentTimeMillis())
        engine.cancel(id)
    }

    /** Back into the queue at the position it held — pausing does not cost you your place. */
    override suspend fun resume(id: String) {
        if (statusOf(id) != DownloadStatus.PAUSED) return
        dao.updateStatus(id, DownloadStatus.QUEUED.name, System.currentTimeMillis())
        ensureQueueRunning()
    }

    override suspend fun reorder(ids: List<String>) {
        val current =
            dao.getByStatus(REORDERABLE_STATUSES).associate { it.id to it.queueOrder }
        val now = System.currentTimeMillis()
        QueueOrdering.reorder(ids, current).forEach { (id, order) ->
            if (current[id] != order) dao.updateQueueOrder(id, order, now)
        }
    }

    override suspend fun retry(id: String) {
        dao.getById(id) ?: return
        val now = System.currentTimeMillis()
        // A retry joins the back of the queue rather than jumping ahead of downloads that have been
        // waiting, and starts with no measured progress rather than a leftover percentage.
        dao.updateQueueOrder(id, QueueOrdering.nextOrder(listOfNotNull(dao.maxQueueOrder())), now)
        dao.updateProgress(id, DownloadStatus.QUEUED.name, null, now)
        ensureQueueRunning()
    }

    override suspend fun delete(id: String) {
        engine.cancel(id)
        dao.delete(id)
    }

    override suspend fun clearHistory() {
        dao.clear()
    }

    private suspend fun statusOf(id: String): DownloadStatus? =
        dao.getById(id)?.let { runCatching { DownloadStatus.valueOf(it.status) }.getOrNull() }

    private companion object {
        const val BACKOFF_SECONDS = 30L
        val REORDERABLE_STATUSES =
            listOf(DownloadStatus.QUEUED.name, DownloadStatus.PAUSED.name)
    }
}
