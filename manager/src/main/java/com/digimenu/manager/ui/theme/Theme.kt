package com.digimenu.manager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DangerRed = Color(0xFFFF6B6B)
val TextSecondary = Color(0xFFB8A7AA)

/** Pure black background (AMOLED) with maroon accents. */
val MaroonPrimary = Color(0xFFC0334E)
val MaroonBright = Color(0xFFD94A63)
val MaroonDeep = Color(0xFF4A0D18)
val MaroonContainer = Color(0xFF2B0A12)
val SurfaceBlack = Color(0xFF0B0506)
val SurfaceRaised = Color(0xFF1C1013)

private val AmoledMaroon = darkColorScheme(
    primary = MaroonPrimary,
    onPrimary = Color.White,
    primaryContainer = MaroonDeep,
    onPrimaryContainer = Color(0xFFF6D3D9),
    secondary = Color(0xFF8A1B2E),
    onSecondary = Color.White,
    secondaryContainer = MaroonContainer,
    onSecondaryContainer = Color(0xFFE8B6BF),
    tertiary = Color(0xFF9A3B22),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF3A150B),
    onTertiaryContainer = Color(0xFFEEC3B6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFECE4E2),
    surface = SurfaceBlack,
    onSurface = Color(0xFFECE4E2),
    surfaceVariant = Color(0xFF1E1215),
    onSurfaceVariant = TextSecondary,
    surfaceContainer = SurfaceRaised,
    surfaceContainerHigh = Color(0xFF241417),
    surfaceContainerHighest = Color(0xFF2C1A1E),
    outline = Color(0xFF574347),
    outlineVariant = Color(0xFF2B1C20),
    error = Color(0xFFCF6679),
    onError = Color(0xFF25070B),
    errorContainer = Color(0xFF4A0D18),
    onErrorContainer = Color(0xFFFFDADF),
)

@Composable
fun DigiMenuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AmoledMaroon,
        typography = Typography(),
        content = content,
    )
}
