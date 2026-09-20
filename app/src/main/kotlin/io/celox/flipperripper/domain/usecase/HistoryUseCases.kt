package io.celox.flipperripper.domain.usecase

import io.celox.flipperripper.domain.model.DownloadRecord
import io.celox.flipperripper.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observe the full download history, newest first. */
class ObserveHistoryUseCase
@Inject
constructor(private val repository: DownloadRepository) {
    operator fun invoke(): Flow<List<DownloadRecord>> = repository.observeHistory()
}

/** Observe a single record for live progress/state. */
class ObserveRecordUseCase
@Inject
constructor(private val repository: DownloadRepository) {
    operator fun invoke(id: String): Flow<DownloadRecord?> = repository.observeRecord(id)
}

/** Cancel an in-flight download. */
class CancelDownloadUseCase
@Inject
constructor(private val repository: DownloadRepository) {
    suspend operator fun invoke(id: String) = repository.cancel(id)
}

/** Stop an in-flight download but keep what it has already fetched. */
class PauseDownloadUseCase
@Inject
constructor(private val repository: DownloadRepository) {
    suspend operator fun invoke(id: String) = repository.pause(id)
}

/** Put a paused download back into the queue, at the position it held. */
class ResumeDownloadUseCase
@Inject
constructor(private val repository: DownloadRepository) {
    suspend operator fun invoke(id: String) = repository.resume(id)
}

/** Rearrange the waiting downloads. */
class ReorderQueueUseCase
@Inject
constructor(private val repository: DownloadRepository) {
    suspend operator fun invoke(ids: List<String>) = repository.reorder(ids)
}

/** Retry a failed/cancelled download. */
class RetryDownloadUseCase
@Inject
constructor(private val repository: DownloadRepository) {
    suspend operator fun invoke(id: String) = repository.retry(id)
}

/** Delete a single history entry. */
class DeleteRecordUseCase
@Inject
constructor(private val repository: DownloadRepository) {
    suspend operator fun invoke(id: String) = repository.delete(id)
}

/** Clear the entire history. */
class ClearHistoryUseCase
@Inject
constructor(private val repository: DownloadRepository) {
    suspend operator fun invoke() = repository.clearHistory()
}
