package io.celox.flipperripper.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import io.celox.flipperripper.domain.model.Platform
import io.celox.flipperripper.ui.motion.MorphPolygonShape
import io.celox.flipperripper.ui.motion.MotionPolicy
import io.celox.flipperripper.ui.motion.rememberMorph
import io.celox.flipperripper.ui.theme.accentColor

/**
 * Expressive platform badge: a slowly morphing [MaterialShapes] motif tinted in the platform's
 * accent color, next to the platform name in a tonal pill.
 */
@Composable
fun PlatformBadge(platform: Platform, modifier: Modifier = Modifier) {
    val accent = platform.accentColor()
    val container = accent.copy(alpha = 0.18f).compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)

    Row(
        modifier =
        modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MorphingMotif(
            modifier = Modifier.size(20.dp),
            color = accent,
        )
        Text(
            text = platform.displayName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * A **static** expressive shape motif, held at a fixed point between two polygons.
 *
 * It deliberately does not animate. Motion in this app is reserved for the one thing that is actually
 * happening — a running download, shown by its progress indicator. Decorative shapes looping in the
 * background competed with that signal and cost battery for nothing, so they simply stand still.
 */
@Composable
fun MorphingMotif(
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    val morph = rememberMorph(MaterialShapes.Cookie7Sided, MaterialShapes.Clover4Leaf)
    val shape = remember(morph) { MorphPolygonShape(morph, MotionPolicy.RESTING_PROGRESS) }
    Box(modifier.clip(shape).background(color))
}
