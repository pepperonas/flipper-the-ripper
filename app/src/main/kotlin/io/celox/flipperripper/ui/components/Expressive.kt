package io.celox.flipperripper.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.celox.flipperripper.ui.motion.rememberReduceMotion
import androidx.compose.foundation.clickable as foundationClickable

/** The expressive [LoadingIndicator] (morphing shapes) — the M3 Expressive replacement for a spinner. */
@Composable
fun ExpressiveLoadingIndicator(modifier: Modifier = Modifier) {
    LoadingIndicator(modifier = modifier)
}

/**
 * A springy press effect: the element scales down with the theme's fast spatial spring while held.
 * Reduced-motion-safe.
 */
@Composable
fun Modifier.springPressed(interactionSource: InteractionSource): Modifier {
    val reduce = rememberReduceMotion()
    val pressed by interactionSource.collectIsPressedAsState()
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed, reduce) {
        val target = if (pressed) 0.94f else 1f
        if (reduce) scale.snapTo(target) else scale.animateTo(target, spec)
    }
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * An expressive connected segmented toggle with a spring-sliding selection pill. Used for
 * Video/Audio and theme selection. Physics via the theme motion scheme; reduced-motion-safe.
 */
@Composable
fun <T> SegmentedToggle(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(4.dp)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val reduce = rememberReduceMotion()
            val spec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.graphics.Color>()
            val bg by animateColorAsState(
                targetValue =
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
                animationSpec = if (reduce) androidx.compose.animation.core.snap() else spec,
                label = "seg-bg",
            )
            val fg by animateColorAsState(
                targetValue =
                if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "seg-fg",
            )
            Box(
                modifier =
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(bg)
                    .foundationClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.RadioButton,
                    ) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = fg,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
