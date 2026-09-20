package io.celox.flipperripper.domain.model

/**
 * Whether this status means work is in flight *right now* — the question the UI asks to decide
 * whether to show a loading indicator at all.
 *
 * `QUEUED` counts because the download is scheduled and will start on its own; `PROCESSING` counts
 * because a merge or a MediaStore copy is real work the user is waiting on, and treating it as
 * finished is what once made the app claim "saved" before the file existed.
 *
 * [DownloadStatus.PAUSED] deliberately does **not** count. A paused download holds its place in the
 * queue, but nothing is happening for it, and a spinner over a card that will not move until the
 * user taps Resume is a lie.
 */
val DownloadStatus.isActive: Boolean
    get() =
        this == DownloadStatus.QUEUED ||
            this == DownloadStatus.PREPARING ||
            this == DownloadStatus.RUNNING ||
            this == DownloadStatus.PROCESSING

/**
 * Whether the download is still part of the queue — running, waiting, or paused.
 *
 * This is the line History draws between its two sections, and it is *not* the same question as
 * [isActive]: a paused download shows no spinner but still belongs above the finished ones, because
 * it is going to run.
 */
val DownloadStatus.isPending: Boolean
    get() = isActive || this == DownloadStatus.PAUSED

/**
 * Whether cancelling is still meaningful.
 *
 * Kept as its own rule rather than reusing [isActive], so that adding a phase cannot quietly make it
 * uncancellable — the mistake that once left PREPARING and PROCESSING off a hand-written list.
 */
val DownloadStatus.isCancellable: Boolean
    get() = isPending

/**
 * Whether pausing this download leaves something worth resuming.
 *
 * `PROCESSING` is excluded on purpose: the transfer is already finished there, and interrupting an
 * ffmpeg merge or a MediaStore copy leaves a half-written file and nothing to continue from. What
 * the user would get back is not a paused download but a broken one.
 */
val DownloadStatus.isPausable: Boolean
    get() =
        this == DownloadStatus.QUEUED ||
            this == DownloadStatus.PREPARING ||
            this == DownloadStatus.RUNNING

/**
 * Whether the user may drag this card to a different position.
 *
 * Only what has not started yet: reordering the running download would mean stopping it, and
 * reordering a finished one means nothing.
 */
val DownloadStatus.isReorderable: Boolean
    get() = this == DownloadStatus.QUEUED || this == DownloadStatus.PAUSED
