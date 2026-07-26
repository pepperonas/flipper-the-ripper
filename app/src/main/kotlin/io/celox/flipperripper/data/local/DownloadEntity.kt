package io.celox.flipperripper.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.DownloadRecord
import io.celox.flipperripper.domain.model.DownloadStatus
import io.celox.flipperripper.domain.model.Platform

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val sourceUrl: String,
    val platform: String,
    val title: String,
    val mode: String,
    val thumbnailUrl: String?,
    val status: String,
    val mediaUri: String?,
    val fileName: String?,
    val sizeBytes: Long?,
    val progressPercent: Float?,
    val errorKind: String?,
    val errorMessage: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    fun toDomain(): DownloadRecord =
        DownloadRecord(
            id = id,
            sourceUrl = sourceUrl,
            platform = runCatching { Platform.valueOf(platform) }.getOrDefault(Platform.YOUTUBE),
            title = title,
            mode = runCatching { DownloadMode.valueOf(mode) }.getOrDefault(DownloadMode.VIDEO),
            thumbnailUrl = thumbnailUrl,
            status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.QUEUED),
            mediaUri = mediaUri,
            fileName = fileName,
            sizeBytes = sizeBytes,
            errorKind = errorKind,
            errorMessage = errorMessage,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
        )

    companion object {
        fun fromDomain(record: DownloadRecord, progressPercent: Float? = null): DownloadEntity =
            DownloadEntity(
                id = record.id,
                sourceUrl = record.sourceUrl,
                platform = record.platform.name,
                title = record.title,
                mode = record.mode.name,
                thumbnailUrl = record.thumbnailUrl,
                status = record.status.name,
                mediaUri = record.mediaUri,
                fileName = record.fileName,
                sizeBytes = record.sizeBytes,
                progressPercent = progressPercent,
                errorKind = record.errorKind,
                errorMessage = record.errorMessage,
                createdAtEpochMs = record.createdAtEpochMs,
                updatedAtEpochMs = record.updatedAtEpochMs,
            )
    }
}
