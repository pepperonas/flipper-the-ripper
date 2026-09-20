package io.celox.flipperripper.domain.model

/**
 * How the stored default quality is read.
 *
 * It has to answer two keys, not one. Before 1.10 the default was a Video/Audio mode, and somebody
 * who chose "Audio" has an opinion on record; reading only the new key would quietly have given
 * them video back — the preferences equivalent of a destructive migration.
 *
 * Pure so the awkward cases (nothing stored, a value from a newer version, a downgrade) are decided
 * by tests rather than by whatever DataStore happens to hand over.
 */
object QualityPreference {
    /**
     * @param storedQuality the 1.10 key, if it has ever been written.
     * @param storedMode the pre-1.10 Video/Audio default, consulted only when the new key is absent.
     */
    fun read(storedQuality: String?, storedMode: String?): QualityChoice =
        when {
            // Present but unreadable — a downgrade, or a tier a newer build knows. The user has
            // used 1.10 and chosen something, so the pre-1.10 mode is stale: falling through to it
            // could answer "audio only" for somebody who deliberately picked a video tier.
            storedQuality != null ->
                runCatching { QualityChoice.valueOf(storedQuality) }.getOrDefault(QualityChoice.BEST)
            storedMode == DownloadMode.AUDIO.name -> QualityChoice.AUDIO_ONLY
            else -> QualityChoice.BEST
        }
}
