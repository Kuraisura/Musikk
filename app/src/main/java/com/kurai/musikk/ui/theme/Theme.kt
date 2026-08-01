package com.kurai.musikk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MusikkColorScheme = darkColorScheme(
    primary          = Primary,
    onPrimary        = OnPrimary,
    secondary        = SurfaceElevated,
    tertiary         = Accent,
    background       = Background,
    onBackground     = Foreground,
    surface          = Surface,
    onSurface        = Foreground,
    surfaceVariant   = SurfaceElevated,
    onSurfaceVariant = MutedForeground,
    error            = Destructive,
    outline          = BorderHairline,
)

@Composable
fun MusikkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme  = MusikkColorScheme,
        typography   = MusikkTypography,
        shapes       = MusikkShapes,
        content      = content,
    )
}
