package com.alex.hubplay.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.OnAccent

/**
 * Shared hero-surface bits used by both DetailScreen (movies) and
 * SeriesScreen. Keeping them in one place keeps the two surfaces
 * visually consistent — the back pill, the focus visuals, and the
 * Netflix-style "fill width" pill button all behave identically.
 */

/**
 * Floating back button with the Accent border + scale visual that the
 * rest of the TV-focus surfaces use. Pass alignment / padding via the
 * `modifier` parameter at the call site.
 */
@Composable
fun BackPill(
    onBack:   () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (focused) 1.08f else 1.0f,
        animationSpec = tween(180),
        label         = "back-pill-scale",
    )
    IconButton(
        onClick  = onBack,
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.border(2.dp, Accent, CircleShape) else Modifier),
    ) {
        Icon(
            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Volver",
            tint               = Color.White,
        )
    }
}

/**
 * Netflix-style hero CTA pill. Two variants:
 *  - `primary = true`  → bright white background, dark text (the
 *    "Reanudar / Reproducir" main action). Optionally takes a
 *    [focusRequester] so the host screen can grant it initial focus.
 *  - `primary = false` → subdued surface-variant fill (secondary
 *    actions like "Episodios" or "Mi lista").
 *
 * Focus visual: 1.04 scale + Accent border. The caller is responsible
 * for sizing — pass `Modifier.fillMaxWidth(0.62f)` for a vertical stack,
 * `Modifier.width(170.dp)` to match a poster width, etc.
 */
@Composable
fun HeroCtaButton(
    label:          String,
    icon:           ImageVector,
    primary:        Boolean,
    enabled:        Boolean = true,
    focusRequester: FocusRequester? = null,
    onClick:        () -> Unit,
    modifier:       Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (focused) 1.04f else 1.0f,
        animationSpec = tween(180),
        label         = "cta-scale",
    )

    val bg = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        primary  -> Color.White
        else     -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
    }
    val fg = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        primary  -> OnAccent
        else     -> MaterialTheme.colorScheme.onBackground
    }

    Row(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onFocusChanged { focused = it.isFocused }
            .background(bg)
            .then(
                if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(8.dp))
                else Modifier,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = fg)
        Text(
            text       = label,
            style      = MaterialTheme.typography.titleMedium,
            color      = fg,
            fontWeight = FontWeight.SemiBold,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Round icon-only button — used for the "more options" 3-dots affordance
 * on the detail screen, and any other secondary single-icon action.
 * Focus visual matches BackPill / HeroCtaButton so it sits naturally in
 * a row with them.
 */
@Composable
fun HeroIconButton(
    icon:               ImageVector,
    contentDescription: String,
    onClick:            () -> Unit,
    enabled:            Boolean = true,
    modifier:           Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (focused) 1.08f else 1.0f,
        animationSpec = tween(180),
        label         = "icon-btn-scale",
    )
    androidx.compose.material3.IconButton(
        onClick  = onClick,
        enabled  = enabled,
        modifier = modifier
            .scale(scale)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused)
                    Modifier.border(2.dp, Accent, androidx.compose.foundation.shape.CircleShape)
                else Modifier,
            ),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = contentDescription,
            tint               = MaterialTheme.colorScheme.onBackground,
        )
    }
}
