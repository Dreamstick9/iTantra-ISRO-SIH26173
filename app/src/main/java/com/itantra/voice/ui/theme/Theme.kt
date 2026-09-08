package com.itantra.voice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Red on neutral for transmit, green on neutral for link. Dynamic colour is
 * deliberately not used: on a disaster-response tool the meaning of red and green must
 * not change with the user's wallpaper.
 */
private val LightColors = lightColorScheme(
    primary = Signal,
    onPrimary = Color.White,
    primaryContainer = Signal,
    onPrimaryContainer = Color.White,

    secondary = Link,
    onSecondary = Color.White,
    secondaryContainer = Link,
    onSecondaryContainer = Color.White,

    background = PaperLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = PaperLight,
    onSurfaceVariant = InkMutedLight,
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = SurfaceLight,
    surfaceContainer = SurfaceLight,
    surfaceContainerHigh = SurfaceLight,
    surfaceContainerHighest = PaperLight,
    surfaceTint = Color.Transparent,
    inverseSurface = InkLight,
    inverseOnSurface = PaperLight,
    outline = LineLight,
    outlineVariant = LineLight,

    error = Signal,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Signal,
    onPrimary = Color.White,
    primaryContainer = SignalDim,
    onPrimaryContainer = Color.White,

    secondary = Link,
    onSecondary = Color.White,
    secondaryContainer = LinkDim,
    onSecondaryContainer = Color.White,

    background = PaperDark,
    onBackground = InkDark,
    surface = SurfaceDarkElevated,
    onSurface = InkDark,
    surfaceVariant = SurfaceDarkElevated,
    onSurfaceVariant = InkMutedDark,
    surfaceContainerLowest = PaperDark,
    surfaceContainerLow = SurfaceDarkElevated,
    surfaceContainer = SurfaceDarkElevated,
    surfaceContainerHigh = SurfaceDarkElevated,
    surfaceContainerHighest = LineDark,
    surfaceTint = Color.Transparent,
    inverseSurface = InkDark,
    inverseOnSurface = PaperDark,
    outline = LineDark,
    outlineVariant = LineDark,

    error = Signal,
    onError = Color.White
)

@Composable
fun ITantraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ITantraTypography,
        shapes = ITantraShapes,
        content = content
    )
}
