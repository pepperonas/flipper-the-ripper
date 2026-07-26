package io.celox.flipperripper.data.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.celox.flipperripper.di.IoDispatcher
import io.celox.flipperripper.domain.model.DownloadError
import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.EngineResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * MediaStore implementation with two code paths:
 *  - API 29+ (scoped storage): insert a pending row with `RELATIVE_PATH`, stream bytes in, clear
 *    `IS_PENDING`. No storage permission required.
 *  - API 24–28 (legacy): copy into the public Movies/Music dir and trigger a media scan so the
 *    gallery/file manager pick it up. Requires `WRITE_EXTERNAL_STORAGE` (declared `maxSdkVersion=28`).
 */
class MediaStoreWriterImpl
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MediaStoreWriter {
    private companion object {
        const val SUBFOLDER = "FlipperTheRipper"
        const val MIME_MP4 = "video/mp4"
        const val MIME_M4A = "audio/mp4"
        const val BUFFER = 8 * 1024
    }

    override suspend fun save(
        source: File,
        displayName: String,
        mode: DownloadMode,
    ): EngineResult<SavedMedia> =
        withContext(ioDispatcher) {
            try {
                if (!source.exists() || source.length() == 0L) {
                    return@withContext EngineResult.Failure(
                        DownloadError.Storage("The downloaded file is missing or empty."),
                    )
                }
                val isVideo = mode == DownloadMode.VIDEO
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveScoped(source, displayName, isVideo)
                } else {
                    saveLegacy(source, displayName, isVideo)
                }
            } catch (e: java.io.IOException) {
                EngineResult.Failure(DownloadError.Storage("Could not save the file: ${e.message}"))
            } catch (e: SecurityException) {
                EngineResult.Failure(DownloadError.Storage("Storage permission denied: ${e.message}"))
            }
        }

    private fun saveScoped(source: File, displayName: String, isVideo: Boolean): EngineResult<SavedMedia> {
        val resolver = context.contentResolver
        val collection =
            if (isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
        val relativeBase = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) MIME_MP4 else MIME_M4A)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$relativeBase/$SUBFOLDER")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val uri: Uri =
            resolver.insert(collection, values)
                ?: return EngineResult.Failure(DownloadError.Storage("Could not create a media entry."))

        resolver.openOutputStream(uri)?.use { out ->
            source.inputStream().use { it.copyTo(out, BUFFER) }
        } ?: run {
            resolver.delete(uri, null, null)
            return EngineResult.Failure(DownloadError.Storage("Could not open the media entry for writing."))
        }

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return EngineResult.Success(SavedMedia(uri.toString(), displayName, source.length()))
    }

    private fun saveLegacy(source: File, displayName: String, isVideo: Boolean): EngineResult<SavedMedia> {
        @Suppress("DEPRECATION")
        val publicDir =
            Environment.getExternalStoragePublicDirectory(
                if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC,
            )
        val targetDir = File(publicDir, SUBFOLDER).apply { mkdirs() }
        val target = uniqueFile(targetDir, displayName)
        source.inputStream().use { input -> target.outputStream().use { input.copyTo(it, BUFFER) } }

        // Make it visible to gallery/file manager.
        MediaScannerConnection.scanFile(
            context,
            arrayOf(target.absolutePath),
            arrayOf(if (isVideo) MIME_MP4 else MIME_M4A),
            null,
        )
        return EngineResult.Success(
            SavedMedia(Uri.fromFile(target).toString(), target.name, target.length()),
        )
    }

    /** Avoid clobbering an existing file by appending " (n)". */
    private fun uniqueFile(dir: File, displayName: String): File {
        val candidate = File(dir, displayName)
        if (!candidate.exists()) return candidate
        val dot = displayName.lastIndexOf('.')
        val base = if (dot > 0) displayName.substring(0, dot) else displayName
        val ext = if (dot > 0) displayName.substring(dot) else ""
        var n = 1
        while (true) {
            val f = File(dir, "$base ($n)$ext")
            if (!f.exists()) return f
            n++
        }
    }
}
