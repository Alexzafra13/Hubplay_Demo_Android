package com.alex.hubplay.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.alex.hubplay.data.MediaItem

@Composable
fun LiveChannelCard(
    item:      MediaItem,
    onFocused: (MediaItem) -> Unit,
    onClick:   (MediaItem) -> Unit,
    modifier:  Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .width(240.dp)
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(parseHex(item.logoBg) ?: MaterialTheme.colorScheme.surfaceVariant)
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
                if (focused) Modifier.border(3.dp, Color.White, shape)
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

        // Channel name overlay at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.7f),
                    ),
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            Column {
                Text(
                    text       = item.title,
                    color      = Color.White,
                    fontSize   = 11.sp,
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
    }
}

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
