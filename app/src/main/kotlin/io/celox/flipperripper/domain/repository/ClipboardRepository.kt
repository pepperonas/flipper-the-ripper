package io.celox.flipperripper.domain.repository

import io.celox.flipperripper.domain.util.ParsedUrl

/** Reads the system clipboard and surfaces a supported URL if present. */
interface ClipboardRepository {
    /**
     * Return a supported URL currently on the clipboard, or null.
     *
     * Suspending by contract: reading the clipboard means binder calls into the system clipboard
     * service, so it must never run on the main thread.
     */
    suspend fun peekSupportedUrl(): ParsedUrl?
}
