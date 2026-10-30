package com.dukaan.app.core.designsystem.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DukaanLightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = SurfaceWhite,
    primaryContainer = SuccessGreenLight,
    onPrimaryContainer = DeepGreen,
    secondary = Saffron,
    onSecondary = SurfaceWhite,
    secondaryContainer = SaffronLight,
    onSecondaryContainer = SaffronDark,
    tertiary = ChartBlue,
    onTertiary = SurfaceWhite,
    background = WarmCream,
    onBackground = Charcoal,
    surface = SurfaceWhite,
    onSurface = Charcoal,
    surfaceVariant = SurfaceGray,
    onSurfaceVariant = SecondaryText,
    error = ErrorRed,
    onError = SurfaceWhite,
    errorContainer = ErrorRedLight,
    onErrorContainer = ErrorRed,
    outline = BorderColor,
    outlineVariant = DividerColor
)

@Composable
fun DukaanTheme(content: @Composable () -> Unit) {
    val colorScheme = DukaanLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = ForestGreen.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}