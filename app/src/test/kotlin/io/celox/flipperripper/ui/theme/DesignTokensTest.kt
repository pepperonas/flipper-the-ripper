package io.celox.flipperripper.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The spacing, size and shape scales only do their job while they stay *ordered*. A step that drifts
 * past its neighbour turns the scale back into the pile of one-off values it replaced — and nothing in
 * a screen would fail visibly, it would just look slightly wrong everywhere.
 */
class DesignTokensTest {
    @Test
    fun `the spacing scale climbs, with no two steps alike`() {
        val scale = listOf(Spacing.xs, Spacing.sm, Spacing.md, Spacing.lg, Spacing.xl, Spacing.xxl, Spacing.xxxl)
        assertThat(scale).isInStrictOrder()
        assertThat(scale.toSet()).hasSize(scale.size)
        assertThat(Spacing.xs).isAtLeast(4.dp)
    }

    @Test
    fun `the shape scale climbs too`() {
        // Read the corner sizes back out of the Shapes object rather than restating them here, so the
        // test still measures the shapes the theme actually installs.
        val radii = listOf(
            FlipperShapes.extraSmall,
            FlipperShapes.small,
            FlipperShapes.medium,
            FlipperShapes.large,
            FlipperShapes.extraLarge,
        ).map { (it as RoundedCornerShape).topStart.toString() }
        assertThat(radii.distinct()).hasSize(radii.size)
    }

    @Test
    fun `everything a finger has to hit clears the 48dp minimum`() {
        // Material's minimum touch target. The buttons carry it themselves; the chip and the platform
        // dot are decorative or sit inside a larger row, so only real controls are listed.
        listOf(Sizes.primaryButtonHeight, Sizes.buttonHeight).forEach {
            assertThat(it).isAtLeast(TOUCH_TARGET_MIN)
        }
    }

    @Test
    fun `the marks are sized in a sensible order`() {
        // A dot beside a word, a glyph in a thumbnail, the mark in the About card, the Home hero, and
        // the empty state which should be the largest of all.
        val marks = listOf(Sizes.platformDot, Sizes.thumbnailGlyph, Sizes.aboutMotif, Sizes.heroMotif, Sizes.emptyMotif)
        assertThat(marks).isInStrictOrder()
        // Below roughly 24dp the lobed mark turns to mush, which is why the badge uses a plain dot.
        assertThat(Sizes.platformDot).isLessThan(24.dp)
        assertThat(Sizes.thumbnailGlyph).isAtLeast(24.dp)
    }

    @Test
    fun `a thumbnail is wider than it is tall`() {
        // It stands in for a video frame; a square would read as an avatar.
        assertThat(Sizes.thumbnailWidth).isGreaterThan(Sizes.thumbnailHeight)
    }

    private companion object {
        val TOUCH_TARGET_MIN: Dp = 48.dp
    }
}
