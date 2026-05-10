package com.alex.hubplay.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Plex/Jellyfin-inspired dark palette. The web client uses a near-black
 * background with a saturated accent (yellow-orange in HubPlay's case);
 * mobile mirrors that so brand identity is consistent across surfaces.
 *
 * Light theme is intentionally rough for now — media apps overwhelmingly
 * run in dark and that's where the design effort goes.
 */
internal val BrandPrimary       = Color(0xFFFFB300) // amber 700, HubPlay accent
internal val BrandPrimaryDim    = Color(0xFFC68400)
internal val BrandOnPrimary     = Color(0xFF1A1500)

internal val SurfaceBase        = Color(0xFF0B0B0E)
internal val SurfaceCard        = Color(0xFF15151B)
internal val SurfaceElevated    = Color(0xFF1F1F26)

internal val TextPrimary        = Color(0xFFF5F5F7)
internal val TextSecondary      = Color(0xFFB8B8C2)
internal val TextMuted          = Color(0xFF7A7A85)

internal val Outline            = Color(0xFF2A2A33)
internal val Error              = Color(0xFFFF5C57)
