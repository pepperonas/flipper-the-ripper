package io.celox.flipperripper.domain.model

/**
 * A persisted entry in the download history — and the single source of truth for what a download is
 * doing. The History card and the notification both read this record; neither keeps its own idea of
 * the progress, which is how the two used to disagree (the notification counted to 100 % while the
 * card showed an indeterminate wave).
 *
 * [status] tracks the phase, [progressPercent] the measured progress. For completed items
 * [mediaUri] points at the MediaStore entry (a `content://` string) so the UI can open it.
 */
data class DownloadRecord(
    val id: String,
    val sourceUrl: String,
    val platform: Platform,
    val title: String,
    val mode: DownloadMode,
    val thumbnailUrl: String?,
    val status: DownloadStatus,
    /**
     * 0..100 once the engine reports a total, null while there is nothing honest to show. Never
     * invented: a phase without a measurable total keeps this null and the UI stays indeterminate.
     */
    val progressPercent: Float?,
    /** MediaStore content URI as a string, once the file is saved. */
    val mediaUri: String?,
    val fileName: String?,
    val sizeBytes: Long?,
    /** Populated with [DownloadError.kind] and message when [status] is [DownloadStatus.FAILED]. */
    val errorKind: String?,
    val errorMessage: String?,
    /**
     * Position in the download queue — smaller runs first.
     *
     * It exists because the order used to be a claim rather than a fact: every download was its own
     * WorkManager job and they ran in parallel, so "queued" said nothing about what would happen
     * next. One runner drains this column in order.
     *
     * Reordering **permutes the existing values** among the affected rows instead of renumbering
     * from zero, so a queue that was seeded from creation timestamps and a queue numbered 1, 2, 3
     * can never end up on different scales and interleave wrongly.
     */
    val queueOrder: Long,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

/**
 * The phases a download moves through.
 *
 * Before 1.9.2 there was only `RUNNING`, and it covered four very different things: resolving
 * metadata (seconds, nothing is downloading), transferring bytes, ffmpeg merging, and copying the
 * finished file into MediaStore. The card said "Downloading…" through all of it and the progress
 * bar sat at 100 % during the last two — so "done" arrived long before anything was saved.
 *
 * [RUNNING] is the user's "downloading": bytes are moving and [DownloadRecord.progressPercent]
 * means something. The name is kept because it is the value already written in every installed
 * database; renaming it would relabel existing rows for no user-visible gain.
 */
enum class DownloadStatus {
    /** Enqueued; WorkManager has not started it yet (it may be waiting for a network). */
    QUEUED,

    /** The worker is up and resolving metadata. Nothing is being transferred, so there is no percent. */
    PREPARING,

    /** Bytes are moving. This is the only phase with a meaningful percentage. */
    RUNNING,

    /** Transfer finished; ffmpeg is merging or the file is being copied into MediaStore. */
    PROCESSING,

    /**
     * Stopped by the user, with the partly transferred bytes kept on disk.
     *
     * Deliberately distinct from [CANCELLED]: cancelling throws the working directory away, pausing
     * keeps the `.part` file so resuming continues from the byte it reached. A paused download is
     * still *in the queue* — it holds its position — but nothing is running for it.
     */
    PAUSED,

    COMPLETED,
    FAILED,
    CANCELLED,
}
