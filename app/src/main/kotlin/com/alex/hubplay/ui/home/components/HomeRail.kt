package com.alex.hubplay.ui.home.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alex.hubplay.data.MediaItem
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
 * Vertical headroom kept INSIDE the rail's offscreen compositing layer
 * so the focused card's 1.06 scale animation doesn't get clipped at
 * the layer bounds. `CompositingStrategy.Offscreen` (used by the edge
 * fade) always clips at its layer, so removing `clipToBounds` on the
 * LazyRow wasn't enough by itself — we also need this slack on top
 * and bottom inside the layer. 14dp comfortably covers 3% of a 225dp
 * portrait card scaling up.
 */
private val RailScaleHeadroom = 14.dp

/**
 * Hover dwell required before the spotlight opens for the focused
 * card. ANY focus change (incl. moving between cards while open)
 * closes the spotlight immediately and restarts this timer; only a
 * deliberate stop of [SPOTLIGHT_OPEN_DELAY_MS] re-opens it on the
 * new focused card.
 */
private const val SPOTLIGHT_OPEN_DELAY_MS = 800L

/**
 * Vertical scroll animation when a rail gains focus and snaps the
 * parent so its title sits at the top of the viewport. 350ms matches
 * the Hero crossfade tween so the page never feels like it's pulling
 * the rug.
 */
private const val SECTION_SNAP_ANIM_MS = 350

/**
 * A titled horizontal rail.
 *
 * Three pieces of state worth flagging:
 *   - [focusedIndex] is the card the LazyRow currently has focus on.
 *     Updates the instant a card calls onFocused. Drives the focus
 *     border + scale on compact cards and the rail-level "focus has
 *     left this rail" detection. Auto-scrolls so the focused card
 *     always sits at slot 0 (after the rail's leading content pad).
 *   - `spotlightTargetIndex` is a DEBOUNCED version of focusedIndex.
 *     It only catches up to focusedIndex once the user has dwelled
 *     on the same card for [SPOTLIGHT_OPEN_DELAY_MS]. Drives the
 *     slot widening + the spotlight overlay.
 *   - `sectionTopY` is the rail's y inside the parent vertical-scroll
 *     column. Captured via `onGloballyPositioned`; used to snap the
 *     parent so the rail's title lands at viewport top whenever any
 *     card here gains focus. That's what makes "estoy en Tendencias y
 *     solo veo Tendencias" work without the Hero peeking above.
 *
 * Two indices for the spotlight: fast D-pad sweeps shouldn't toggle
 * the spotlight on every card under the cursor — that's how you get
 * the glitches the user reported (overlay drifting away from the
 * focused card, posters stuck behind). Decoupling the immediate focus
 * signal from the spotlight commit means rapid nav reads as plain
 * compact-card navigation, and only deliberate stops promote a card
 * into the spotlight.
 *
 * The rail also reserves [sectionMinHeight] so each rail's section
 * fills the viewport. Combined with the snap on focus, this gives
 * the Netflix/AppleTV "one row per screen" vertical paging feel.
 *
 * Landscape rails (Continue Watching, Next Up) skip the spotlight
 * entirely — their 16:9 footprint already reads as a preview.
 */
