package com.alex.hubplay.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * App-wide theme wrapper. We only ship dark for now — this matches user
 * expectation for media apps (Plex, Jellyfin, Netflix, Prime Video all
 * default to dark and so do most TV launchers).
 */
@Composable
fun HubPlayTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary           = BrandPrimary,
        onPrimary         = BrandOnPrimary,
        primaryContainer  = BrandPrimaryDim,
        onPrimaryContainer = BrandOnPrimary,
        background        = SurfaceBase,
        onBackground      = TextPrimary,
        surface           = SurfaceCard,
        onSurface         = TextPrimary,
        surfaceVariant    = SurfaceElevated,
        onSurfaceVariant  = TextSecondary,
        outline           = Outline,
        error             = Error,
    )

    MaterialTheme(
        colorScheme = colors,
        typography  = HubPlayTypography,
        content     = content,
    )
}
