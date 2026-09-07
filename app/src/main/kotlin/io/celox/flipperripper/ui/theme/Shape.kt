package io.celox.flipperripper.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The shape scale, following Material 3's corner tokens (small 8 · medium 12 · large 16 ·
 * large-increased 20 · extra-large 28). The app used four literal radii (16/20/24/28) scattered across
 * screens; they now map onto three roles read from `MaterialTheme.shapes`:
 *
 *  - `large` (16) — thumbnails, small tiles
 *  - `largeIncreased` (20) — text fields, banners, inline notices
 *  - `extraLarge` (28) — cards and the preview
 *
 * 24 dp had no token and sat between two; cards moved up to the extra-large 28 so every card in the
 * app shares one silhouette.
 */
val FlipperShapes: Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

/** 20 dp — the M3 Expressive "large increased" corner, for fields and inline banners. */
val FieldShape = RoundedCornerShape(20.dp)
