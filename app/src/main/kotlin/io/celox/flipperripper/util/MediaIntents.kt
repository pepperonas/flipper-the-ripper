package io.celox.flipperripper.util

import android.content.Intent
import android.net.Uri
import io.celox.flipperripper.domain.model.DownloadMode

/** Builds intents to view saved media in the gallery/player. */
object MediaIntents {
    /**
     * ACTION_VIEW intent for a saved media URI, or null when it can't be shared safely.
     * Only `content://` URIs are used — sharing a `file://` URI would trip FileUriExposedException
     * on API 24+.
     */
    fun viewIntent(uriString: String?, mode: DownloadMode): Intent? {
        if (uriString.isNullOrBlank()) return null
        val uri = Uri.parse(uriString)
        if (uri.scheme != "content") return null
        val mime = if (mode == DownloadMode.AUDIO) "audio/*" else "video/*"
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
