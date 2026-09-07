package io.celox.flipperripper.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale. Every gap in the UI is one of these steps — screens used to mix 4/6/8/10/12/14/
 * 16/18/20/24/28/32 dp freely, which reads as visual noise even when each value is "fine" on its own.
 */
object Spacing {
    /** Hairline gaps inside a text block (title → subtitle). */
    val xs: Dp = 4.dp

    /** Between closely related controls (a row of buttons). */
    val sm: Dp = 8.dp

    /** Between elements inside a card. */
    val md: Dp = 12.dp

    /** Card inner padding, list item gaps. */
    val lg: Dp = 16.dp

    /** Screen edge padding, between sections. */
    val xl: Dp = 20.dp

    /** Between major blocks (hero → content, preview card). */
    val xxl: Dp = 24.dp

    /** Bottom breathing room above the bar. */
    val xxxl: Dp = 32.dp
}

/** Fixed control sizes, so a "big button" is the same big button everywhere. */
object Sizes {
    /** The primary action pair on Home. */
    val primaryButtonHeight: Dp = 56.dp

    /** Secondary actions (Settings). */
    val buttonHeight: Dp = 52.dp

    /** The hero motif on Home. */
    val heroMotif: Dp = 56.dp

    /** Empty-state motif. */
    val emptyMotif: Dp = 72.dp

    /** The app mark in the About card. */
    val aboutMotif: Dp = 48.dp

    /** List thumbnails (16:9-ish). */
    val thumbnailWidth: Dp = 96.dp
    val thumbnailHeight: Dp = 56.dp

    /** The dot marking a platform in its badge. */
    val platformDot: Dp = 10.dp

    /** The placeholder glyph inside an empty thumbnail. */
    val thumbnailGlyph: Dp = 28.dp

    /** The expressive loading indicator when it sits inline with text. */
    val inlineIndicator: Dp = 32.dp

    /** Preview image height on Home. */
    val previewImageHeight: Dp = 200.dp
}
