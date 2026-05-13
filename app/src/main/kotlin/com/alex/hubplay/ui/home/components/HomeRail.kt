package com.alex.hubplay.ui.home.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alex.hubplay.data.MediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RailContentPadding = 24.dp

/**
 * Width of the soft alpha fade applied to both ends of every rail.
 * Cards leaving the viewport (because the focused one was auto-
 * scrolled to the start) feather into transparency instead of being
 * sliced sharply at the rail's edge — same polish trick Plex / Apple
 * TV / Disney+ use.
 */
private val EdgeFadeWidth = 48.dp

/**
 * Vertical headroom inside the rail's offscreen compositing layer so
 * the focused card's 1.06 scale animation doesn't get clipped at the
 * layer bounds. `CompositingStrategy.Offscreen` (used by the edge
 * fade) always clips at its own bounds — this padding gives the
 * scale-up somewhere to grow.
 */
private val RailScaleHeadroom = 14.dp

/**
 * Peek the previous section keeps when the focused rail snaps into
 * place. Instead of putting the rail title flush at the viewport
 * top (which leaves a wide black band above when the previous
 * section is shorter than the viewport), we land the title 60dp
 * below the top so the user sees a faded slice of the prior content
 * — the "transición elegante entre rails, no negro" the user asked
 * for.
 */
private val RailPeekOffset = 60.dp

/**
 * Hover dwell required before the spotlight opens for the focused
 * card. ANY focus change closes the spotlight immediately and
 * restarts this timer.
 */
private const val SPOTLIGHT_OPEN_DELAY_MS = 800L

/**
 * Vertical scroll animation when a rail gains focus and snaps the
 * parent so the rail's title lands `RailPeekOffset` below the
 * viewport top. 350ms matches HeroSection's snap.
 */
private const val SECTION_SNAP_ANIM_MS = 350

/**
 * Replica factor for the cyclic LazyRow. Items are repeated this
 * many times so D-pad past the last item lands on the FIRST item
 * again (= the next replica's slot 0) without any visual jump or
 * focus reset — Compose just navigates to the next focusable in the
 * row. The user starts at index `items.size * (REPEATS / 2)` so
 * they can also navigate LEFT past item 0 for tens of thousands of
 * presses before exhausting the replicas. 200 replicas keeps total
 * content-width well under Float precision limits even on a long
 * 100-item rail.
 */
private const val CYCLE_REPEATS = 200

/**
 * A titled horizontal rail with cyclic navigation + section-snap.
 *
 * Three state pieces worth flagging:
 *   - [focusedIndex] is the card the LazyRow currently has focus on
 *     (in *cycle-index* space, not the underlying items list).
 *     Drives the focus border + scale on compact cards, the
 *     auto-scroll that keeps the focused card at slot 0, and the
 *     rail-level "focus has left this rail" detection.
 *   - `spotlightTargetIndex` is a DEBOUNCED version of focusedIndex.
 *     Promotes a card into the spotlight only after the user
 *     dwells on it for [SPOTLIGHT_OPEN_DELAY_MS]. Rapid D-pad
 *     sweeps never trigger a spotlight at all.
 *   - `sectionTopY` is the rail's y inside the parent vertical-
 *     scroll column. Captured via `onGloballyPositioned`; used to
 *     animate the parent scroll on focus enter so the rail sits
 *     `RailPeekOffset` below the viewport top — previous content
 *     fades in above, next rail peeks below.
 *
 * Cyclic LazyRow: items get replicated [CYCLE_REPEATS] times. From
 * the focus system's POV there are 200 × items.size slots; D-pad
 * Right past the last "real" item lands on the next replica's slot
 * 0, which IS item 0 again — no jump, no focus reset, no end-of-list
 * dead-end. The user notices nothing because Coil caches images by
 * URL so revisited posters paint instantly.
 *
 * Landscape rails (Continue Watching, Next Up) skip the spotlight —
 * their 16:9 footprint is already preview-shaped.
 */
