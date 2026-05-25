package com.alex.hubplay.ui.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.alex.hubplay.data.MediaItem
import com.alex.hubplay.ui.theme.Accent

enum class CardStyle(val aspect: Float, val defaultWidth: Dp) {
    Landscape(16f / 9f, 220.dp),
    Portrait(2f / 3f, 150.dp),
}

@Composable
fun MediaCard(
    item:         MediaItem,
    onFocused:    (MediaItem) -> Unit,
    onClick:      (MediaItem) -> Unit,
    style:        CardStyle = CardStyle.Landscape,
    slotWidth:    Dp        = style.defaultWidth,
    hideContent:  Boolean   = false,
    modifier:     Modifier  = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (focused && !hideContent) 1.06f else 1.0f,
        animationSpec = tween(durationMillis = 180),
        label         = "media-card-scale",
    )

    val cardHeight = when (style) {
        CardStyle.Portrait  -> style.defaultWidth * 1.5f
        CardStyle.Landscape -> style.defaultWidth * (9f / 16f)
    }

    val imageUrl = when (style) {
        CardStyle.Portrait  -> item.posterUrl ?: item.backdropUrl
        CardStyle.Landscape -> item.backdropUrl ?: item.posterUrl
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .width(slotWidth)
            .height(cardHeight)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .alpha(if (hideContent) 0f else 1f)
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) onFocused(item)
            }
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = { onClick(item) },
            )
            .then(
                if (focused && !hideContent) Modifier.border(
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

        // Title overlay at bottom of card
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.8f),
                    ),
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Column {
                Text(
                    text       = item.title,
                    color      = Color.White,
                    fontSize   = 12.sp,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                )
                item.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                    Text(
                        text     = sub,
                        color    = Color(0xFFB0B7C5),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

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
}
