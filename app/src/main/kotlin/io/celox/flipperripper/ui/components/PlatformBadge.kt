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
import io.celox.flipperripper.ui.motion.LocalDownloadsActive
import io.celox.flipperripper.ui.motion.MorphPolygonShape
import io.celox.flipperripper.ui.motion.MotionPolicy
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

/**
 * A shape that morphs between two expressive polygons **while a download is in flight**, and rests in a
 * fixed in-between shape otherwise.
 *
 * The loop used to run unconditionally, which meant an idle app animated forever — on the Home hero, the
 * empty history state and once per list item for placeholder thumbnails and platform badges. Gating it
 * on [animating] keeps the motion meaningful (it now signals actual work) and stops the constant drain.
 */
@Composable
fun MorphingMotif(
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    animating: Boolean = LocalDownloadsActive.current,
) {
    val morph = rememberMorph(MaterialShapes.Cookie7Sided, MaterialShapes.Clover4Leaf)
    val reduce = rememberReduceMotion()
    val progress = remember { Animatable(MotionPolicy.RESTING_PROGRESS) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val loop = MotionPolicy.shouldLoop(animating, reduce)
    LaunchedEffect(loop, reduce) {
        if (loop) {
            // Continue from wherever the shape currently rests, so starting a download eases in.
            while (true) {
                progress.animateTo(1f, spec)
                progress.animateTo(0f, spec)
            }
        } else if (MotionPolicy.shouldAnimateToRest(reduce)) {
            // Settle once and stop, rather than snapping mid-morph when the last download finishes.
            progress.animateTo(MotionPolicy.RESTING_PROGRESS, spec)
        } else {
            progress.snapTo(MotionPolicy.RESTING_PROGRESS)
        }
    }
    Box(
        modifier
            .graphicsLayer { rotationZ = progress.value * 30f }
            .clip(MorphPolygonShape(morph, progress.value))
            .background(color),
    )
}
