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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alex.hubplay.data.MediaItem
import com.alex.hubplay.ui.theme.Accent

/**
 * Card aspect — what shape the artwork is.
 *
 *   Landscape  16:9  → episode stills (Continue Watching) and channel
 *                       logos (Live Now). Title sits below.
 *   Portrait   2:3   → movie / series posters. The Plex / Netflix /
 *                       Apple TV "wall of posters" pattern.
 */
enum class CardStyle(val aspect: Float, val defaultWidth: Dp) {
    Landscape(16f / 9f, 220.dp),
    Portrait(2f / 3f, 150.dp),
}

/**
 * Compact poster card used in every home rail and catalog grid.
 *
 *   - Default state: poster + title underneath.
 *   - Focused state: same poster, slightly scaled up, accent border.
 *
 * Cards never expand inline. The "rich preview" (backdrop, year,
 * rating, overview) lives in the FocusedHero billboard at the top of
 * the home screen, which the ViewModel updates with a hover debounce
 * — quick D-pad scans don't repaint, deliberate stops do. That keeps
 * the rail row uncovered no matter where the user navigates.
 */
@Composable
fun MediaCard(
    item:      MediaItem,
    onFocused: (MediaItem) -> Unit,
    onClick:   (MediaItem) -> Unit,
    style:     CardStyle = CardStyle.Landscape,
    modifier:  Modifier  = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (focused) 1.06f else 1.0f,
        animationSpec = tween(durationMillis = 180),
        label         = "media-card-scale",
    )

    val cardWidth  = style.defaultWidth
    val cardHeight = when (style) {
        CardStyle.Portrait  -> cardWidth * 1.5f                  // 225dp
        CardStyle.Landscape -> cardWidth * (9f / 16f)            // ~124dp
    }

    val imageUrl = when (style) {
        CardStyle.Portrait  -> item.posterUrl ?: item.backdropUrl
        CardStyle.Landscape -> item.backdropUrl ?: item.posterUrl
    }

    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .scale(scale)
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) onFocused(item)
            }
            .clickable(
                interactionSource = interactionSource,
                indication        = null,   // no system-grey focus overlay
                onClick           = { onClick(item) },
            ),
        horizontalAlignment = Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (focused) Modifier.border(
                        width = 2.dp,
                        color = Accent,
                        shape = RoundedCornerShape(10.dp),
                    ) else Modifier,
                ),
        ) {
            AsyncImage(
                model              = imageUrl,
                contentDescription = item.title,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
            )
            if (item.progressPct > 0f) {
                LinearProgressIndicator(
                    progress   = { item.progressPct },
                    modifier   = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    color      = Accent,
                    trackColor = Color.Transparent,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.width(cardWidth)) {
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
}
