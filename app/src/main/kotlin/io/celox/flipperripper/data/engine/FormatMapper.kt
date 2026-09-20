package io.celox.flipperripper.data.engine

import io.celox.flipperripper.domain.model.MediaFormat

/**
 * Reduces the engine's format list to the three facts a quality choice depends on.
 *
 * Separate from the engine so it can be tested without one: the interesting part is not the copying
 * but what counts as "has video" — yt-dlp reports a missing stream as the **string** `"none"`, not
 * as a null, and treating that as a codec name would make every audio-only rendition look like a
 * video and offer tiers that do not exist.
 */
object FormatMapper {
    private const val ABSENT = "none"

    /** True when the codec field names an actual stream. */
    fun present(codec: String?): Boolean = !codec.isNullOrBlank() && codec != ABSENT

    fun toMediaFormat(height: Int, vcodec: String?, acodec: String?): MediaFormat =
        MediaFormat(
            height = height.takeIf { it > 0 },
            hasVideo = present(vcodec),
            hasAudio = present(acodec),
        )
}
