package io.celox.flipperripper.domain.model

/**
 * Whether work is in flight *right now* — the question the UI asks before it draws a spinner or a
 * progress bar.
 *
 * `PROCESSING` counts, because a merge or a MediaStore copy is real work the user is waiting on, and
 * treating it as finished is what once made the app claim "saved" before the file existed.
 *
 * ⚠️ `QUEUED` counted here until the download queue existed, and that was defensible then: a
 * download started the moment it was enqueued, so QUEUED was a blink. With one runner draining the
 * queue it can be minutes, and a card that shows a moving progress bar for something nobody has
 * started yet is simply untrue — measured on the device, two waiting downloads were both drawing a
 * wavy indeterminate bar.
 *
 * [DownloadStatus.PAUSED] does not count either, for the same reason: nothing is happening for it
 * until the user taps Resume.
 */
val DownloadStatus.isActive: Boolean
    get() =
        this == DownloadStatus.PREPARING ||
            this == DownloadStatus.RUNNING ||
            this == DownloadStatus.PROCESSING

/** Enqueued and waiting its turn. Nothing is happening for it yet, but it will run on its own. */
val DownloadStatus.isWaiting: Boolean
    get() = this == DownloadStatus.QUEUED

/**
 * Whether the download is still part of the queue — running, waiting, or paused.
 *
 * This is the line History draws between its two sections, and it is *not* the same question as
 * [isActive]: a waiting or paused download shows no spinner but still belongs above the finished
 * ones, because it is going to run.
 */
val DownloadStatus.isPending: Boolean
    get() = isActive || isWaiting || this == DownloadStatus.PAUSED

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
    get() = isWaiting || this == DownloadStatus.PREPARING || this == DownloadStatus.RUNNING

/**
 * Whether the user may drag this card to a different position.
 *
 * Only what has not started yet: reordering the running download would mean stopping it, and
 * reordering a finished one means nothing.
 */
val DownloadStatus.isReorderable: Boolean
    get() = isWaiting || this == DownloadStatus.PAUSED
