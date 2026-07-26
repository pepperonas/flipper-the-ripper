package io.celox.flipperripper.domain.repository

import io.celox.flipperripper.domain.model.DownloadRecord
import io.celox.flipperripper.domain.model.DownloadRequest
import kotlinx.coroutines.flow.Flow

/** Schedules downloads and exposes their persisted history. */
interface DownloadRepository {
    /** Enqueue a background download. Returns the record id that tracks it. */
    suspend fun enqueue(request: DownloadRequest): String

    /** All history entries, newest first, updated live as downloads progress. */
    fun observeHistory(): Flow<List<DownloadRecord>>

    /** A single record, or null if unknown. */
    fun observeRecord(id: String): Flow<DownloadRecord?>

    /** Request cancellation of an in-flight download. */
    suspend fun cancel(id: String)

    /** Retry a failed/cancelled record, reusing its request. */
    suspend fun retry(id: String)

    /** Delete a single history entry (does not delete the saved media). */
    suspend fun delete(id: String)

    /** Clear the entire history (does not delete saved media). */
    suspend fun clearHistory()
}
