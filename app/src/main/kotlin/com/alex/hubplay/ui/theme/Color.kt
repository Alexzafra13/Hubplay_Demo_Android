package com.alex.hubplay.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colour tokens copied verbatim from web/src/styles/globals.css so the
 * Android client looks identical to the web client. Names mirror the
 * CSS variable names to make grepping across the two repos trivial.
 *
 * Theme: HubPlay "Dark editorial" — cool blue-black surfaces with a
 * luminous azure (sky-blue) accent.
 */

// ─── Surfaces ────────────────────────────────────────────────────────────────
internal val BgBase     = Color(0xFF07090E)   // page background
internal val BgSurface  = Color(0xFF0B0F17)
internal val BgCard     = Color(0xFF131722)
internal val BgElevated = Color(0xFF1A1F2E)
internal val BgOverlay  = Color(0xFF161A23)

// Brand-mark adaptive-icon background — slightly bluer than BgBase so the
// app icon stands out from the launcher background on dark wallpapers.
internal val BrandMarkBg = Color(0xFF0D1220)

// ─── Borders ─────────────────────────────────────────────────────────────────
internal val Border        = Color(0x14FFFFFF) // rgba(255,255,255,0.08) → 0x14
internal val BorderSubtle  = Color(0x0AFFFFFF) // 0.04
internal val BorderStrong  = Color(0x24FFFFFF) // 0.14

// ─── Text ────────────────────────────────────────────────────────────────────
internal val TextPrimary   = Color(0xFFE8EAF0)
internal val TextSecondary = Color(0xFF8B92A5)
internal val TextMuted     = Color(0xFF5A6072)

// ─── Accent (Azure — azul cielo) ─────────────────────────────────────────────
// Azul puro y luminoso, distinto del cian verdoso de Prime Video. Elegido
// sobre el fondo azul-negro frío para que el realce "respire" sin gritar.
internal val Accent      = Color(0xFF2A9CF0)
internal val AccentHover = Color(0xFF1B86D8)
internal val AccentLight = Color(0xFF7DC6F8)
internal val AccentSoft  = Color(0x1F2A9CF0) // rgba(42,156,240,0.12)
internal val AccentGlow  = Color(0x592A9CF0) // rgba(42,156,240,0.35)
internal val OnAccent    = Color(0xFF04233D) // dark navy for max contrast on the accent

// ─── Semantic ────────────────────────────────────────────────────────────────
internal val Success     = Color(0xFF22C55E)
internal val ErrorRed    = Color(0xFFEF4444)
internal val Warning     = Color(0xFFF59E0B)
internal val LiveRed     = Color(0xFFEF4444)

// ─── Wordmark accents (used inside the SVG-derived vector drawable) ──────────
internal val WordmarkPlay = Color(0xFF0DBFFF) // the cyan "PLAY" tail of the logo
