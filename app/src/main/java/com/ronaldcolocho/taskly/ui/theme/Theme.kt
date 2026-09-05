package com.ronaldcolocho.taskly.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Indigo600,
    secondary = Emerald500,
    tertiary = Amber400,
    background = Slate950,
    surface = Slate900,
    onPrimary = White,
    onSecondary = White,
    onTertiary = Slate900,
    onBackground = Slate50,
    onSurface = Slate50,
    error = Red600,
    outlineVariant = Slate800
)

private val LightColorScheme = lightColorScheme(
    primary = Indigo600,
    secondary = Emerald500,
    tertiary = Amber400,
    background = Slate50,
    surface = White,
    onPrimary = White,
    onSecondary = White,
    onTertiary = Slate900,
    onBackground = Slate900,
    onSurface = Slate900,
    error = Red600,
    outlineVariant = Slate200
)

@Composable
fun TasklyTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val currentDensity = androidx.compose.ui.platform.LocalDensity.current
    val customDensity = androidx.compose.ui.unit.Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale * 1.10f
    )

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides customDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
