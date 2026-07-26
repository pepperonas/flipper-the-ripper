package io.celox.flipperripper.data.repository

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.celox.flipperripper.data.engine.YtDlpEngine
import io.celox.flipperripper.data.local.DownloadDao
import io.celox.flipperripper.data.local.DownloadEntity
import io.celox.flipperripper.data.work.DownloadWorker
import io.celox.flipperripper.domain.model.DownloadRecord
import io.celox.flipperripper.domain.model.DownloadRequest
import io.celox.flipperripper.domain.model.DownloadStatus
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
                progressPercent = 0f,
                errorKind = null,
                errorMessage = null,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
        scheduleWork(id)
        return id
    }

    private fun scheduleWork(id: String) {
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        val work =
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(Data.Builder().putString(DownloadWorker.KEY_RECORD_ID, id).build())
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()
        workManager.enqueueUniqueWork(
            DownloadWorker.WORK_NAME_PREFIX + id,
            ExistingWorkPolicy.KEEP,
            work,
        )
    }

    override fun observeHistory(): Flow<List<DownloadRecord>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeRecord(id: String): Flow<DownloadRecord?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun cancel(id: String) {
        workManager.cancelUniqueWork(DownloadWorker.WORK_NAME_PREFIX + id)
        engine.cancel(id)
        val record = dao.getById(id) ?: return
        if (record.status == DownloadStatus.QUEUED.name || record.status == DownloadStatus.RUNNING.name) {
            dao.markFailed(
                id = id,
                status = DownloadStatus.CANCELLED.name,
                errorKind = "Cancelled",
                errorMessage = "Download cancelled.",
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun retry(id: String) {
        val record = dao.getById(id) ?: return
        dao.updateProgress(id, DownloadStatus.QUEUED.name, 0f, System.currentTimeMillis())
        scheduleWork(id)
    }

    override suspend fun delete(id: String) {
        workManager.cancelUniqueWork(DownloadWorker.WORK_NAME_PREFIX + id)
        dao.delete(id)
    }

    override suspend fun clearHistory() {
        dao.clear()
    }

    private companion object {
        const val TAG = "flipper_download"
    }
}
