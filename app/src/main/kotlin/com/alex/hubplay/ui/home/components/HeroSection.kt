package com.alex.hubplay.ui.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.alex.hubplay.data.MediaItem
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.BgBase
import com.alex.hubplay.ui.theme.OnAccent
import kotlinx.coroutines.delay

/**
 * The big spotlight at the top of the Home screen.
 *
 * Two modes:
 *   - **Spotlight (default)**: auto-rotates through `spotlight` items
 *     every [autoRotateMs] ms. Pagination dots indicate position.
 *   - **Focused**: when a MediaCard further down the page is focused,
 *     [focusedOverride] is non-null and the hero crossfades to that
 *     item instead, pausing the auto-rotation. Releasing focus (the
 *     user scrolls back up to the hero, or onto a non-focusable area)
 *     resumes the spotlight from where it left off.
 *
 * Trailer auto-play on focus is a future enhancement; today the hero
 * is backdrop + info + CTAs only. Trailer URLs from /items/{id}
 * (`trailer.key` + `trailer.site`) point at YouTube/Vimeo, which on
 * Android requires either an in-app WebView or kicking out to the
 * YouTube app — both worth a dedicated design pass.
 */
@Composable
fun HeroSection(
    spotlight:        List<MediaItem>,
    focusedOverride:  MediaItem?,
    onPlay:           (MediaItem) -> Unit,
    onDetails:        (MediaItem) -> Unit,
    modifier:         Modifier = Modifier,
    autoRotateMs:     Long     = 8_000L,
) {
    if (spotlight.isEmpty() && focusedOverride == null) return
    var currentIdx by remember { mutableIntStateOf(0) }

    // Auto-rotate only while no card is focused.
    LaunchedEffect(spotlight, focusedOverride) {
        if (focusedOverride != null || spotlight.size <= 1) return@LaunchedEffect
        while (true) {
            delay(autoRotateMs)
            currentIdx = (currentIdx + 1) % spotlight.size
        }
    }

    val displayItem = focusedOverride
        ?: spotlight.getOrNull(currentIdx.coerceAtMost(spotlight.lastIndex))
        ?: return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(420.dp),
    ) {
        // ── Backdrop crossfade ──────────────────────────────────────────────
        Crossfade(
            targetState     = displayItem.id,
            animationSpec   = tween(durationMillis = 350),
            label           = "hero-backdrop",
            modifier        = Modifier.fillMaxSize(),
        ) { _ ->
            AsyncImage(
                model              = displayItem.backdropUrl ?: displayItem.posterUrl,
                contentDescription = displayItem.title,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
            )
        }

        // ── Vertical fade to bg colour at bottom + horizontal fade left ─────
        // Same trick the web hero uses so the info column reads cleanly
        // over any backdrop.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f   to Color.Transparent,
                        0.6f to BgBase.copy(alpha = 0.6f),
                        1f   to BgBase,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.55f)
                .background(
                    Brush.horizontalGradient(
                        0f to BgBase.copy(alpha = 0.92f),
                        1f to Color.Transparent,
                    ),
                ),
        )

        // ── Info column (left) crossfades content with the backdrop ────────
        AnimatedContent(
            targetState     = displayItem,
            label           = "hero-info",
            transitionSpec  = {
                (fadeIn(tween(350)) togetherWith fadeOut(tween(200)))
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .widthIn(max = 640.dp)
                .padding(horizontal = 32.dp, vertical = 28.dp),
        ) { item ->
            Column {
                Text(
                    text       = "TRENDING ESTA SEMANA",
                    style      = MaterialTheme.typography.labelMedium,
                    color      = Accent,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.height(8.dp))

                // Title — show the logo when the backend has one (Trending
                // items often do), fall back to the text title otherwise.
                if (!item.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model              = item.logoUrl,
                        contentDescription = item.title,
                        contentScale       = ContentScale.Fit,
                        modifier           = Modifier
                            .heightIn(80.dp)
                            .widthIn(max = 380.dp),
                    )
                } else {
                    Text(
                        text       = item.title,
                        style      = MaterialTheme.typography.displayMedium,
                        color      = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))

                // Meta row: rating · year · genres
                MetaRow(item)
                Spacer(Modifier.height(12.dp))

                // Overview — only when present (Trending always has it,
                // Continue Watching usually doesn't)
                item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                    Text(
                        text     = overview,
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(20.dp))
                } ?: Spacer(Modifier.height(20.dp))

                // CTAs
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onPlay(item) },
                        shape   = RoundedCornerShape(10.dp),
                        colors  = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor   = OnAccent,
                        ),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Reproducir", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { onDetails(item) },
                        shape   = RoundedCornerShape(10.dp),
                    ) {
                        Text("Ver detalles")
                    }
                }
            }
        }

        // ── Pagination dots (right). Hidden while focus override is active
        //     since the dots refer to the auto-rotated spotlight, not to
        //     wherever the user is browsing. ──────────────────────────────
        if (focusedOverride == null && spotlight.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 32.dp, vertical = 28.dp),
            ) {
                spotlight.forEachIndexed { i, _ ->
                    Box(
                        modifier = Modifier
                            .size(if (i == currentIdx) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == currentIdx) Color.White
                                else Color.White.copy(alpha = 0.35f)
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaRow(item: MediaItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item.rating?.let { rating ->
            Text(
                text       = "★ ${"%.1f".format(rating)}",
                style      = MaterialTheme.typography.bodyMedium,
                color      = Accent,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item.year?.let {
            Text(
                text  = it.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item.genres.take(3).forEach { genre ->
            Text(
                text  = "· $genre",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─── Tiny shim so `heightIn(80.dp)` works without importing the namespace.
//     Keeps the imports list shorter at the cost of one alias. ────────────
@Composable
private fun Modifier.heightIn(min: androidx.compose.ui.unit.Dp) =
    this.then(androidx.compose.foundation.layout.heightIn(min = min))
