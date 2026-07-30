package io.celox.flipperripper.util

import android.content.Intent
import android.net.Uri
import io.celox.flipperripper.domain.model.DownloadMode

/** Builds intents to view or share saved media. */
object MediaIntents {
    /**
     * ACTION_VIEW intent for a saved media URI, or null when it can't be shared safely.
     * Only `content://` URIs are used — sharing a `file://` URI would trip FileUriExposedException
     * on API 24+.
     */
    fun viewIntent(uriString: String?, mode: DownloadMode): Intent? {
        val uri = contentUriOrNull(uriString) ?: return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeOf(mode))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * A chooser wrapping an ACTION_SEND for the saved media, or null when the URI can't be shared. The
     * MediaStore `content://` URI is passed as EXTRA_STREAM with a read grant, so any target app (chats,
     * mail, cloud) receives the actual video/audio file. As with [viewIntent], only `content://` is used.
     */
    fun shareIntent(uriString: String?, mode: DownloadMode): Intent? {
        val uri = contentUriOrNull(uriString) ?: return null
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = mimeOf(mode)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        return Intent.createChooser(send, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun contentUriOrNull(uriString: String?): Uri? {
        if (uriString.isNullOrBlank()) return null
        val uri = Uri.parse(uriString)
        return uri.takeIf { it.scheme == "content" }
    }

    private fun mimeOf(mode: DownloadMode): String =
        if (mode == DownloadMode.AUDIO) "audio/*" else "video/*"
}
