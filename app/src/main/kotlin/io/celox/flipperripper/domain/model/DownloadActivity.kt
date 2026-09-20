package io.celox.flipperripper.domain.model

/**
 * Whether this status means work is still in flight — the question the UI asks to decide whether to
 * show a loading indicator at all.
 *
 * All four in-flight phases count. `QUEUED` counts because the download is scheduled and will start
 * on its own; `PROCESSING` counts because a merge or a MediaStore copy is real work the user is
 * waiting on, and treating it as finished is what once made the app claim "saved" before the file
 * existed.
 */
val DownloadStatus.isActive: Boolean
    get() =
        this == DownloadStatus.QUEUED ||
            this == DownloadStatus.PREPARING ||
            this == DownloadStatus.RUNNING ||
            this == DownloadStatus.PROCESSING
