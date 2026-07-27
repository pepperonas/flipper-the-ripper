package io.celox.flipperripper.ui.motion

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The expressive shape motifs used to loop forever, so an idle app animated continuously on the Home
 * hero, the empty history state, and once per list item for placeholder thumbnails and platform badges.
 * These assert the rule that ties looping motion to real work.
 */
class MotionPolicyTest {
    @Test
    fun `loops only while a download is in flight`() {
        assertThat(MotionPolicy.shouldLoop(hasActiveDownload = true, reduceMotion = false)).isTrue()
    }

    @Test
    fun `does not loop when idle`() {
        // The regression this whole change exists to prevent.
        assertThat(MotionPolicy.shouldLoop(hasActiveDownload = false, reduceMotion = false)).isFalse()
    }

    @Test
    fun `reduced motion wins over an active download`() {
        assertThat(MotionPolicy.shouldLoop(hasActiveDownload = true, reduceMotion = true)).isFalse()
    }

    @Test
    fun `idle plus reduced motion never loops`() {
        assertThat(MotionPolicy.shouldLoop(hasActiveDownload = false, reduceMotion = true)).isFalse()
    }

    @Test
    fun `settling to rest is animated normally but snaps under reduced motion`() {
        assertThat(MotionPolicy.shouldAnimateToRest(reduceMotion = false)).isTrue()
        assertThat(MotionPolicy.shouldAnimateToRest(reduceMotion = true)).isFalse()
    }

    @Test
    fun `resting shape is a real mid-morph, not a collapsed one`() {
        // 0f or 1f would sit exactly on one of the two source polygons and read as "stuck".
        assertThat(MotionPolicy.RESTING_PROGRESS).isGreaterThan(0f)
        assertThat(MotionPolicy.RESTING_PROGRESS).isLessThan(1f)
    }
}
