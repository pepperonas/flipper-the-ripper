package io.celox.flipperripper.domain.model

/**
 * Whether this status means work is still in flight. `QUEUED` counts: the download is scheduled and
 * will start on its own, so from the user's point of view it is already running.
 */
val DownloadStatus.isActive: Boolean
    get() = this == DownloadStatus.QUEUED || this == DownloadStatus.RUNNING
