package io.celox.flipperripper.ui.components

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.celox.flipperripper.domain.model.Platform
import io.celox.flipperripper.ui.motion.MorphPolygonShape
import io.celox.flipperripper.ui.motion.rememberMorph
import io.celox.flipperripper.ui.motion.rememberReduceMotion
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

/** A small shape that continuously morphs between two expressive polygons (a lively accent). */
@Composable
fun MorphingMotif(
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    val morph = rememberMorph(MaterialShapes.Cookie7Sided, MaterialShapes.Clover4Leaf)
    val reduce = rememberReduceMotion()
    val progress = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(reduce) {
        if (reduce) {
            progress.snapTo(0.5f)
        } else {
            while (true) {
                progress.animateTo(1f, spec)
                progress.animateTo(0f, spec)
            }
        }
    }
    Box(
        modifier
            .graphicsLayer { rotationZ = progress.value * 30f }
            .clip(MorphPolygonShape(morph, progress.value))
            .background(color),
    )
}
