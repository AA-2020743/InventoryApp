package com.supermarket.inventory.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.supermarket.inventory.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = GreenPrimaryLight,
    primaryContainer = GreenPrimaryContainerLight,
    secondary = GreenSecondaryLight,
    background = BackgroundLight,
    surface = SurfaceLight,
    error = ErrorLight,
)

private val DarkColors = darkColorScheme(
    primary = GreenPrimaryDark,
    primaryContainer = GreenPrimaryContainerDark,
    secondary = GreenSecondaryDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    error = ErrorDark,
)

@Composable
fun InventoryAppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (useDark) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
