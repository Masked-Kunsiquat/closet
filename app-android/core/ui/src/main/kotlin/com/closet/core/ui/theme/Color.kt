package com.closet.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Parity: Neutral palette from tokens.ts (dark-first).
 */
val Black = Color(0xFF000000)
val White = Color(0xFFFFFFFF)

val Gray100 = Color(0xFFF5F5F5)

val Surface0 = Color(0xFF0A0A0A) // Deepest background
val Surface1 = Color(0xFF111111) // Card base
val Surface2 = Color(0xFF1A1A1A) // Elevated
val Surface3 = Color(0xFF242424) // Input/Chip

val Border = Color(0xFF2A2A2A)
val BorderMuted = Color(0xFF1E1E1E)

val TextPrimary = Color(0xFFF5F5F5)
val TextSecondary = Color(0xFFA0A0A0)
val TextDisabled = Color(0xFF555555)

val ErrorColor = Color(0xFFEF4444)
val WarningColor = Color(0xFFF59E0B)
val SuccessColor = Color(0xFF22C55E)

/**
 * Parity: Accent Palettes from tokens.ts.
 * We'll use these to generate Material 3 ColorSchemes.
 */
enum class ClosetAccent(val primary: Color, val muted: Color) {
    Amber(Color(0xFFF59E0B), Color(0xFFB45309)),
    Coral(Color(0xFFF97316), Color(0xFFC2410C)),
    Sage(Color(0xFF84CC16), Color(0xFF4D7C0F)),
    Sky(Color(0xFF38BDF8), Color(0xFF0369A1)),
    Lavender(Color(0xFFA78BFA), Color(0xFF6D28D9)),
    Rose(Color(0xFFFB7185), Color(0xFFBE123C))
}
