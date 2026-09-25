package io.celox.flipperripper.domain.repository

import io.celox.flipperripper.domain.model.DownloadRecord
import io.celox.flipperripper.domain.model.DownloadRequest
import kotlinx.coroutines.flow.Flow

/**
 * Schedules downloads and exposes their persisted history.
 *
 * One function per thing a user can do to a download; splitting the contract to satisfy a count
 * would only scatter it across interfaces every caller then needs all of.
 */
@Suppress("TooManyFunctions")
interface DownloadRepository {
    /** Enqueue a background download. Returns the record id that tracks it. */
    suspend fun enqueue(request: DownloadRequest): String

    /** All history entries, newest first, updated live as downloads progress. */
    fun observeHistory(): Flow<List<DownloadRecord>>

    /** A single record, or null if unknown. */
    fun observeRecord(id: String): Flow<DownloadRecord?>

    /** Request cancellation of an in-flight download; its partial file is discarded. */
    suspend fun cancel(id: String)

    /** Stop an in-flight download but keep its partial file, so [resume] continues from there. */
    suspend fun pause(id: String)

    /** Put a paused download back in the queue, at the position it held. */
    suspend fun resume(id: String)

    /** Rearrange the waiting downloads; [ids] is the new order of the ones the user can move. */
    suspend fun reorder(ids: List<String>)

    /** Retry a failed/cancelled record, reusing its request. */
    suspend fun retry(id: String)

    /**
     * Delete a single history entry (does not delete the saved media). Returns the entry as it was,
     * so it can be handed to [restore] — or null if there was none.
     */
    suspend fun delete(id: String): DownloadRecord?

    /**
     * Put a deleted history entry back exactly as it was — the other half of "undo" after a swipe.
     * Only for finished entries: a download that was still pending was cancelled by [delete].
     */
    suspend fun restore(record: DownloadRecord)

    /** Clear the entire history (does not delete saved media). */
    suspend fun clearHistory()
}
