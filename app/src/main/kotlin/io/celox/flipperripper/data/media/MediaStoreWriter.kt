package io.celox.flipperripper.data.media

import io.celox.flipperripper.domain.model.DownloadMode
import io.celox.flipperripper.domain.model.EngineResult
import java.io.File

/** The saved media, addressable by the gallery/file manager. */
data class SavedMedia(val uri: String, val displayName: String, val sizeBytes: Long)

/** Persists a downloaded file into shared media collections so it shows up in the gallery. */
interface MediaStoreWriter {
    /**
     * Copy [source] into the public Movies (video) or Music (audio) collection under a
     * "FlipperTheRipper" subfolder, named [displayName]. Returns the resulting media URI.
     */
    suspend fun save(source: File, displayName: String, mode: DownloadMode): EngineResult<SavedMedia>
}
