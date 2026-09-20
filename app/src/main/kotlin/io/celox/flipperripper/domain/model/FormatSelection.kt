package io.celox.flipperripper.domain.model

/**
 * Turns a [QualityChoice] into yt-dlp arguments, and a list of renditions into the choices worth
 * offering.
 *
 * Pure: no engine, no Android. The arguments are the part most likely to be quietly wrong — a cap
 * that does not cap, a fallback that makes a 1080p file arrive when 480p was asked for — and a rule
 * that can be read in a test is the only way that stays true.
 */
object FormatSelection {
    /** Which collection the finished file belongs in — Movies or Music. */
    fun mode(choice: QualityChoice): DownloadMode =
        if (choice.isAudioOnly) DownloadMode.AUDIO else DownloadMode.VIDEO

    /**
     * The format arguments for [choice].
     *
     * [BEST][QualityChoice.BEST] keeps the sort-only form the app has always used: prefer H.264 +
     * m4a so the muxed mp4 plays everywhere, and take the best resolution available.
     *
     * A capped tier needs a **filter**, not a sort. `-S res:720` merely prefers something near 720
     * and will happily return 1080p when that is the closest match — which is not what a person who
     * picked "720p" asked for. `height<=720` cannot do that.
     *
     * ⚠️ The chain ends in a bare `/b`: if a video exists *only* above the cap, the download still
     * happens at whatever there is. Failing would be the purer reading of "at most 720p" and the
     * worse product — and the picker hides tiers the video does not have anyway, so this fallback
     * is reached mainly by a shared link that was downloaded without resolving metadata first.
     */
    fun videoFormatArgs(choice: QualityChoice, preferProgressive: Boolean): List<String> {
        val cap = choice.maxHeight
        return when {
            preferProgressive && cap == null -> listOf("-f", "best[ext=mp4]/best")
            preferProgressive -> listOf("-f", "best[ext=mp4][height<=$cap]/best[height<=$cap]/best")
            cap == null -> listOf("-S", "vcodec:h264,res,acodec:m4a")
            else -> listOf("-f", "bv*[height<=$cap]+ba/b[height<=$cap]/b", "-S", "vcodec:h264,acodec:m4a")
        }
    }

    /**
     * The choices worth showing for a video whose renditions are known.
     *
     * A tier is offered only when the video actually **reaches** it. The first version of this rule
     * asked the other question — whether the cap could be honoured — and a real fixture showed why
     * that is wrong: "Me at the zoo" exists in 144p and 240p only, and offering "480p" for it reads
     * as a promise of 480 pixels that no amount of capping can keep.
     *
     * [BEST][QualityChoice.BEST] is always offered; it is a preference, not a promise about pixels.
     */
    fun availableTiers(formats: List<MediaFormat>): Set<QualityChoice> {
        val best = bestHeight(formats)
        val tiers = mutableSetOf(QualityChoice.BEST)
        if (best != null) {
            QualityChoice.entries
                .filter { it.maxHeight != null && it.maxHeight <= best }
                .forEach { tiers += it }
        }
        if (formats.any { it.hasAudio }) tiers += QualityChoice.AUDIO_ONLY
        return tiers
    }

    /**
     * The highest resolution on offer, for the hint beside a tier that is out of reach.
     *
     * Null when nothing says — which is also what an unresolved link looks like, and the reason the
     * picker falls back to offering everything rather than nothing.
     */
    fun bestHeight(formats: List<MediaFormat>): Int? =
        formats.filter { it.hasVideo }.mapNotNull { it.height }.maxOrNull()
}
