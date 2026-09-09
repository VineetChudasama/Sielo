package com.sielo.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SieloColorScheme = darkColorScheme(
    primary = ElectricCyan,
    secondary = LaserAmber,
    tertiary = NeonPink,
    background = ObsidianBlack,
    surface = SurfaceDark,
    onPrimary = ObsidianBlack,
    onSecondary = ObsidianBlack,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun SieloTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SieloColorScheme,
        content = content
    )
}