@Composable
fun HomeRail(
    title:            String,
    items:            List<MediaItem>,
    onFocused:        (MediaItem) -> Unit,
    onClick:          (MediaItem) -> Unit,
    parentScroll:     ScrollState,
    sectionMinHeight: Dp,
    style:            CardStyle = CardStyle.Landscape,
    modifier:         Modifier  = Modifier,
) {
    if (items.isEmpty()) return

    val listState    = rememberLazyListState()
    val scope        = rememberCoroutineScope()
    val canSpotlight = style == CardStyle.Portrait

    var focusedIndex         by remember { mutableStateOf<Int?>(null) }
    var spotlightTargetIndex by remember { mutableStateOf<Int?>(null) }
    var sectionTopY          by remember { mutableFloatStateOf(0f) }

    // Close-on-navigate / open-on-dwell. The instant focusedIndex
    // changes we drop spotlightTargetIndex back to null (slot
    // collapses, overlay fades) and start a fresh timer. The new
    // focused card only "wins" the spotlight if the user actually
    // dwells on it for SPOTLIGHT_OPEN_DELAY_MS. Rapid D-pad sweeps
    // never see a spotlight at all.
    LaunchedEffect(focusedIndex, canSpotlight) {
        if (!canSpotlight) {
            spotlightTargetIndex = null
            return@LaunchedEffect
        }
        val current = focusedIndex
        spotlightTargetIndex = null
        if (current == null) return@LaunchedEffect
        delay(SPOTLIGHT_OPEN_DELAY_MS)
        if (focusedIndex == current) {
            spotlightTargetIndex = current
        }
    }

    // Auto-scroll: focused card always lands at slot 0 (just past the
    // rail's leading content padding). Uses animateScrollToItem so the
    // smooth-scroller re-targets each frame as slot widths animate —
    // an earlier animateScrollBy(precomputedDelta) over-shot whenever
    // the previous spotlight slot was still collapsing during the
    // scroll, leaving the focused card off-screen to the left.
    LaunchedEffect(focusedIndex) {
        val target = focusedIndex ?: return@LaunchedEffect
        listState.animateScrollToItem(index = target, scrollOffset = 0)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = sectionMinHeight)
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
        Text(
            text       = title,
            style      = MaterialTheme.typography.titleLarge,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(horizontal = RailContentPadding, vertical = 8.dp),
        )

        // BoxWithConstraints reads the viewport width so we can size
        // the trailing content pad large enough that the LAST item
        // can still scroll all the way to slot 0. Without it, focus
        // near the end of the list leaves the focused card stuck in
        // the middle/right of the rail and the spotlight overlay
        // (anchored at slot 0) ends up on a different card — the
        // "Ant-Man en el panel, Transformers como título" desync the
        // user spotted in screenshot 5.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val viewportWidth = maxWidth
            val trailingPad   = (viewportWidth - RailContentPadding - style.defaultWidth)
                .coerceAtLeast(RailContentPadding)

            LazyRow(
                state                 = listState,
                contentPadding        = PaddingValues(
                    start = RailContentPadding,
                    end   = trailingPad,
                ),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier              = Modifier
                    .fillMaxWidth()
                    .horizontalEdgeFade()
                    // Padding INSIDE the offscreen compositing layer
                    // gives the focused card's 1.06 scale somewhere
                    // to grow without being clipped by the layer
                    // bounds. The previous "se ve cortado de arriba"
                    // was the layer slicing the scaled-up card at
                    // its top and bottom edges.
                    .padding(vertical = RailScaleHeadroom)
                    .onFocusChanged { focusState ->
                        // hasFocus stays true while ANY descendant
                        // card is focused. Goes false the instant
                        // D-pad up/down moves focus to a different
                        // rail or to the Hero — that's our signal
                        // to clear focusedIndex which collapses any
                        // pending spotlight.
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
            // the immediate focus. Stays anchored to slot 0 (where
            // the focused card always lives thanks to the trailing
            // pad above). The top padding mirrors the LazyRow's
            // vertical headroom so the overlay aligns with the
            // cards' top edges.
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
                    // hidden behind the rail's own background.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = RailScaleHeadroom)
                            .width(RailContentPadding)
                            .height(SpotlightDims.height)
                            .alpha(spotlightAlpha)
                            .background(MaterialTheme.colorScheme.background),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = RailContentPadding,
                                top   = RailScaleHeadroom,
                            )
                            .alpha(spotlightAlpha),
                    ) {
                        // direction = 0 → pure fade inside
                        // RailSpotlight, no slide. Each open is a
                        // fresh reveal because we close on every
                        // nav and reopen on dwell.
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
 * HomeRail (snap-on-focus, trailing pad, scale headroom) but renders
 * [LiveChannelCard] instead of MediaCard so channels without a logo
 * get the initials placeholder, and the channel name lives under the
 * card. No spotlight — channels don't have an overview / rating / year
 * to populate one.
 */
@Composable
fun LiveNowRail(
    title:            String,
    items:            List<MediaItem>,
    onFocused:        (MediaItem) -> Unit,
    onClick:          (MediaItem) -> Unit,
    parentScroll:     ScrollState,
    sectionMinHeight: Dp,
    modifier:         Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val listState     = rememberLazyListState()
    val scope         = rememberCoroutineScope()
    var focusedIndex by remember { mutableStateOf<Int?>(null) }
    var sectionTopY  by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(focusedIndex) {
        val target = focusedIndex ?: return@LaunchedEffect
        listState.animateScrollToItem(index = target, scrollOffset = 0)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = sectionMinHeight)
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
        Text(
            text       = title,
            style      = MaterialTheme.typography.titleLarge,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(horizontal = RailContentPadding, vertical = 8.dp),
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val viewportWidth = maxWidth
            // LiveChannelCard width is hard-coded to 220.dp.
            val liveCardWidth = 220.dp
            val trailingPad   = (viewportWidth - RailContentPadding - liveCardWidth)
                .coerceAtLeast(RailContentPadding)

            LazyRow(
                state                 = listState,
                contentPadding        = PaddingValues(
                    start = RailContentPadding,
                    end   = trailingPad,
                ),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier              = Modifier
                    .fillMaxWidth()
                    .horizontalEdgeFade()
                    .padding(vertical = RailScaleHeadroom)
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
}

/**
 * Soft alpha fade on both horizontal ends of the rail. Uses
 * BlendMode.DstIn against an offscreen layer so the gradient actually
 * erases the rendered content's alpha (instead of painting a colour
 * on top, which would leave a visible band over the rail's
 * background). Standard "fading edge" recipe.
 *
 * The offscreen layer also clips at its bounds. Callers must pair this
 * modifier with [RailScaleHeadroom] vertical padding so the focused
 * card's 1.06 scale doesn't get sliced.
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
