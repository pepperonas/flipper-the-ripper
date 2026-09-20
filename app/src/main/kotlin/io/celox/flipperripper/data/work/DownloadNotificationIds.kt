package io.celox.flipperripper.data.work

/**
 * The notification ids a download uses.
 *
 * There have to be two, and that is the whole point of this file. The ongoing progress notification
 * belongs to WorkManager's foreground service, and WorkManager **cancels that id** when the worker
 * finishes. Until 1.9.2 the "Download complete" notification was posted under the same id moments
 * earlier, so WorkManager took it down again: a successful download left nothing in the shade at
 * all (measured — zero notifications at +0 s, +2 s and +5 s after completion).
 *
 * A terminal notification therefore gets an id of its own, which nothing else may cancel.
 */
object DownloadNotificationIds {
    /**
     * Keeps the two ids apart for every record id. Any fixed non-zero mask does that; this one is
     * arbitrary and only has to stay stable, because an id that changed between app versions would
     * strand a notification nobody can replace.
     */
    private const val TERMINAL_MASK = 0x5445524D // "TERM"

    /** The ongoing notification, owned by WorkManager's foreground service. */
    fun progress(recordId: String): Int = recordId.hashCode()

    /** The completed / failed notification, which must outlive the worker. */
    fun terminal(recordId: String): Int = recordId.hashCode() xor TERMINAL_MASK
}
