package org.isro.itantra.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    secondary = EmeraldGreen,
    tertiary = WarningOrange,
    background = SpaceBlack,
    surface = DarkNavySurface,
    onPrimary = SpaceBlack,
    onSecondary = SpaceBlack,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun ITantraTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
