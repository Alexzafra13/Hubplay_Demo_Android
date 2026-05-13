package com.alex.hubplay.ui.home.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alex.hubplay.data.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RailContentPadding = 24.dp

/**
 * Hover dwell required before the spotlight expands. Long enough that
 * a fast D-pad scan reads as plain navigation, short enough that a
 * deliberate stop feels snappy.
 */
private const val SPOTLIGHT_OPEN_DELAY_MS = 800L

/**
 * A titled horizontal rail.
 *
 * Compact cards always; the focused card's slot widens to
 * [SpotlightDims.totalWidth] when the spotlight is open, pushing the
 * next poster to the right (never covering it). The spotlight overlay
 * lives at viewport position [RailContentPadding] and stays mounted
 * while focus is anywhere inside the rail — only its content slides
 * (left/right depending on D-pad direction). No collapse-and-re-expand
 * jitter.
 *
 * Three animations run in lockstep on every focus change:
 *   1. Old focused slot width: 475 → 150 (tween SPOTLIGHT_ANIM_MS)
 *   2. New focused slot width: 150 → 475 (tween SPOTLIGHT_ANIM_MS)
 *   3. Auto-scroll by precise delta so the new focused's left edge
 *      lands at viewport position RailContentPadding.
 *
 * Landscape rails (Continue Watching, Next Up) skip the spotlight
 * — their 16:9 stills already read as a preview.
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

    var focusedIndex   by remember { mutableStateOf<Int?>(null) }
    var lastFocusedIdx by remember { mutableIntStateOf(-1) }
    var spotlightOpen  by remember { mutableStateOf(false) }
    var slideDirection by remember { mutableIntStateOf(0) }

    // Hover delay: first focus inside the rail waits the open delay
    // before showing the spotlight. Once open, focus changes within
    // the rail keep it mounted (only its content slides). Focus
    // leaving the rail closes it via the LazyRow's onFocusChanged.
    LaunchedEffect(focusedIndex, canSpotlight) {
        if (!canSpotlight) {
            spotlightOpen = false
            return@LaunchedEffect
        }
        when {
            focusedIndex == null -> spotlightOpen = false
            !spotlightOpen       -> {
                delay(SPOTLIGHT_OPEN_DELAY_MS)
                if (focusedIndex != null) spotlightOpen = true
            }
        }
    }

    // Auto-scroll on every focus change while the spotlight is open.
    // We compute the delta from the focused card's CURRENT visible
    // offset to the rail's viewport start so the wider slot lines up
    // perfectly with the spotlight overlay above it. animateScrollBy
    // (not animateScrollToItem, which no-ops when the item is already
    // "visible enough" — exactly the case here, since the focused card
    // sits under the spotlight).
    LaunchedEffect(focusedIndex, spotlightOpen) {
        val target = focusedIndex
        if (!spotlightOpen || target == null) return@LaunchedEffect
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

        // Box wraps the LazyRow + spotlight overlay. clipToBounds on
        // the LazyRow stops mid-animation slivers of the previously-
        // focused card from poking past the rail's left edge.
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                state                 = listState,
                contentPadding        = PaddingValues(horizontal = RailContentPadding),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier              = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .onFocusChanged { focusState ->
                        // hasFocus stays true while ANY descendant card
                        // is focused. Goes false the instant D-pad
                        // up/down moves to a different rail. That's
                        // the signal to close the spotlight and reset
                        // direction tracking so the next visit starts
                        // with a clean fade-in.
                        if (!focusState.hasFocus) {
                            focusedIndex   = null
                            lastFocusedIdx = -1
                        }
                    },
            ) {
                itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                    val isActiveSlot = canSpotlight && spotlightOpen && index == focusedIndex
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
                            slideDirection = when {
                                lastFocusedIdx < 0     -> 0
                                index > lastFocusedIdx -> 1
                                index < lastFocusedIdx -> -1
                                else                   -> 0
                            }
                            lastFocusedIdx = index
                            focusedIndex   = index
                            onFocused(focusedItem)
                        },
                        onClick     = onClick,
                    )
                }
            }

            // Spotlight overlay at viewport position RailContentPadding
            // — exactly where auto-scroll keeps the focused card's
            // left edge. The wider slot reserves the space underneath
            // so this overlay never covers any unfocused card.
            if (canSpotlight) {
                val spotlightAlpha by animateFloatAsState(
                    targetValue   = if (spotlightOpen && focusedIndex != null) 1f else 0f,
                    animationSpec = tween(SPOTLIGHT_ANIM_MS),
                    label         = "spotlight-alpha",
                )
                val current = focusedIndex?.let { items.getOrNull(it) }
                if (spotlightAlpha > 0.01f && current != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = RailContentPadding)
                            .alpha(spotlightAlpha),
                    ) {
                        RailSpotlight(
                            state = SpotlightState(
                                item      = current,
                                direction = slideDirection,
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
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                LiveChannelCard(
                    item      = item,
                    onFocused = { focusedItem ->
                        onFocused(focusedItem)
                        scope.launch {
                            scrollFocusedToStart(scope, listState, index)
                        }
                    },
                    onClick   = onClick,
                )
            }
        }
    }
}

/**
 * Animate scroll so [index]'s left edge lands at the rail's leading
 * content padding. Uses animateScrollBy with a precise delta because
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
        val info       = listState.layoutInfo
        val target     = info.visibleItemsInfo.firstOrNull { it.index == index }
        val viewportPx = info.viewportStartOffset

        if (target != null) {
            val delta = (target.offset - viewportPx).toFloat()
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
