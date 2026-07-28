package io.celox.flipperripper.ui.motion

/**
 * Where motion is allowed in this app.
 *
 * Motion is reserved for the one thing that is genuinely happening: a running download, signalled by
 * its progress indicator. Decorative shape motifs stand still — they used to loop forever, so an idle
 * app animated continuously on the Home hero, the empty history state and once per list item for every
 * placeholder thumbnail and platform badge. That competed with the real progress signal and cost
 * battery for nothing.
 *
 * One-shot effects (card entrance, screen transition, press feedback) are unaffected: they end by
 * themselves and settle immediately.
 */
object MotionPolicy {
    /** The fixed point along the morph at which a static motif is drawn. */
    const val RESTING_PROGRESS = 0.5f
}
