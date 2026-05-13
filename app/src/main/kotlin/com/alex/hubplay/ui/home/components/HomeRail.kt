package com.alex.hubplay.ui.home.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alex.hubplay.data.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RailContentPadding = 24.dp

/**
 * Width of the soft alpha fade applied to both ends of every rail.
 * Cards leaving the viewport (because the focused one was auto-
 * scrolled to the start) feather into transparency instead of being
 * sliced sharply at the rail's edge — the polish trick Plex / Apple
 * TV / Disney+ all use to hide the seam between visible and clipped
 * content.
 */
private val EdgeFadeWidth = 48.dp

/**
 * Hover dwell required before the spotlight opens for the focused
 * card. ANY focus change (incl. moving between cards while open)
 * closes the spotlight immediately and restarts this timer; only a
 * deliberate stop of [SPOTLIGHT_OPEN_DELAY_MS] re-opens it on the
 * new focused card.
 *
 * That close-on-navigate / open-on-dwell rule is what the user kept
 * asking for: rapid D-pad sweeps stay as plain compact navigation
 * (no spotlight ever appears), and the visual artifacts of trying
 * to slide a wide slot from one position to another (películas
 * detrás, huecos al moverse atrás) are gone by construction —
 * there's no in-flight slide to be inconsistent with the focused
 * card's position.
 */
private const val SPOTLIGHT_OPEN_DELAY_MS = 800L

/**
 * A titled horizontal rail.
 *
 * State model:
 *   - [focusedIndex] is the card the LazyRow currently has focus on.
 *     Updates the instant a card calls onFocused. Drives the focus
 *     border + scale on compact cards and the rail-level "focus has
 *     left this rail" detection.
 *   - `spotlightTargetIndex` is a DEBOUNCED version of focusedIndex.
 *     It only catches up to focusedIndex once the user has dwelled on
 *     the same card for [SPOTLIGHT_OPEN_DELAY_MS]. Drives the slot
 *     widening, the spotlight overlay's content and the auto-scroll.
 *
 * Why two indices: fast D-pad sweeps shouldn't toggle the spotlight
 * on every card under the cursor — that's how you get jittering
 * panels and pósters that "stay behind" because the slot animations
 * never settle. Decoupling the immediate focus signal from the
 * spotlight commit means rapid nav reads as plain compact-card
 * navigation, and only deliberate stops promote a card to the
 * spotlight.
 *
 * Landscape rails (Continue Watching, Next Up, Live Now) skip the
 * spotlight entirely — their 16:9 footprint already reads as a
 * preview.
 */
