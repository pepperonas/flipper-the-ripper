package io.celox.flipperripper.data.repository

import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.celox.flipperripper.domain.repository.ClipboardRepository
import io.celox.flipperripper.domain.util.ParsedUrl
import io.celox.flipperripper.domain.util.UrlParser
import javax.inject.Inject

class ClipboardRepositoryImpl
@Inject
constructor(@ApplicationContext private val context: Context) : ClipboardRepository {
    override fun peekSupportedUrl(): ParsedUrl? {
        val clipboard = context.getSystemService<ClipboardManager>() ?: return null
        if (!clipboard.hasPrimaryClip()) return null
        val clip = clipboard.primaryClip ?: return null
        for (i in 0 until clip.itemCount) {
            val text = clip.getItemAt(i).coerceToText(context)?.toString()
            UrlParser.extractSupported(text)?.let { return it }
        }
        return null
    }
}
