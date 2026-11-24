package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AntigravityDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianBackground,
    primaryContainer = ElectricCyanDim,
    onPrimaryContainer = ElectricCyan,
    secondary = SoftIndigo,
    onSecondary = ObsidianBackground,
    secondaryContainer = SoftIndigoDim,
    onSecondaryContainer = SoftIndigo,
    tertiary = TerminalGreen,
    onTertiary = ObsidianBackground,
    tertiaryContainer = TerminalGreenDim,
    onTertiaryContainer = TerminalGreen,
    error = WarningCoral,
    onError = ObsidianBackground,
    errorContainer = WarningCoralDim,
    onErrorContainer = WarningCoral,
    background = ObsidianBackground,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBackground,
    surfaceContainerLow = ObsidianElevated,
    surfaceContainer = ObsidianSurface,
    surfaceContainerHigh = ObsidianSurfaceVariant,
    surfaceContainerHighest = Color(0xFF21283B),
    outline = ObsidianBorder,
    outlineVariant = ObsidianBorderStrong
)

private val AntigravityShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AntigravityStudioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AntigravityDarkColorScheme,
        typography = Typography,
        shapes = AntigravityShapes,
        content = content
    )
}
