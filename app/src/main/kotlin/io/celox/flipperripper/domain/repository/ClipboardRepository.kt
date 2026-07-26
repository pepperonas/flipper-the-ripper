package io.celox.flipperripper.domain.repository

import io.celox.flipperripper.domain.util.ParsedUrl

/** Reads the system clipboard and surfaces a supported URL if present. */
interface ClipboardRepository {
    /** Return a supported URL currently on the clipboard, or null. */
    fun peekSupportedUrl(): ParsedUrl?
}