@Composable
fun HomeRail(
    title:        String,
    items:        List<MediaItem>,
    onFocused:    (MediaItem) -> Unit,
    onClick:      (MediaItem) -> Unit,
    parentScroll: ScrollState,
    style:        CardStyle = CardStyle.Landscape,
    modifier:     Modifier  = Modifier,
) {
    if (items.isEmpty()) return

    val shouldCycle      = items.size > 1
    val cycleSize        = if (shouldCycle) items.size * CYCLE_REPEATS else items.size
    val startCycleIndex  = if (shouldCycle) items.size * (CYCLE_REPEATS / 2) else 0

    val listState    = rememberLazyListState()
    val scope        = rememberCoroutineScope()
    val canSpotlight = style == CardStyle.Portrait
    val density      = LocalDensity.current
    val peekPx       = with(density) { RailPeekOffset.toPx() }

    var focusedIndex         by remember { mutableStateOf<Int?>(null) }
    var spotlightTargetIndex by remember { mutableStateOf<Int?>(null) }
    var sectionTopY          by remember { mutableFloatStateOf(0f) }

    // Anchor the user mid-cycle so D-pad LEFT past item 0 wraps to
    // the previous replica's last item without hitting cycleIndex
    // = -1. Only fires once on first composition (gated by
    // listState's resting position at 0).
    LaunchedEffect(shouldCycle, items.size) {
        if (shouldCycle && listState.firstVisibleItemIndex == 0) {
            listState.scrollToItem(startCycleIndex)
        }
    }

    // Close-on-navigate / open-on-dwell.
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

    // Focused cycle-index always re-aligns to slot 0. Uses
    // animateScrollToItem so the smooth-scroller re-targets each
    // frame as slot widths animate — the previous animateScrollBy-
    // with-precomputed-delta over-shot while the prior spotlight
    // slot was still collapsing.
    LaunchedEffect(focusedIndex) {
        val target = focusedIndex ?: return@LaunchedEffect
        listState.animateScrollToItem(index = target, scrollOffset = 0)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                sectionTopY = coords.positionInParent().y
            }
            .onFocusChanged { state ->
                if (state.hasFocus) {
                    scope.launch {
                        parentScroll.animateScrollTo(
                            value         = (sectionTopY - peekPx).toInt().coerceAtLeast(0),
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

        Box(modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                state                 = listState,
                contentPadding        = PaddingValues(horizontal = RailContentPadding),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier              = Modifier
                    .fillMaxWidth()
                    .horizontalEdgeFade()
                    // Headroom INSIDE the offscreen compositing
                    // layer so the focused card's 1.06 scale isn't
                    // clipped at the layer bounds.
                    .padding(vertical = RailScaleHeadroom)
                    .onFocusChanged { focusState ->
                        if (!focusState.hasFocus) focusedIndex = null
                    },
            ) {
                items(
                    count = cycleSize,
                    // Cycle-index as key (not item.id) — the same
                    // item appears CYCLE_REPEATS times and Compose
                    // requires unique keys per slot.
                    key   = { cycleIndex -> cycleIndex },
                ) { cycleIndex ->
                    val realIndex = if (shouldCycle) cycleIndex.mod(items.size) else cycleIndex
                    val item = items[realIndex]
                    val isActiveSlot = canSpotlight && cycleIndex == spotlightTargetIndex
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
                            focusedIndex = cycleIndex
                            onFocused(focusedItem)
                        },
                        onClick     = onClick,
                    )
                }
            }

            // Spotlight overlay — anchored at slot 0 (the leading
            // edge after content padding). The focused card always
            // lives here thanks to the auto-scroll above + the
            // cyclic LazyRow (no end-of-list dead zone). Driven by
            // the DEBOUNCED target so rapid D-pad sweeps don't open
            // it.
            if (canSpotlight) {
                val spotlightAlpha by animateFloatAsState(
                    targetValue   = if (spotlightTargetIndex != null) 1f else 0f,
                    animationSpec = tween(SPOTLIGHT_ANIM_MS),
                    label         = "spotlight-alpha",
                )
                val current = spotlightTargetIndex?.let {
                    items.getOrNull(if (shouldCycle) it.mod(items.size) else it)
                }
                if (spotlightAlpha > 0.01f && current != null) {
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
 * Specialised rail for the "En directo ahora" section. Shares the
 * cyclic + snap-on-focus pattern with [HomeRail] but renders
 * [LiveChannelCard] (logo/initials placeholder, channel name under
 * the card). No spotlight — channels don't have an overview / rating
 * to populate one.
 */
@Composable
fun LiveNowRail(
    title:        String,
    items:        List<MediaItem>,
    onFocused:    (MediaItem) -> Unit,
    onClick:      (MediaItem) -> Unit,
    parentScroll: ScrollState,
    modifier:     Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val shouldCycle     = items.size > 1
    val cycleSize       = if (shouldCycle) items.size * CYCLE_REPEATS else items.size
    val startCycleIndex = if (shouldCycle) items.size * (CYCLE_REPEATS / 2) else 0

    val listState  = rememberLazyListState()
    val scope      = rememberCoroutineScope()
    val density    = LocalDensity.current
    val peekPx     = with(density) { RailPeekOffset.toPx() }

    var focusedIndex by remember { mutableStateOf<Int?>(null) }
    var sectionTopY  by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(shouldCycle, items.size) {
        if (shouldCycle && listState.firstVisibleItemIndex == 0) {
            listState.scrollToItem(startCycleIndex)
        }
    }

    LaunchedEffect(focusedIndex) {
        val target = focusedIndex ?: return@LaunchedEffect
        listState.animateScrollToItem(index = target, scrollOffset = 0)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                sectionTopY = coords.positionInParent().y
            }
            .onFocusChanged { state ->
                if (state.hasFocus) {
                    scope.launch {
                        parentScroll.animateScrollTo(
                            value         = (sectionTopY - peekPx).toInt().coerceAtLeast(0),
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

        LazyRow(
            state                 = listState,
            contentPadding        = PaddingValues(horizontal = RailContentPadding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier              = Modifier
                .fillMaxWidth()
                .horizontalEdgeFade()
                .padding(vertical = RailScaleHeadroom)
                .onFocusChanged { focusState ->
                    if (!focusState.hasFocus) focusedIndex = null
                },
        ) {
            items(
                count = cycleSize,
                key   = { cycleIndex -> cycleIndex },
            ) { cycleIndex ->
                val realIndex = if (shouldCycle) cycleIndex.mod(items.size) else cycleIndex
                val item = items[realIndex]
                LiveChannelCard(
                    item      = item,
                    onFocused = { focusedItem ->
                        focusedIndex = cycleIndex
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
 *
 * The offscreen layer also clips at its bounds — callers must pair
 * this with [RailScaleHeadroom] vertical padding so the focused
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
