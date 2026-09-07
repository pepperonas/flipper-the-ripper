package io.celox.flipperripper.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.celox.flipperripper.domain.model.ThemeMode

/**
 * Material 3 **Expressive** theme: installs the spring-based [MotionScheme.expressive] physics
 * system, expressive color roles and dynamic color (Android 12+). Components across the app read
 * `MaterialTheme.motionScheme` for their spatial/effects springs.
 */
@Composable
fun FlipperTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(themeMode)
    val context = LocalContext.current
    val colorScheme: ColorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dark -> ExpressiveDarkColors
            else -> expressiveLightColorScheme()
        }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = FlipperShapes,
        content = content,
    )
}

/**
 * Whether the app renders dark for [themeMode]. Shared by the theme and by the system-bar styling
 * in `MainActivity`: the status-bar icons must follow the *app's* choice, not the OS — with the app
 * forced to Light on a dark OS the clock and icons stayed white on a white surface.
 */
@Composable
fun isDarkTheme(themeMode: ThemeMode): Boolean =
    when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
