package io.celox.flipperripper.domain.model

/**
 * What the user asked for, in the one vocabulary the UI speaks.
 *
 * "Audio only" sits in this list rather than beside it as a second control. It is what the user is
 * choosing between — a 1080p video, a 480p video, or just the sound — and splitting that across a
 * Video/Audio toggle *and* a quality picker made two controls out of one decision.
 *
 * [maxHeight] is a ceiling, not a target: picking 720p must never hand back 1080p.
 */
enum class QualityChoice(val maxHeight: Int?) {
    /** Whatever the platform offers, preferring codecs that play everywhere. The default. */
    BEST(null),
    P1080(1080),
    P720(720),
    P480(480),

    /** No video at all — the audio track, extracted to m4a. */
    AUDIO_ONLY(null),
    ;

    val isAudioOnly: Boolean get() = this == AUDIO_ONLY
}

/**
 * One downloadable rendition, reduced to what choosing a quality depends on.
 *
 * Deliberately not the engine's own format class: that carries twenty fields about codecs, bitrates
 * and manifest URLs, none of which the question "can this video be had at 720p" needs.
 */
data class MediaFormat(
    /** Video height in pixels, or null for an audio-only rendition. */
    val height: Int?,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
)
