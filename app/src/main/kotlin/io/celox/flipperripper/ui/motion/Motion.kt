package io.celox.flipperripper.ui.motion

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * True when the system animation scale is 0 (accessibility "remove animations"). All physics-heavy
 * motion is guarded on this so the app stays fully usable with reduced motion.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale =
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        scale == 0f
    }
}

/**
 * A staggered spring "entrance": each item fades in while rising and settling from a slight
 * over/under-scale, delayed by [index]. Uses the theme's expressive spatial spring. No-op under
 * reduced motion.
 */
fun Modifier.springEntrance(
    index: Int = 0,
    staggerMillis: Int = 55,
): Modifier =
    composed {
        if (rememberReduceMotion()) return@composed this

        val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        val progress = remember { Animatable(0f) }
        val currentIndex by rememberUpdatedState(index)

        LaunchedEffect(Unit) {
            // Small startup delay proportional to position for the cascade effect.
            kotlinx.coroutines.delay(currentIndex.toLong() * staggerMillis)
            progress.animateTo(1f, spatialSpec)
        }

        val p = progress.value
        this
            .alpha(p.coerceIn(0f, 1f))
            .graphicsLayer {
                val eased = p.coerceIn(0f, 1f)
                translationY = (1f - eased) * 28.dp.toPx()
                val s = 0.92f + 0.08f * eased
                scaleX = s
                scaleY = s
            }
    }

/** A simple fade-in for elements that appear (e.g. preview cards), spring-timed, reduced-motion-safe. */
fun Modifier.fadeRiseIn(): Modifier =
    composed {
        if (rememberReduceMotion()) return@composed this
        val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        val progress = remember { Animatable(0f) }
        LaunchedEffect(Unit) { progress.animateTo(1f, spec) }
        val p = progress.value
        this.alpha(p).graphicsLayer { translationY = (1f - p) * 24.dp.toPx() }
    }