@Composable
fun HomeRail(
    title:     String,
    items:     List<MediaItem>,
    onFocused: (MediaItem) -> Unit,
    onClick:   (MediaItem) -> Unit,
    style:     CardStyle = CardStyle.Landscape,
    modifier:  Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val listState = rememberLazyListState()
    val scope     = rememberCoroutineScope()
    val canSpotlight = style == CardStyle.Portrait

    var focusedIndex         by remember { mutableStateOf<Int?>(null) }
    var spotlightTargetIndex by remember { mutableStateOf<Int?>(null) }

    // Close-on-navigate / open-on-dwell. The instant focusedIndex
    // changes we drop spotlightTargetIndex back to null (slot
    // collapses, overlay fades) and start a fresh timer. The new
    // focused card only "wins" the spotlight if the user actually
    // dwells on it for SPOTLIGHT_OPEN_DELAY_MS. Rapid D-pad sweeps
    // never see a spotlight at all — there's no in-flight slide to
    // get out of sync with the row layout.
    LaunchedEffect(focusedIndex, canSpotlight) {
        if (!canSpotlight) {
            spotlightTargetIndex = null
            return@LaunchedEffect
        }
        val current = focusedIndex
        // Always reset on any focus event so the close animation
        // kicks in immediately when the user navigates with the
        // spotlight open.
        spotlightTargetIndex = null
        if (current == null) return@LaunchedEffect
        delay(SPOTLIGHT_OPEN_DELAY_MS)
        if (focusedIndex == current) {
            spotlightTargetIndex = current
        }
    }

    // Auto-scroll: focused card always sits at the start of the
    // viewport (just past beforeContentPadding). Triggers on EVERY
    // focus change, not just when the spotlight commits, so cards
    // never sit at "second position" while focused — moving forward
    // pushes the previous card off the left edge (where the
    // [horizontalEdgeFade] feathers it out) and the next compact
    // poster slides in from the right.
    LaunchedEffect(focusedIndex) {
        val target = focusedIndex ?: return@LaunchedEffect
        scrollFocusedToStart(scope, listState, target)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text       = title,
            style      = MaterialTheme.typography.titleLarge,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(horizontal = RailContentPadding, vertical = 8.dp),
        )

        // No clipToBounds on the LazyRow: vertical clipping would
        // crop the focused card's 1.06 scale animation at the top
        // (the "se ve cortado de arriba" the user spotted on
        // Continuar viendo). Mid-collapse slivers in the leading
        // padding are masked by the dedicated leading-mask Box below
        // — they only matter while the spotlight is open anyway.
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                state                 = listState,
                contentPadding        = PaddingValues(horizontal = RailContentPadding),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier              = Modifier
                    .fillMaxWidth()
                    .horizontalEdgeFade()
                    .onFocusChanged { focusState ->
                        // hasFocus stays true while ANY descendant card
                        // is focused. Goes false the instant D-pad
                        // up/down moves to a different rail. That's
                        // the signal to clear focusedIndex which in
                        // turn closes the spotlight.
                        if (!focusState.hasFocus) {
                            focusedIndex = null
                        }
                    },
            ) {
                itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                    val isActiveSlot = canSpotlight && index == spotlightTargetIndex
                    val slotWidth by animateDpAsState(
                        targetValue   = if (isActiveSlot) SpotlightDims.totalWidth else style.defaultWidth,
                        animationSpec = tween(SPOTLIGHT_ANIM_MS),
                        label         = "slot-width",
                    )

                    MediaCard(
                        item        = item,
                        style       = style,
                        slotWidth   = slotWidth,
                        hideContent = isActiveSlot,
                        onFocused   = { focusedItem ->
                            focusedIndex = index
                            onFocused(focusedItem)
                        },
                        onClick     = onClick,
                    )
                }
            }

            // Spotlight overlay — driven by the DEBOUNCED target, not
            // the immediate focus. Stays anchored to the last settled
            // card during fast D-pad sweeps; only a dwell promotes a
            // new card into it.
            if (canSpotlight) {
                val spotlightAlpha by animateFloatAsState(
                    targetValue   = if (spotlightTargetIndex != null) 1f else 0f,
                    animationSpec = tween(SPOTLIGHT_ANIM_MS),
                    label         = "spotlight-alpha",
                )
                val current = spotlightTargetIndex?.let { items.getOrNull(it) }
                if (spotlightAlpha > 0.01f && current != null) {
                    // Leading mask: covers the 0..RailContentPadding
                    // strip so any mid-animation card sliver gets
                    // hidden behind the rail's own background colour.
                    // Height matches the spotlight; the title strips
                    // below sit beyond it but the next card's strip
                    // is far enough right that it doesn't read as a
                    // peeking artefact.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .width(RailContentPadding)
                            .height(SpotlightDims.height)
                            .alpha(spotlightAlpha)
                            .background(MaterialTheme.colorScheme.background),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = RailContentPadding)
                            .alpha(spotlightAlpha),
                    ) {
                        // Direction = 0 → AnimatedContent inside
                        // RailSpotlight does a pure fade, no slide.
                        // We never slide between cards anymore: the
                        // spotlight closes on every nav and reopens
                        // on dwell, so each open is a fresh reveal.
                        RailSpotlight(
                            state = SpotlightState(
                                item      = current,
                                direction = 0,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Specialised rail for the "En directo ahora" section. Same shell as
 * HomeRail but renders [LiveChannelCard] instead of MediaCard so that
 * channels without a logo get the initials placeholder, and the
 * channel name lives under the card. No spotlight — channels don't
 * have an overview / rating / year to populate one.
 */
@Composable
fun LiveNowRail(
    title:     String,
    items:     List<MediaItem>,
    onFocused: (MediaItem) -> Unit,
    onClick:   (MediaItem) -> Unit,
    modifier:  Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val listState = rememberLazyListState()
    val scope     = rememberCoroutineScope()
    var focusedIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(focusedIndex) {
        val target = focusedIndex ?: return@LaunchedEffect
        scrollFocusedToStart(scope, listState, target)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text       = title,
            style      = MaterialTheme.typography.titleLarge,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(horizontal = RailContentPadding, vertical = 8.dp),
        )
        LazyRow(
            state                 = listState,
            contentPadding        = PaddingValues(horizontal = RailContentPadding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier              = Modifier
                .fillMaxWidth()
                .horizontalEdgeFade()
                .onFocusChanged { focusState ->
                    if (!focusState.hasFocus) focusedIndex = null
                },
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                LiveChannelCard(
                    item      = item,
                    onFocused = { focusedItem ->
                        focusedIndex = index
                        onFocused(focusedItem)
                    },
                    onClick   = onClick,
                )
            }
        }
    }
}

/**
 * Soft alpha fade on both horizontal ends of the rail. Uses
 * BlendMode.DstIn against an offscreen layer so the gradient
 * actually erases the rendered content's alpha (instead of just
 * painting a colour on top, which would leave a visible band over
 * the rail's background). Standard "fading edge" recipe.
 */
private fun Modifier.horizontalEdgeFade(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fadePx = EdgeFadeWidth.toPx().coerceAtMost(size.width / 2f)
        if (fadePx <= 0f) return@drawWithContent
        val leftStop  = fadePx / size.width
        val rightStop = 1f - leftStop
        drawRect(
            brush = Brush.horizontalGradient(
                0f        to Color.Transparent,
                leftStop  to Color.Black,
                rightStop to Color.Black,
                1f        to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * Animate scroll so [index]'s left edge lands exactly at
 * `beforeContentPadding` — that is the same x where the spotlight
 * overlay sits, so the focused slot and the spotlight align to the
 * pixel.
 *
 * Uses animateScrollBy with a precise delta because
 * animateScrollToItem treats the call as a no-op when the item is
 * already "visible enough" — which it always is for the focused card
 * sitting under the spotlight.
 */
private fun scrollFocusedToStart(
    scope:     CoroutineScope,
    listState: LazyListState,
    index:     Int,
) {
    scope.launch {
        val info   = listState.layoutInfo
        val target = info.visibleItemsInfo.firstOrNull { it.index == index }

        if (target != null) {
            val delta = (target.offset - info.beforeContentPadding).toFloat()
            if (delta != 0f) {
                listState.animateScrollBy(
                    value         = delta,
                    animationSpec = tween(SPOTLIGHT_ANIM_MS),
                )
            }
        } else {
            listState.scrollToItem(index = index, scrollOffset = 0)
        }
    }
}
