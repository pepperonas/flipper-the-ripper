package io.celox.flipperripper.ui.motion

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The expressive shape motifs used to loop forever, so an idle app animated continuously on the Home
 * hero, the empty history state, and once per list item for placeholder thumbnails and platform badges.
 * They are now static: the only thing that animates is a running download's progress indicator.
 */
class MotionPolicyTest {
    @Test
    fun `resting shape is a real mid-morph, not a collapsed one`() {
        // 0f or 1f would sit exactly on one of the two source polygons and read as "stuck".
        assertThat(MotionPolicy.RESTING_PROGRESS).isGreaterThan(0f)
        assertThat(MotionPolicy.RESTING_PROGRESS).isLessThan(1f)
    }
}
