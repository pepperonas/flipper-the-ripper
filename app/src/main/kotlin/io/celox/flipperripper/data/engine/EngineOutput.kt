package io.celox.flipperripper.data.engine

import java.io.File

/**
 * Which file in a working directory is the finished download.
 *
 * It used to be "the first non-empty file", which was only safe because the directory was wiped
 * before every run. Once a resumed download may find leftovers there, that rule would happily hand
 * back a half-transferred `.part` as the finished video.
 */
object EngineOutput {
    /** yt-dlp's own scratch files: a partial stream and its resume bookkeeping. */
    private val INTERMEDIATE = setOf("part", "ytdl", "tmp")

    /**
     * The newest complete file, or null if there is none.
     *
     * Newest rather than first: a run that fell back to a different format leaves the earlier
     * attempt's output behind, and the one just produced is the one that matters.
     */
    fun finished(files: List<File>): File? =
        files
            .filter { it.isFile && it.length() > 0 && it.extension.lowercase() !in INTERMEDIATE }
            .maxByOrNull { it.lastModified() }

    /** True when the directory holds bytes a resumed download could continue from. */
    fun hasPartialBytes(dir: File): Boolean =
        dir.isDirectory && (dir.listFiles()?.any { it.isFile && it.length() > 0 } == true)
}
