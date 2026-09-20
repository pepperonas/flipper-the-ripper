package io.celox.flipperripper.ui.components

import androidx.compose.foundation.Canvas
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

/**
 * The 2x3 dot grid that says "this card can be dragged".
 *
 * Drawn rather than shipped as a drawable: six circles on a computed grid is less to get wrong than
 * hand-written path data, and it scales to whatever size the caller asks for. The visible mark is
 * small on purpose — what makes it usable is the 44 dp touch area around it, not the ink.
 */
@Composable
fun DragHandleMark(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Canvas(modifier = modifier) {
        val columns = 2
        val rows = 3
        // Cell centres, so the grid sits centred whatever the box is.
        val cellWidth = size.width / columns
        val cellHeight = size.height / rows
        val radius = minOf(cellWidth, cellHeight) * DOT_RADIUS_FRACTION
        for (column in 0 until columns) {
            for (row in 0 until rows) {
                drawCircle(
                    color = color,
                    radius = radius,
                    center =
                    androidx.compose.ui.geometry.Offset(
                        x = cellWidth * (column + 0.5f),
                        y = cellHeight * (row + 0.5f),
                    ),
                )
            }
        }
    }
}

/** Keeps the dots clearly separate; a larger fraction reads as a striped block rather than a grip. */
private const val DOT_RADIUS_FRACTION = 0.18f
