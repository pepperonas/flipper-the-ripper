package io.celox.flipperripper.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.DownloadRecord
import io.celox.flipperripper.domain.model.DownloadStatus
import io.celox.flipperripper.domain.model.Platform
import io.celox.flipperripper.domain.model.QualityChoice

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
    /** Queue position; smaller runs first. Seeded from [createdAtEpochMs] for pre-1.10 rows. */
    val queueOrder: Long,
    /** The quality the user asked for; seeded from [mode] for pre-1.10 rows. */
    val quality: String,
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
            // Carried through, not dropped: the column was written on every progress step and the
            // domain record left it behind, so the UI could only ever draw an indeterminate bar
            // while the notification counted to 100 %.
            progressPercent = progressPercent,
            mediaUri = mediaUri,
            fileName = fileName,
            sizeBytes = sizeBytes,
            errorKind = errorKind,
            errorMessage = errorMessage,
            queueOrder = queueOrder,
            quality = runCatching { QualityChoice.valueOf(quality) }.getOrDefault(QualityChoice.BEST),
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
        )

    companion object {
        fun fromDomain(record: DownloadRecord, progressPercent: Float? = record.progressPercent): DownloadEntity =
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
                queueOrder = record.queueOrder,
                quality = record.quality.name,
                createdAtEpochMs = record.createdAtEpochMs,
                updatedAtEpochMs = record.updatedAtEpochMs,
            )
    }
}
