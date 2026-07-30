package com.example.mobile_app_herp.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * "Back of house" — the service corridor, not the lobby.
 *
 * This app gets used in cold rooms and linen stores by people holding a trolley
 * in the other hand, so the palette comes from that world: painted metal, enamel
 * signage, and the brass of a key tag. The light ground is a COOL grey (painted
 * steel), not the warm cream every app reaches for — warmth reads boutique, and
 * nothing about a stock count is boutique.
 */

// Grounds and surfaces
val Ink = Color(0xFF12151A)      // dark ground; also the text colour on light
val Slate = Color(0xFF1B212A)    // raised surface in dark
val SlateEdge = Color(0xFF2A3340)
val Enamel = Color(0xFFEDF0F3)   // light ground — painted metal
val Chalk = Color(0xFFFFFFFF)    // raised surface in light
val ChalkEdge = Color(0xFFD6DCE2)

// Text
val Bright = Color(0xFFE8EBEE)
val MutedDark = Color(0xFF98A1AC)
val MutedLight = Color(0xFF5A6470)

/**
 * The accent, and the only warm colour in the system: the brass of a hotel key
 * tag. Two values because brass legible on ink is too pale on enamel.
 */
val Brass = Color(0xFFC08A2E)
val BrassDeep = Color(0xFF8A6220)

// Status. Each hue comes from what it means, not from a ramp: brass = still on
// the rack, steam = work under way, moss = settled.
val Steam = Color(0xFF2E7D8F)
val SteamDim = Color(0xFF63B5C6)
val Moss = Color(0xFF4A7C4E)
val MossDim = Color(0xFF7FBA84)
val Rust = Color(0xFFB3402A)
val RustDim = Color(0xFFE08774)
