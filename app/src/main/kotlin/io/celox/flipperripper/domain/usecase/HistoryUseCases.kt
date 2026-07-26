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
