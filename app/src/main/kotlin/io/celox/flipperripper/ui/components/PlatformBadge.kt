package io.celox.flipperripper.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import io.celox.flipperripper.domain.model.Platform
import io.celox.flipperripper.ui.theme.Sizes
import io.celox.flipperripper.ui.theme.Spacing
import io.celox.flipperripper.ui.theme.accentColor

/**
 * The platform badge: the platform's name in a tonal pill, marked by a dot in its own colour.
 *
 * The dot is a plain circle on purpose. The app's lobed mark turns to mush below roughly 24dp, and
 * next to a word the badge only has to answer "which platform" — clarity beats decoration here.
 */
@Composable
fun PlatformBadge(platform: Platform, modifier: Modifier = Modifier) {
    val accent = platform.accentColor()
    val container = accent.copy(alpha = 0.18f).compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)

    Row(
        modifier =
        modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(Modifier.size(Sizes.platformDot).clip(CircleShape).background(accent))
        Text(
            text = platform.displayName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
