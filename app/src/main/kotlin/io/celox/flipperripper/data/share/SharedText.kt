package io.celox.flipperripper.data.share

/**
 * Decides whether an incoming intent is a share this app should read a link out of.
 *
 * It exists as its own rule because the manifest filter and this check have to agree and nothing
 * enforces that on its own. v1.8.2 widened the filter to every `text/` subtype so that senders which
 * label shared text as something other than `text/plain` could reach the app at all — and the
 * activity went on requiring the exact subtype. Those shares opened the app and were then dropped without a word:
 * the sheet offered the app, the tap appeared to work, and nothing happened.
 */
object SharedText {
    /** Every `text/` subtype, matching what the manifest's SEND filter accepts. */
    private const val TEXT_PREFIX = "text/"

    fun isShare(action: String?, type: String?): Boolean =
        action == android.content.Intent.ACTION_SEND && type?.startsWith(TEXT_PREFIX) == true
}
