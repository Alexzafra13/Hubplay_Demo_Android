package com.alex.hubplay.ui.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
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
import kotlinx.coroutines.launch

/**
 * Tween for vertical snap-scrolling between page sections. Matches
 * [HomeRail]'s value so the page never feels like one component is
 * pulling against another.
 */
private const val SECTION_SNAP_ANIM_MS = 350

/**
 * The big spotlight at the top of the Home screen.
 *
 * Fixed 420dp tall — the original cinematic band height. An earlier
 * iteration forced this to viewport height so the Hero "took the
 * whole screen", but it felt sluggish: pressing Down from the Play
 * button meant scrolling a full 1000dp before the first rail
 * appeared, and the user explicitly flagged "queda muy alta y no
 * se navega fluido". 420dp keeps the cinematic backdrop while
 * letting Rail 1 peek below — closer to how Netflix actually does
 * it.
 *
 * Auto-rotates through `spotlight` items every [autoRotateMs] ms
 * with pagination dots. Independent of which card is focused
 * elsewhere on the page.
 *
 * The Play button uses a [FocusRequester] so initial focus lands
 * here rather than on the first rail's auto-focused card.
 */
@Composable
fun HeroSection(
    spotlight:    List<MediaItem>,
    onPlay:       (MediaItem) -> Unit,
    onDetails:    (MediaItem) -> Unit,
    parentScroll: ScrollState,
    modifier:     Modifier = Modifier,
    autoRotateMs: Long     = 8_000L,
) {
    if (spotlight.isEmpty()) return
    var currentIdx by remember { mutableIntStateOf(0) }
    var sectionTopY by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val playFocusRequester = remember { FocusRequester() }

    LaunchedEffect(spotlight) {
        if (spotlight.size <= 1) return@LaunchedEffect
        while (true) {
            delay(autoRotateMs)
            currentIdx = (currentIdx + 1) % spotlight.size
        }
    }

    // Claim initial focus so the LazyRow inside the first rail
    // doesn't auto-grab it on first composition and snap the page
    // past the Hero. runCatching keeps a hostile focus state (no
    // composed node yet) from crashing — should always succeed in
    // normal flow.
    LaunchedEffect(Unit) {
        runCatching { playFocusRequester.requestFocus() }
    }

    val displayItem = spotlight.getOrNull(currentIdx.coerceAtMost(spotlight.lastIndex))
        ?: return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(420.dp)
            .onGloballyPositioned { coords ->
                sectionTopY = coords.positionInParent().y
            }
            .onFocusChanged { state ->
                if (state.hasFocus) {
                    scope.launch {
                        parentScroll.animateScrollTo(
                            value         = sectionTopY.toInt(),
                            animationSpec = tween(durationMillis = SECTION_SNAP_ANIM_MS),
                        )
                    }
                }
            },
    ) {
        // ── Backdrop crossfade — stretched to fill the section so
        //    the Hero reads as a full-screen page, not a 420dp band
        //    with empty space below. ────────────────────────────────────
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
        // Same trick the web hero uses so the info column reads
        // cleanly over any backdrop. The vertical fade is now
        // stronger toward the bottom because the section is taller
        // than 420dp.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f   to Color.Transparent,
                        0.5f to BgBase.copy(alpha = 0.35f),
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

        // ── Info column (left), aligned to the bottom-left so the
        //    layout reads the same regardless of section height. ─────────
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
                    text          = "TRENDING ESTA SEMANA",
                    style         = MaterialTheme.typography.labelMedium,
                    color         = Accent,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.height(8.dp))

                // Title — logo when available, text fallback otherwise.
                if (!item.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model              = item.logoUrl,
                        contentDescription = item.title,
                        contentScale       = ContentScale.Fit,
                        modifier           = Modifier
                            .heightIn(min = 80.dp, max = 120.dp)
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

                MetaRow(item)
                Spacer(Modifier.height(12.dp))

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

                // CTAs — TV focus visuals (border + scale) mirror
                // MediaCard. The Play button gets the
                // FocusRequester so initial focus lands here.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    var playFocused by remember { mutableStateOf(false) }
                    var detailsFocused by remember { mutableStateOf(false) }
                    val playScale by animateFloatAsState(
                        targetValue   = if (playFocused) 1.06f else 1.0f,
                        animationSpec = tween(180),
                        label         = "hero-play-scale",
                    )
                    val detailsScale by animateFloatAsState(
                        targetValue   = if (detailsFocused) 1.06f else 1.0f,
                        animationSpec = tween(180),
                        label         = "hero-details-scale",
                    )
                    Button(
                        onClick = { onPlay(item) },
                        shape   = RoundedCornerShape(10.dp),
                        colors  = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor   = OnAccent,
                        ),
                        modifier = Modifier
                            .focusRequester(playFocusRequester)
                            .onFocusChanged { playFocused = it.isFocused }
                            .scale(playScale)
                            .then(
                                if (playFocused)
                                    Modifier.border(2.dp, Accent, RoundedCornerShape(10.dp))
                                else Modifier,
                            ),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Reproducir", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { onDetails(item) },
                        shape   = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .onFocusChanged { detailsFocused = it.isFocused }
                            .scale(detailsScale)
                            .then(
                                if (detailsFocused)
                                    Modifier.border(2.dp, Accent, RoundedCornerShape(10.dp))
                                else Modifier,
                            ),
                    ) {
                        Text("Ver detalles")
                    }
                }
            }
        }

        // ── Pagination dots (bottom-right). ──────────────────────────────
        if (spotlight.size > 1) {
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
