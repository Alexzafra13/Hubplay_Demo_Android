package com.alex.hubplay.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Default Material3 Typography with mild weight bumps on the display /
 * headline scale so titles look right against the dark backdrop. We
 * intentionally don't ship a custom font yet — the system font keeps
 * the APK small and the look is plenty Plex-like.
 */
internal val HubPlayTypography = Typography(
    displayLarge   = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold,    lineHeight = 56.sp),
    displayMedium  = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.SemiBold, lineHeight = 44.sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp),
    titleLarge     = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    bodyLarge      = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal,   lineHeight = 24.sp),
    bodyMedium     = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal,   lineHeight = 20.sp),
    labelLarge     = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium,   lineHeight = 20.sp),
)
