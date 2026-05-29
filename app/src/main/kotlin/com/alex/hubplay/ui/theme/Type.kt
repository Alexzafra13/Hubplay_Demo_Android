package com.alex.hubplay.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.alex.hubplay.R

/**
 * HubPlay ships **Inter** (variable font, `res/font/inter_variable.ttf`)
 * instead of the platform default. Inter is the same geometric-grotesque
 * family that premium streaming UIs lean on — it reads cleaner than Roboto
 * against a dark backdrop and gives the app a deliberate, non-stock feel.
 *
 * minSdk is 26, so we exploit the variable-font weight axis: a single
 * binary serves every weight via [FontVariation], keeping the APK lean
 * (~860 KB for one file vs. 4 static cuts).
 */
@OptIn(ExperimentalTextApi::class)
private fun interWeight(weight: Int) = Font(
    resId             = R.font.inter_variable,
    weight            = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        weight = FontWeight(weight),
        style  = FontStyle.Normal,
    ),
)

internal val InterFamily = FontFamily(
    interWeight(400),
    interWeight(500),
    interWeight(600),
    interWeight(700),
)

/**
 * Material3 type scale, re-skinned with Inter. We start from the M3
 * defaults (good sizes / line-heights) and override:
 *  - `fontFamily` on **every** role so nothing falls back to Roboto;
 *  - tighter negative tracking on the display / headline / title scale —
 *    large Inter text looks editorial with slightly condensed letter
 *    spacing, the way Prime / Apple TV set their hero titles;
 *  - weight bumps on the big roles so titles hold up over busy artwork.
 */
private val base = Typography()

internal val HubPlayTypography = Typography(
    displayLarge   = base.displayLarge.copy(
        fontFamily = InterFamily, fontWeight = FontWeight.Bold,
        fontSize = 48.sp, lineHeight = 56.sp, letterSpacing = (-1.0).sp,
    ),
    displayMedium  = base.displayMedium.copy(
        fontFamily = InterFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp,
    ),
    displaySmall   = base.displaySmall.copy(
        fontFamily = InterFamily, fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.25).sp,
    ),
    headlineLarge  = base.headlineLarge.copy(
        fontFamily = InterFamily, fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.25).sp,
    ),
    headlineMedium = base.headlineMedium.copy(
        fontFamily = InterFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.25).sp,
    ),
    headlineSmall  = base.headlineSmall.copy(
        fontFamily = InterFamily, fontWeight = FontWeight.SemiBold,
    ),
    titleLarge     = base.titleLarge.copy(
        fontFamily = InterFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = (-0.15).sp,
    ),
    titleMedium    = base.titleMedium.copy(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold),
    titleSmall     = base.titleSmall.copy(fontFamily = InterFamily, fontWeight = FontWeight.Medium),
    bodyLarge      = base.bodyLarge.copy(
        fontFamily = InterFamily, fontSize = 16.sp, lineHeight = 24.sp,
    ),
    bodyMedium     = base.bodyMedium.copy(
        fontFamily = InterFamily, fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodySmall      = base.bodySmall.copy(fontFamily = InterFamily),
    labelLarge     = base.labelLarge.copy(
        fontFamily = InterFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    labelMedium    = base.labelMedium.copy(fontFamily = InterFamily, fontWeight = FontWeight.Medium),
    labelSmall     = base.labelSmall.copy(fontFamily = InterFamily, fontWeight = FontWeight.Medium),
)
