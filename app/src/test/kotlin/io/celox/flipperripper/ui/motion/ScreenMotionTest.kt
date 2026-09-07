package io.celox.flipperripper.ui.motion

import com.google.common.truth.Truth.assertThat
import io.celox.flipperripper.ui.navigation.Destination
import org.junit.Test

/**
 * The pure geometry behind the screen transitions. The springs themselves come from the theme and
 * are not re-tested here; what is pinned is that the direction and the distances stay a *hint*.
 */
class ScreenMotionTest {
    @Test
    fun `moving right in the bar is forward, left is backward`() {
        assertThat(ScreenMotion.isForward(fromIndex = 0, toIndex = 1)).isTrue()
        assertThat(ScreenMotion.isForward(fromIndex = 0, toIndex = 2)).isTrue()
        assertThat(ScreenMotion.isForward(fromIndex = 2, toIndex = 0)).isFalse()
        assertThat(ScreenMotion.isForward(fromIndex = 1, toIndex = 0)).isFalse()
    }

    @Test
    fun `a route outside the bar always reads as forward`() {
        // A child screen (Instagram sign-in) has no bar position; the pair must still agree on a
        // direction, otherwise enter and exit would slide against each other.
        assertThat(ScreenMotion.isForward(fromIndex = -1, toIndex = 2)).isTrue()
        assertThat(ScreenMotion.isForward(fromIndex = 2, toIndex = -1)).isTrue()
        assertThat(Destination.tabIndexOf("instagram_login")).isEqualTo(-1)
        assertThat(Destination.tabIndexOf(null)).isEqualTo(-1)
    }

    @Test
    fun `tab indices follow the bar order`() {
        assertThat(Destination.tabIndexOf("home")).isEqualTo(0)
        assertThat(Destination.tabIndexOf("history")).isEqualTo(1)
        assertThat(Destination.tabIndexOf("settings")).isEqualTo(2)
    }

    @Test
    fun `slides are a hint of direction, not a swipe`() {
        // On a 1080 px wide screen a tab drifts 65 px; a child rises from 10 % of the height.
        assertThat(ScreenMotion.slideOffset(1080, ScreenMotion.TAB_SLIDE_FRACTION, sign = 1)).isEqualTo(65)
        assertThat(ScreenMotion.slideOffset(1080, ScreenMotion.TAB_SLIDE_FRACTION, sign = -1)).isEqualTo(-65)
        assertThat(ScreenMotion.slideOffset(2400, ScreenMotion.HIERARCHY_SLIDE_FRACTION, sign = 1)).isEqualTo(240)
        assertThat(ScreenMotion.TAB_SLIDE_FRACTION).isLessThan(0.15f)
        assertThat(ScreenMotion.ENTER_SCALE).isAtLeast(0.9f)
        assertThat(ScreenMotion.ENTER_SCALE).isLessThan(1f)
        assertThat(ScreenMotion.PARENT_SCALE).isAtLeast(0.9f)
        assertThat(ScreenMotion.PARENT_SCALE).isLessThan(1f)
    }
}
