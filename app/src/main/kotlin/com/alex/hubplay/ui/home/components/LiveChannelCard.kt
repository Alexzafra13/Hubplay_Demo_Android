package com.alex.hubplay.ui.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.alex.hubplay.data.MediaItem
import com.alex.hubplay.ui.theme.Accent

/**
 * Card for the "En directo ahora" rail.
 *
 * Channels are different from movies/series in two ways the generic
 * MediaCard doesn't handle:
 *
 *   1. **Logo fallback** — a fair chunk of M3U channels have no logo at
 *      all, and our LiveNow endpoint always emits a deterministic
 *      `logo_initials` + `logo_bg` + `logo_fg` placeholder triple
 *      (same one the web LiveNowCard uses). We render that instead
 *      of leaving the card empty when channel_logo is null.
 *   2. **Identification** — without a poster, the only thing telling
 *      the user "this is RTVE2" is the channel name. Plain MediaCard
 *      hides text under the card; the LiveNow rail puts it back
 *      because the name IS the identification.
 */
@Composable
fun LiveChannelCard(
    item:      MediaItem,
    onFocused: (MediaItem) -> Unit,
    onClick:   (MediaItem) -> Unit,
    modifier:  Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (focused) 1.06f else 1.0f,
        animationSpec = tween(180),
        label         = "live-channel-scale",
    )

    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .width(220.dp)
            .scale(scale)
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) onFocused(item)
            }
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = { onClick(item) },
            ),
    ) {
        // ── Card body: 16:9 with logo OR initials placeholder ─────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(parseHex(item.logoBg) ?: MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(10.dp))
                    else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (!item.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model              = item.logoUrl,
                    contentDescription = item.title,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                )
            } else {
                Text(
                    text       = (item.logoInitials ?: initialsFromName(item.title)).take(3),
                    style      = MaterialTheme.typography.headlineLarge,
                    color      = parseHex(item.logoFg) ?: Color.White,
                    fontSize   = 42.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text       = item.title,
            style      = MaterialTheme.typography.bodyMedium,
            color      = Color.White,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
        )
        item.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
            Text(
                text     = sub,
                style    = MaterialTheme.typography.bodySmall,
                color    = Color(0xFFB0B7C5),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Best-effort initials when the server didn't send `logo_initials` (an
 * older backend that hasn't shipped that field, or a channel inserted
 * by hand). Picks the first letter of the first two whitespace-split
 * tokens, falls back to the first two characters of a single-word name.
 */
private fun initialsFromName(name: String): String {
    val cleaned = name.trim()
    if (cleaned.isEmpty()) return "TV"
    val tokens = cleaned.split(Regex("\\s+"))
    return when {
        tokens.size >= 2 -> "${tokens[0].first()}${tokens[1].first()}".uppercase()
        cleaned.length >= 2 -> cleaned.take(2).uppercase()
        else                 -> cleaned.uppercase()
    }
}

/**
 * Parse a `#rrggbb` / `rgb(r,g,b)` / `#aarrggbb` hex string into a
 * Compose Color. Returns null on anything we can't parse so the caller
 * can fall back to a theme colour rather than crash on a malformed
 * backend payload.
 */
private fun parseHex(s: String?): Color? {
    if (s.isNullOrBlank()) return null
    return try {
        val hex = s.trim().removePrefix("#")
        when (hex.length) {
            6 -> Color(("ff$hex").toLong(16))
            8 -> Color(hex.toLong(16))
            else -> null
        }
    } catch (_: Throwable) {
        null
    }
}
