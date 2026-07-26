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

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM downloads")
    suspend fun clear()
}
