package io.celox.flipperripper.data.repository

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.celox.flipperripper.di.IoDispatcher
import io.celox.flipperripper.domain.repository.ClipboardRepository
import io.celox.flipperripper.domain.util.ParsedUrl
import io.celox.flipperripper.domain.util.UrlParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Reads a shareable link off the system clipboard.
 *
 * Two rules here exist because this runs during app start-up, where anything slow freezes the UI:
 *
 *  1. **Never call `ClipData.Item.coerceToText()`.** For an item holding a URI it opens the owning
 *     app's content provider and reads the content to turn it into text — blocking cross-process I/O.
 *     A clipboard holding a `content://` item from a slow or unresponsive app (routine on devices with
 *     a clipboard manager or phone-link feature) would hang the app right after opening.
 *  2. **Do the read off the main thread anyway.** Even `primaryClip` is a binder call into the system
 *     clipboard service and is not guaranteed to return instantly.
 *
 * Only plain text and http(s) URIs are considered — that is all a video link can be, and neither
 * requires touching another app's provider.
 */
class ClipboardRepositoryImpl
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ClipboardRepository {
    override suspend fun peekSupportedUrl(): ParsedUrl? =
        withContext(ioDispatcher) {
            val clipboard = context.getSystemService<ClipboardManager>() ?: return@withContext null
            val clip = runCatching { clipboard.primaryClip }.getOrNull() ?: return@withContext null
            (0 until clip.itemCount)
                .asSequence()
                .map { clip.getItemAt(it) }
                .flatMap { item -> sequenceOf(item.text?.toString(), item.httpUriOrNull()) }
                .firstNotNullOfOrNull { UrlParser.extractSupported(it) }
        }

    /**
     * A URI item's string form, but only when it already is an http(s) link. `content://` and `file://`
     * items are skipped on purpose — resolving those is exactly what would block.
     */
    private fun ClipData.Item.httpUriOrNull(): String? =
        uri
            ?.takeIf { it.scheme.equals("http", ignoreCase = true) || it.scheme.equals("https", ignoreCase = true) }
            ?.toString()
}
