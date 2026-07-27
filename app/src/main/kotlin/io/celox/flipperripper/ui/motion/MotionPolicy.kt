package io.celox.flipperripper.ui.motion

/**
 * Decides whether *continuous* (never-ending) motion may run.
 *
 * The expressive shape motifs used to loop forever, so an idle app kept animating on the Home hero, the
 * empty history state, every placeholder thumbnail and every platform badge at once — constant CPU and
 * battery cost with nothing happening. Looping motion is now tied to actual work: it runs only while a
 * download is queued or running.
 *
 * This governs looping motion only. One-shot effects (card entrance, screen transition, press feedback)
 * are unaffected — they end by themselves and cost nothing once settled.
 */
object MotionPolicy {
    /** The shape a motif rests at when it is not looping — mid-morph, so it still looks deliberate. */
    const val RESTING_PROGRESS = 0.5f

    /** Looping motion runs only for real work, and never when the system asks for reduced motion. */
    fun shouldLoop(hasActiveDownload: Boolean, reduceMotion: Boolean): Boolean =
        hasActiveDownload && !reduceMotion

    /**
     * Whether coming to rest should be animated. Under reduced motion the motif snaps instead, so no
     * movement happens at all.
     */
    fun shouldAnimateToRest(reduceMotion: Boolean): Boolean = !reduceMotion
}
