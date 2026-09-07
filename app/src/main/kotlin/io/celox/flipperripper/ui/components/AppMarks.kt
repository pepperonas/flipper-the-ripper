package io.celox.flipperripper.ui.components

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import io.celox.flipperripper.R

/**
 * The app's own marks, drawn from vector paths in `res/drawable`.
 *
 * All three share one shape language, the same the launcher icon is built from: a soft eight-lobed
 * Material *sunny* disc with a sharp glyph punched clean through it. Material 3 Expressive asks for
 * tension between rounded and angular forms, and here that contrast carries the meaning — there is no
 * decorative blob standing in for four different ideas any more.
 *
 * Punching the glyph out instead of laying it on top is what makes one path work everywhere: filled,
 * outlined, tinted by the theme, or recoloured by the launcher for a themed icon.
 */

/** The app mark — identical to the launcher icon's foreground. Home hero and About. */
@Composable
fun AppMark(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Image(
        painter = painterResource(R.drawable.ic_app_mark),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier,
    )
}

/** The mark as an outline: the same shape, still waiting to be filled. Empty download history. */
@Composable
fun EmptyDownloadsMark(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Image(
        painter = painterResource(R.drawable.ic_downloads_empty),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier,
    )
}

/** Stands in for a thumbnail the platform did not give us: the disc with a play triangle cut out. */
@Composable
fun VideoPlaceholder(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Image(
        painter = painterResource(R.drawable.ic_video_placeholder),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier,
    )
}
