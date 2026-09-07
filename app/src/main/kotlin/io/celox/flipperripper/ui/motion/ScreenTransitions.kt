package io.celox.flipperripper.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.MotionScheme
import kotlin.math.roundToInt

/**
 * The single source of truth for **screen-to-screen** motion.
 *
 * Every transition is built from the theme's [MotionScheme] springs — never from ad-hoc tweens — so
 * the whole app moves with one physics. Material 3 Expressive distinguishes two spring families
 * (Material 3 Expressive motion system, m3.material.io/blog/m3-expressive-motion-theming; token values
 * from androidx `ExpressiveMotionTokens`):
 *
 *  - **spatial** springs move things (position, size, shape). Expressive: default ζ 0.8 / k 380,
 *    fast ζ 0.6 / k 800, slow ζ 0.8 / k 200 — a little overshoot is intended.
 *  - **effects** springs change appearance (alpha, color). Expressive: ζ 1.0 (critically damped —
 *    an alpha must never overshoot) / k 1600 default, 3800 fast, 800 slow.
 *
 * Patterns (Material 3 *Transitions*, m3.material.io/styles/motion/transitions):
 *
 *  - [tabEnter]/[tabExit] — **lateral** navigation between the bottom-bar destinations. A fade
 *    through with a directional hint: the outgoing screen fades on the *fast* effects spring and
 *    drifts a few percent towards the side it is leaving for; the incoming one fades on the default
 *    effects spring while sliding in from the opposite side and settling from a slight under-scale
 *    on the default spatial spring. Because the exit is fast and the enter default, the two barely
 *    overlap — the "through" of fade-through — without a hard-coded delay.
 *  - [hierarchyEnter]/[hierarchyExit] (+ the `pop` pair) — **hierarchical** navigation to a child
 *    screen (Settings → Instagram sign-in). The child rises from the bottom (shared-axis Y) while the
 *    parent settles back slightly; popping reverses both.
 *
 * Reduced motion is decided by the caller ([rememberReduceMotion]): pass [reduceMotion] = true and
 * every transition collapses to [EnterTransition.None]/[ExitTransition.None].
 */
class ScreenTransitions(private val motion: MotionScheme, private val reduceMotion: Boolean) {
    /** Incoming tab. [forward] = the target sits to the right of the source in the bar. */
    fun tabEnter(forward: Boolean): EnterTransition {
        if (reduceMotion) return EnterTransition.None
        val sign = if (forward) 1 else -1
        return fadeIn(motion.defaultEffectsSpec()) +
            slideInHorizontally(motion.defaultSpatialSpec()) { width -> lateralOffset(width, sign) } +
            scaleIn(motion.defaultSpatialSpec(), initialScale = ScreenMotion.ENTER_SCALE)
    }

    /** Outgoing tab, drifting away from the incoming one. */
    fun tabExit(forward: Boolean): ExitTransition {
        if (reduceMotion) return ExitTransition.None
        val sign = if (forward) -1 else 1
        return fadeOut(motion.fastEffectsSpec()) +
            slideOutHorizontally(motion.defaultSpatialSpec()) { width -> lateralOffset(width, sign) }
    }

    /** A child screen rising over its parent. */
    fun hierarchyEnter(): EnterTransition {
        if (reduceMotion) return EnterTransition.None
        return fadeIn(motion.defaultEffectsSpec()) +
            slideInVertically(motion.defaultSpatialSpec()) { height -> verticalOffset(height) }
    }

    /** The parent settling back while a child covers it. */
    fun hierarchyExit(): ExitTransition {
        if (reduceMotion) return ExitTransition.None
        return fadeOut(motion.defaultEffectsSpec()) +
            scaleOut(motion.defaultSpatialSpec(), targetScale = ScreenMotion.PARENT_SCALE)
    }

    /** The parent coming back as the child pops. */
    fun hierarchyPopEnter(): EnterTransition {
        if (reduceMotion) return EnterTransition.None
        return fadeIn(motion.defaultEffectsSpec()) +
            scaleIn(motion.defaultSpatialSpec(), initialScale = ScreenMotion.PARENT_SCALE)
    }

    /** The child sinking back down as it pops. */
    fun hierarchyPopExit(): ExitTransition {
        if (reduceMotion) return ExitTransition.None
        return fadeOut(motion.fastEffectsSpec()) +
            slideOutVertically(motion.defaultSpatialSpec()) { height -> verticalOffset(height) }
    }

    private fun lateralOffset(width: Int, sign: Int): Int =
        ScreenMotion.slideOffset(width, ScreenMotion.TAB_SLIDE_FRACTION, sign)

    private fun verticalOffset(height: Int): Int =
        ScreenMotion.slideOffset(height, ScreenMotion.HIERARCHY_SLIDE_FRACTION, sign = 1)
}

/** The geometry tokens behind [ScreenTransitions] — pure, so they can be pinned by unit tests. */
object ScreenMotion {
    /** How far (fraction of width) a tab slides on the way in/out — a hint of direction, not a swipe. */
    const val TAB_SLIDE_FRACTION = 0.06f

    /** How far (fraction of height) a child screen rises from. */
    const val HIERARCHY_SLIDE_FRACTION = 0.10f

    /** Incoming tabs settle from this under-scale (M3 fade-through uses ~0.92; expressive springs overshoot, so less). */
    const val ENTER_SCALE = 0.96f

    /** A parent shrinks to this while a child covers it, so the child visibly sits *on top*. */
    const val PARENT_SCALE = 0.98f

    /**
     * Whether moving from tab [fromIndex] to [toIndex] is "forward" (to the right in the bar). Unknown
     * positions (a route outside the bar, index -1) count as forward so the transition still has a
     * consistent direction.
     */
    fun isForward(fromIndex: Int, toIndex: Int): Boolean = fromIndex < 0 || toIndex < 0 || toIndex > fromIndex

    /** The offset a slide starts/ends at, in px, for a container of [extent] px. Positive = right/down. */
    fun slideOffset(extent: Int, fraction: Float, sign: Int): Int = (extent * fraction * sign).roundToInt()
}
