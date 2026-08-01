package com.kurai.musikk.ui.theme

import androidx.compose.ui.graphics.Color

// Musikk palette dark only 5 hues + neutrals
val Background = Color(0xFF0C0E14)
val Foreground = Color(0xFFEAEEF7)
val Surface = Color(0xFF161922)
val SurfaceElevated = Color(0xFF1D2230)
val Primary = Color(0xFF00E5FF)
val OnPrimary = Color(0xFF04121A)
val Accent = Color(0xFF8A2BE2)
val AccentText = Color(0xFFC79BFF)
val OnAccent = Color(0xFFF6EFFF)
val Amber = Color(0xFFFFB020)
val Emerald = Color(0xFF2BE29A)
val Destructive = Color(0xFFFF4757)
val OnDestructive = Color(0xFF1A0709)
val MutedForeground = Color(0xFF8A93A8)
val BorderHairline = Color(0x14FFFFFF)
val RingHairline = Color(0x0FFFFFFF)
val InputRing = Color(0x1AFFFFFF)
val TrackInactive = Color(0x1FFFFFFF)
val WaveMuted = Color(0x24FFFFFF)
val DeviceBody = Color(0xFF05060A)
val SystemDialogBg = Color(0xFF1F232B)
val SystemDialogText = Color(0xFFE3E3E3)
val SystemDialogBody = Color(0xFFA8ADB8)

fun channelColor(channel: Int): Color = when (channel) {
    0 -> Color(0xFFFF6B6B)
    1 -> Color(0xFF4ECDC4)
    2 -> Color(0xFF45B7D1)
    3 -> Color(0xFF96CEB4)
    4 -> Color(0xFFFFA07A)
    5 -> Color(0xFF98D8C8)
    6 -> Color(0xFFD4A5A5)
    7 -> Color(0xFFC9A0DC)
    8 -> Color(0xFFD4D4D4)
    9 -> Color(0xFFB39DDB)
    else -> MutedForeground
}

fun channelSeed(channel: Int): Int = when (channel) {
    0 -> 7
    1 -> 23
    2 -> 41
    3 -> 61
    else -> 0
}
