package com.example.appdev.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HeatGuardColorScheme = darkColorScheme(
    primary = HeatColors.Accent,
    onPrimary = HeatColors.OnAccent,
    primaryContainer = HeatColors.SurfaceHighest,
    onPrimaryContainer = HeatColors.TextPrimary,
    secondary = HeatColors.TextSecondary,
    onSecondary = HeatColors.Background,
    secondaryContainer = HeatColors.SurfaceHighest,
    onSecondaryContainer = HeatColors.TextPrimary,
    background = HeatColors.Background,
    onBackground = HeatColors.TextPrimary,
    surface = HeatColors.Background,
    onSurface = HeatColors.TextPrimary,
    surfaceVariant = HeatColors.SurfaceRaised,
    onSurfaceVariant = HeatColors.TextSecondary,
    surfaceContainerLowest = HeatColors.Background,
    surfaceContainerLow = HeatColors.Surface,
    surfaceContainer = HeatColors.Surface,
    surfaceContainerHigh = HeatColors.SurfaceRaised,
    surfaceContainerHighest = HeatColors.SurfaceHighest,
    outline = HeatColors.Outline,
    outlineVariant = HeatColors.OutlineSubtle,
    error = HeatColors.Critical,
    onError = HeatColors.Background,
)

/**
 * HeatGuard Pro always uses its own dark scheme; dynamic colour is deliberately disabled so the
 * green/orange/red status colours stay consistent and legible on every device.
 */
@Composable
fun HeatGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HeatGuardColorScheme,
        typography = Typography,
        content = content,
    )
}
