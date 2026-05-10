package com.alex.hubplay.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * App-wide theme wrapper.
 *
 * Dark only — matches what every media app does (Plex, Jellyfin, Netflix,
 * Prime). The color tokens are the same as web/src/styles/globals.css so
 * the apps read as the same product.
 */
@Composable
fun HubPlayTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary               = Accent,
        onPrimary             = OnAccent,
        primaryContainer      = AccentSoft,
        onPrimaryContainer    = AccentLight,
        secondary             = AccentLight,
        onSecondary           = OnAccent,
        background            = BgBase,
        onBackground          = TextPrimary,
        surface               = BgCard,
        onSurface             = TextPrimary,
        surfaceVariant        = BgElevated,
        onSurfaceVariant      = TextSecondary,
        surfaceContainer      = BgSurface,
        surfaceContainerHigh  = BgOverlay,
        outline               = Border,
        outlineVariant        = BorderSubtle,
        error                 = ErrorRed,
    )

    MaterialTheme(
        colorScheme = colors,
        typography  = HubPlayTypography,
        content     = content,
    )
}
