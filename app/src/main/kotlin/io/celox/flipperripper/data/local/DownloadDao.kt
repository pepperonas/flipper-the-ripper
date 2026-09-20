package io.celox.flipperripper.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Query(
        """
        UPDATE downloads
        SET status = :status, progressPercent = :percent, updatedAtEpochMs = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateProgress(id: String, status: String, percent: Float?, updatedAt: Long)

    @Query(
        """
        UPDATE downloads
        SET status = :status, mediaUri = :mediaUri, fileName = :fileName, sizeBytes = :sizeBytes,
            progressPercent = 100, updatedAtEpochMs = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun markCompleted(
        id: String,
        status: String,
        mediaUri: String?,
        fileName: String?,
        sizeBytes: Long?,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE downloads
        SET status = :status, errorKind = :errorKind, errorMessage = :errorMessage, updatedAtEpochMs = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun markFailed(id: String, status: String, errorKind: String?, errorMessage: String?, updatedAt: Long)

    /**
     * Status only, leaving progress and everything else alone.
     *
     * The worker used to update a record by reading it once and writing the whole row back with
     * `REPLACE`. That in-memory copy was read while the row still said QUEUED, so every such write
     * reset a running download to QUEUED / 0 % — twice per download, once after metadata resolution
     * and once at 100 % right before saving. Field-scoped updates cannot do that.
     */
    /** Title and thumbnail only, once metadata resolves. A null thumbnail keeps the existing one. */
    @Query(
        """
        UPDATE downloads
        SET title = :title,
            thumbnailUrl = COALESCE(:thumbnailUrl, thumbnailUrl),
            updatedAtEpochMs = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateMetadata(id: String, title: String, thumbnailUrl: String?, updatedAt: Long)

    /** Every row the runner may still have to deal with, in the order it will run them. */
    @Query("SELECT * FROM downloads WHERE status IN (:statuses) ORDER BY queueOrder ASC, id ASC")
    suspend fun getByStatus(statuses: List<String>): List<DownloadEntity>

    /** Status only. Used for pause/resume, which must not touch progress or the queue position. */
    @Query("UPDATE downloads SET status = :status, updatedAtEpochMs = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)

    @Query("UPDATE downloads SET queueOrder = :order, updatedAtEpochMs = :updatedAt WHERE id = :id")
    suspend fun updateQueueOrder(id: String, order: Long, updatedAt: Long)

    @Query("SELECT MAX(queueOrder) FROM downloads")
    suspend fun maxQueueOrder(): Long?

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM downloads")
    suspend fun clear()
}
