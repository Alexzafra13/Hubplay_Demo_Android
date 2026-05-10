package com.alex.hubplay.ui.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alex.hubplay.data.MediaItem
import com.alex.hubplay.ui.theme.Accent

/**
 * 16:9 landscape card used in every home rail.
 *
 * Focus behaviour mirrors Plex / Netflix / Prime on TV:
 *   - On focus the card scales 1.06×, the title brightens, and a teal
 *     accent border draws around it.
 *   - When focused it also calls back via `onFocused(item)` so the
 *     parent (HomeScreen → HeroSection) can crossfade the hero to
 *     this item's backdrop / overview.
 *   - Clicking (touch) or pressing center on a D-pad fires `onClick`.
 */
@Composable
fun MediaCard(
    item:        MediaItem,
    onFocused:   (MediaItem) -> Unit,
    onClick:     (MediaItem) -> Unit,
    modifier:    Modifier = Modifier,
    width:       androidx.compose.ui.unit.Dp = 220.dp,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1.0f,
        animationSpec = tween(durationMillis = 180),
        label = "media-card-scale",
    )

    Column(
        modifier = modifier
            .width(width)
            .scale(scale)
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) onFocused(item)
            }
            .clickable { onClick(item) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (focused) Modifier.border(
                        width = 2.dp,
                        color = Accent,
                        shape = RoundedCornerShape(10.dp),
                    ) else Modifier
                ),
        ) {
            AsyncImage(
                model              = item.backdropUrl ?: item.posterUrl,
                contentDescription = item.title,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
            )
            // Plex-style resume bar — only when the item has progress.
            if (item.progressPct > 0f) {
                LinearProgressIndicator(
                    progress   = { item.progressPct },
                    modifier   = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                    color      = Accent,
                    trackColor = Color.Transparent,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text       = item.title,
            style      = MaterialTheme.typography.bodyMedium,
            color      = if (focused) MaterialTheme.colorScheme.onBackground
                         else        MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
        )
        item.subtitle?.let { sub ->
            Text(
                text     = sub,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
