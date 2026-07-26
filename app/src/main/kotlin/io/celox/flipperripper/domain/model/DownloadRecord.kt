package io.celox.flipperripper.domain.model

/**
 * A persisted entry in the download history.
 *
 * [status] tracks the terminal or in-flight state. For completed items [mediaUri] points at the
 * MediaStore entry (a `content://` string) so the UI can open it in the gallery.
 */
data class DownloadRecord(
    val id: String,
    val sourceUrl: String,
    val platform: Platform,
    val title: String,
    val mode: DownloadMode,
    val thumbnailUrl: String?,
    val status: DownloadStatus,
    /** MediaStore content URI as a string, once the file is saved. */
    val mediaUri: String?,
    val fileName: String?,
    val sizeBytes: Long?,
    /** Populated with [DownloadError.kind] and message when [status] is [DownloadStatus.FAILED]. */
    val errorKind: String?,
    val errorMessage: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

enum class DownloadStatus { QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED }
