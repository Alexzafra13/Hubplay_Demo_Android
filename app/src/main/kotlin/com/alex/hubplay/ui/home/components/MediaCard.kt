package com.alex.hubplay.ui.home.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
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
 *                       Apple TV "wall of posters" pattern. Card is
 *                       narrower so more posters fit per row.
 */
enum class CardStyle(val aspect: Float, val defaultWidth: Dp) {
    Landscape(16f / 9f, 220.dp),
    Portrait(2f / 3f, 150.dp),
}

/**
 * Shared tween duration for the focused-card expand animation and the
 * rail's auto-scroll. Keeping both in lockstep stops the visible "two
 * animations racing each other" desync when the user jumps cards.
 */
const val ANIM_MS = 380

/**
 * Trapezoid clip for the focused-card info panel: top edge full width,
 * bottom edge inset by [bottomSlantDp] on the LEFT. Gives the panel a
 * slight tilt — wider at the top, narrower at the bottom — so the
 * boundary with the backdrop never reads as a clean vertical line.
 */
private class PanelTrapezoidShape(private val bottomSlantDp: androidx.compose.ui.unit.Dp) : Shape {
    override fun createOutline(
        size:            Size,
        layoutDirection: LayoutDirection,
        density:         Density,
    ): Outline {
        val slantPx = with(density) { bottomSlantDp.toPx() }
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(slantPx, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Card used in every home rail.
 *
 *   - Default state: poster only.
 *   - Focused state (Portrait cards only): card EXPANDS horizontally
 *     with an info panel pegado to the right of the poster — same
 *     pattern Netflix's "My List" hover preview uses. Adjacent cards
 *     in the LazyRow re-flow smoothly because the width is animated
 *     via animateDpAsState rather than swapped abruptly.
 *
 * Landscape cards don't expand laterally — their 16:9 footprint is
 * already wide enough that the extra panel would push the row past the
 * viewport edge. They keep the simple border-Accent + scale focus
 * visual.
 */
@Composable
fun MediaCard(
    item:           MediaItem,
    onFocused:      (MediaItem) -> Unit,
    onClick:        (MediaItem) -> Unit,
    style:          CardStyle = CardStyle.Landscape,
    expandOnFocus:  Boolean   = true,
    modifier:       Modifier  = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (focused) 1.06f else 1.0f,
        animationSpec = tween(durationMillis = 180),
        label         = "media-card-scale",
    )

    // Catalog grids (Movies / Series / Live TV) opt out of the panel
    // expansion — there the focus highlight + scale is enough, and the
    // expansion would just push neighbour cards around inside a static
    // grid. Home rails keep the default behaviour.
    val canExpand    = expandOnFocus && style == CardStyle.Portrait

    // 2s hover-to-expand delay (Netflix Web pattern). Navigating
    // between cards reads as "the rail slides under me"; only when
    // the user pauses on a card do we splay it open.
    var isExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(focused, canExpand) {
        if (focused && canExpand) {
            delay(2_000L)
            isExpanded = true
        } else {
            isExpanded = false
        }
    }

    // Row height is locked to the card's natural aspect so the rail
    // never grows vertically when a Portrait card expands. Landscape
    // cards (Continue Watching, Live Now) keep their own 16:9 height
    // — earlier the row was forced to 225dp and the 220-wide
    // landscape thumbnails got stretched into squares.
    val rowHeight = when (style) {
        CardStyle.Portrait  -> style.defaultWidth * 1.5f                  // 225dp
        CardStyle.Landscape -> style.defaultWidth * (9f / 16f)            // 124dp
    }
    // Smaller than the strict 16:9 derivation so the focused card
    // doesn't bulldoze the whole rail — backdrop is wide enough to
    // read as a still but doesn't crop key art too much.
    val backdropWidth = rowHeight * 1.4f                                  // ~315dp (portrait)
    val panelWidth    = 240.dp
    // Panel mounts on top of the backdrop's right edge by this much,
    // so the white surface visually crops the RIGHT side of the
    // backdrop — the left side of the backdrop stays untouched.
    val panelOverlap  = 80.dp
    // Slant on the panel's LEFT edge: the top-left sits at x = 0,
    // the bottom-left sits at x = panelSlant (shifted right). Result
    // is a trapezoid with the top wider than the bottom — the boundary
    // between backdrop and white panel is a visible diagonal line,
    // not a flat vertical edge.
    val panelSlant    = 56.dp

    // Expand is animated (visible reveal); collapse is INSTANT (0ms)
    // — when the user moves to the next card we don't want the old
    // one to slowly retract in place. The rail's auto-scroll carries
    // the now-compact card off-screen to the left while the new one
    // takes the leftmost position; the user never sees a retraction.
    val widthSpec = if (isExpanded) tween<androidx.compose.ui.unit.Dp>(ANIM_MS)
                    else            tween<androidx.compose.ui.unit.Dp>(0)
    val imageWidth by animateDpAsState(
        targetValue   = if (isExpanded) backdropWidth else style.defaultWidth,
        animationSpec = widthSpec,
        label         = "card-image-width",
    )
    val panelW by animateDpAsState(
        targetValue   = if (isExpanded) panelWidth else 0.dp,
        animationSpec = widthSpec,
        label         = "card-panel-width",
    )
    // Total card width: panel overlays the backdrop by `panelOverlap` dp.
    val totalCardWidth = imageWidth + (panelW - panelOverlap.coerceAtMost(panelW))

    // Default image used in compact (non-focused) state. The expanded
    // state swaps in the backdrop explicitly via Crossfade below so
    // we don't accidentally show the poster stretched to 16:9.
    val compactImageUrl = when (style) {
        CardStyle.Portrait  -> item.posterUrl ?: item.backdropUrl
        CardStyle.Landscape -> item.backdropUrl ?: item.posterUrl
    }

    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            // Focused card floats above its neighbours so any visual
            // overlap (during the expansion animation, or when a card
            // ends up wider than its layout slot) always reads as
            // "this is the foreground" — never half-eaten by the
            // card next to it.
            .zIndex(if (focused) 1f else 0f)
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
        // ── Picture + (optional) overlapping panel ────────────────────────
        // A Box (not a Row) lets the panel SIT ON TOP of the backdrop's
        // right edge. The panel's left ~70dp is a transparent→white
        // gradient so the backdrop bleeds into the panel, exactly the
        // "montando con la parte blanca" effect Netflix uses.
        Box(
            modifier = Modifier
                .width(totalCardWidth)
                .height(rowHeight)
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
            // ── Picture half (left) ──────────────────────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(imageWidth)
                    .fillMaxHeight()
                    .clipToBounds(),
            ) {
                Crossfade(
                    targetState   = isExpanded,
                    // Same expand-yes / collapse-instant rule as the
                    // widths. When the card collapses, the picture
                    // snaps back to the poster with no fade — the
                    // collapse only happens after the user has moved
                    // focus elsewhere and the rail is scrolling the
                    // card off-screen anyway.
                    animationSpec = if (isExpanded) tween(ANIM_MS) else tween(0),
                    label         = "media-card-image",
                ) { expanded ->
                    AsyncImage(
                        model              = if (expanded)
                                                (item.backdropUrl ?: compactImageUrl)
                                             else
                                                compactImageUrl,
                        contentDescription = item.title,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize(),
                    )
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

            // ── Info panel (right) — SOLID white trapezoid ──────────────
            // No gradient, no fade. The panel's clip is a trapezoid:
            // its top-left corner is at x = 0 (full width on top), its
            // bottom-left corner is at x = panelSlant (narrower at
            // the bottom). That diagonal left edge crops the backdrop
            // from the right — the backdrop's LEFT stays whole, only
            // its right gets covered.
            if (canExpand) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(panelW)
                        .fillMaxHeight()
                        .clip(PanelTrapezoidShape(bottomSlantDp = panelSlant))
                        .background(Color.White),
                ) {
                    CardInfoPanel(
                        item = item,
                        modifier = Modifier
                            .width(panelWidth)
                            .fillMaxHeight()
                            .padding(
                                // Push text past the slanted left edge
                                // so the title doesn't get clipped by
                                // the diagonal at the bottom of the
                                // panel.
                                start  = panelSlant + 16.dp,
                                end    = 18.dp,
                                top    = 18.dp,
                                bottom = 18.dp,
                            ),
                    )
                }
            }
        }

        // ── Title + subtitle under the poster (web-style) ────────────────
        // Constrained to the poster's own width — not the animated total
        // — so the labels don't stretch sideways when the card expands.
        // Pure white for the title (TextPrimary tone reads "grey" against
        // BgBase, which the user flagged earlier).
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.width(style.defaultWidth)) {
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
                    color    = Color(0xFFB0B7C5),       // lighter grey than TextSecondary
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CardInfoPanel(item: MediaItem, modifier: Modifier = Modifier) {
    // Palette deliberately diverges from the rest of the dark theme —
    // this is a "ticket / info card" anchored to the focused poster, à
    // la Netflix's web hover preview. Near-black text on off-white
    // background gives it the visual weight that says "look here".
    val panelTextPrimary   = Color(0xFF1A1A1A)
    val panelTextSecondary = Color(0xFF555B66)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text       = item.title,
            style      = MaterialTheme.typography.titleMedium,
            color      = panelTextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item.rating?.let {
                Text(
                    text       = "★ ${"%.1f".format(it)}",
                    style      = MaterialTheme.typography.labelMedium,
                    color      = Accent,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                )
            }
            item.year?.let {
                Text(
                    text     = it.toString(),
                    style    = MaterialTheme.typography.labelMedium,
                    color    = panelTextSecondary,
                    maxLines = 1,
                )
            }
            item.genres.firstOrNull()?.let { g ->
                Text("·", style = MaterialTheme.typography.labelMedium,
                     color = panelTextSecondary)
                Text(
                    text     = g,
                    style    = MaterialTheme.typography.labelMedium,
                    color    = panelTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!item.overview.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text     = item.overview,
                style    = MaterialTheme.typography.bodySmall,
                color    = panelTextSecondary,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
