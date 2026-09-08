package com.itantra.voice.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * iTantra palette.
 *
 * Two accents and a neutral ramp, nothing else. Red and green are not decoration
 * here — they are the transceiver's state language:
 *   RED   = transmitting, armed, or an emergency alert
 *   GREEN = linked, received, or ready
 * Every other surface is neutral so those two colours are never diluted.
 */

// ─── Accents ─────────────────────────────────────────────────────────────────
val Signal = Color(0xFFD32F2F)      // transmit / alert / armed
val SignalDim = Color(0xFF8E1F1F)   // pressed and dark-theme variants
val Link = Color(0xFF2E7D32)        // connected / ready / received
val LinkDim = Color(0xFF1B5E20)

// ─── Light neutrals ──────────────────────────────────────────────────────────
val PaperLight = Color(0xFFFAFAFA)
val SurfaceLight = Color(0xFFFFFFFF)
val LineLight = Color(0xFFE0E0E0)
val InkLight = Color(0xFF1A1A1A)
val InkMutedLight = Color(0xFF6B6B6B)

// ─── Dark neutrals ───────────────────────────────────────────────────────────
val PaperDark = Color(0xFF121212)
val SurfaceDarkElevated = Color(0xFF1E1E1E)
val LineDark = Color(0xFF2E2E2E)
val InkDark = Color(0xFFF2F2F2)
val InkMutedDark = Color(0xFF9E9E9E)
