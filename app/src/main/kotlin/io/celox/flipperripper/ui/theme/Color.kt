package io.celox.flipperripper.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.ui.graphics.Color
import io.celox.flipperripper.domain.model.Platform

/**
 * A dark expressive scheme derived from the light expressive baseline so both share the same
 * tonal intent. (There is no stable `expressiveDarkColorScheme()` on material3 1.4.0, so we tune a
 * dark surface set on top of the expressive light seed.)
 */
val ExpressiveDarkColors: ColorScheme =
    expressiveLightColorScheme().copy(
        primary = Color(0xFFD0BCFF),
        onPrimary = Color(0xFF381E72),
        primaryContainer = Color(0xFF4F378B),
        onPrimaryContainer = Color(0xFFEADDFF),
        secondary = Color(0xFFCCC2DC),
        secondaryContainer = Color(0xFF4A4458),
        onSecondaryContainer = Color(0xFFE8DEF8),
        tertiary = Color(0xFFEFB8C8),
        tertiaryContainer = Color(0xFF633B48),
        background = Color(0xFF141218),
        onBackground = Color(0xFFE6E0E9),
        surface = Color(0xFF141218),
        onSurface = Color(0xFFE6E0E9),
        surfaceVariant = Color(0xFF49454F),
        onSurfaceVariant = Color(0xFFCAC4D0),
        surfaceContainerLowest = Color(0xFF0F0D13),
        surfaceContainerLow = Color(0xFF1D1B20),
        surfaceContainer = Color(0xFF211F26),
        surfaceContainerHigh = Color(0xFF2B2930),
        surfaceContainerHighest = Color(0xFF36343B),
        outline = Color(0xFF938F99),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
    )

/** Brand-ish accent used for the app's playful shape motifs. */
val AccentSeed = Color(0xFF7B4DFF)

/** Per-platform accent colors used to tint the expressive platform badge. */
fun Platform.accentColor(): Color =
    when (this) {
        Platform.YOUTUBE -> Color(0xFFFF3D3D)
        Platform.INSTAGRAM -> Color(0xFFE1306C)
        Platform.TIKTOK -> Color(0xFF25F4EE)
        Platform.FACEBOOK -> Color(0xFF1877F2)
    }
