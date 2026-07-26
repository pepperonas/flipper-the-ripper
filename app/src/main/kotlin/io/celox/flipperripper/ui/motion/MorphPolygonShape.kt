package io.celox.flipperripper.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

/**
 * A [Shape] that renders a point along a [Morph] between two [RoundedPolygon]s. The morph is built
 * from normalized polygons (0..1 box) and scaled to the composable's size.
 */
class MorphPolygonShape(private val morph: Morph, private val progress: Float) : Shape {
    private val androidPath = android.graphics.Path()
    private val matrix = Matrix()

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        androidPath.rewind()
        morph.toPath(progress.coerceIn(0f, 1f), androidPath)
        val path = androidPath.asComposePath()
        matrix.reset()
        matrix.scale(size.width, size.height)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

private fun RoundedPolygon.normalizedCopy(): RoundedPolygon = RoundedPolygon(this).normalized()

/** Remember a [Morph] between two [MaterialShapes], normalized for [MorphPolygonShape]. */
@Composable
fun rememberMorph(start: RoundedPolygon, end: RoundedPolygon): Morph =
    remember(start, end) { Morph(start.normalizedCopy(), end.normalizedCopy()) }

/** A [Shape] for a static [MaterialShapes] polygon (no morph). */
@Composable
fun rememberPolygonShape(polygon: RoundedPolygon): Shape {
    val morph = rememberMorph(polygon, polygon)
    return remember(morph) { MorphPolygonShape(morph, 0f) }
}

/** A press-driven morph between [rest] and [pressed] shapes, springy, reduced-motion-safe. */
@Composable
fun Modifier.pressMorph(
    interactionSource: InteractionSource,
    rest: RoundedPolygon = MaterialShapes.Circle,
    pressed: RoundedPolygon = MaterialShapes.Cookie9Sided,
): Modifier {
    val reduce = rememberReduceMotion()
    val isPressed by interactionSource.collectIsPressedAsState()
    val morph = rememberMorph(rest, pressed)
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(isPressed, reduce) {
        val target = if (isPressed) 1f else 0f
        if (reduce) progress.snapTo(target) else progress.animateTo(target, spec)
    }
    return this.clip(MorphPolygonShape(morph, progress.value))
}
