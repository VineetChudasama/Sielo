package com.sielo.music.ui.theme

import androidx.compose.ui.graphics.Color

// Requested Color Palette
val PaletteDarkNavy = Color(0xFF0D1B2A)   // #0D1B2A - Deep Navy Canvas / Background
val PaletteOxfordBlue = Color(0xFF1B263B) // #1B263B - Dark Slate Surface / Cards
val PaletteSlateBlue = Color(0xFF415A77)  // #415A77 - Slate Blue Borders / Muted Accents
val PaletteSageGreen = Color(0xFF778D7A)  // #778D7A - Sage Green / Secondary Accent
val PaletteSand = Color(0xFFD4C4A8)       // #D4C4A8 - Warm Sand / Primary Accent & Play Buttons
val PaletteCream = Color(0xFFF4F1DE)      // #F4F1DE - Eggshell Cream / Primary Typography

// Semantic UI Mappings
val ObsidianBlack = PaletteDarkNavy
val BackgroundDark = PaletteDarkNavy
val SurfaceDark = PaletteOxfordBlue
val SurfaceElevated = Color(0xFF22304A)
val SurfaceGlass = Color(0xEB1B263B)
val BorderGlass = Color(0x3D415A77)
val BorderSubtle = PaletteSlateBlue.copy(alpha = 0.5f)
val BorderHighlight = PaletteSand.copy(alpha = 0.4f)

// Accents
val AccentCoral = PaletteSand
val AccentPeach = PaletteSageGreen
val AccentRose = PaletteSand
val ElectricCyan = PaletteSand
val LaserAmber = PaletteSageGreen
val NeonPurple = PaletteSlateBlue
val NeonPink = PaletteSageGreen
val EmeraldGlow = PaletteSageGreen

// Typography & State
val TextPrimary = PaletteCream
val TextSecondary = Color(0xFFB0BDC9)
val TextMuted = PaletteSlateBlue