package com.alex.hubplay.ui.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alex.hubplay.data.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RailContentPadding = 24.dp

/**
 * Delay before the spotlight overlay opens after first focus inside
 * the rail. Short tap-throughs (user just navigating past) don't
 * trigger it; only a deliberate pause does. Once open, the spotlight
 * stays mounted and only its content swaps — that is the whole point
 * of [RailSpotlight] living at the rail level.
 */
private const val SPOTLIGHT_OPEN_DELAY_MS = 600L

/**
 * A titled horizontal rail. Renders nothing when empty (the Plex
 * pattern — empty rails feel like broken UI, the right thing is to
 * not render them at all).
 *
 *   - Portrait rails get the Netflix-style spotlight overlay: a single
 *     persistent expanded preview at a fixed position, content slides
 *     left/right when the user navigates between cards. Underlying
 *     compact poster of the focused card is rendered invisible so the
 *     spotlight backdrop sits cleanly on top.
 *   - Landscape rails (Continue Watching, Next Up) keep the simple
 *     compact-card-with-focus-border behaviour — their 16:9 footprint
 *     already reads as a "preview" by itself.
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

    // First focus inside the rail waits SPOTLIGHT_OPEN_DELAY_MS before
    // opening the spotlight, so a quick swipe-through doesn't trigger
    // it. Once open, focus changes don't reset the timer — only focus
    // leaving the rail closes it.
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

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text       = title,
            style      = MaterialTheme.typography.titleLarge,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(horizontal = RailContentPadding, vertical = 8.dp),
        )

        // Box wraps the LazyRow + spotlight overlay so the overlay
        // sits on top at a fixed x = RailContentPadding. The row gives
        // the Box its height implicitly; on portrait rails we add the
        // title-strip height (handled by MediaCard's column) so
        // overlaying doesn't crop the title underneath.
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                state                 = listState,
                contentPadding        = PaddingValues(horizontal = RailContentPadding),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier              = Modifier.onFocusChanged { focusState ->
                    // hasFocus stays true while ANY descendant card is
                    // focused. Goes false the instant the user dpads
                    // up/down to another rail or to the top nav. That
                    // is the signal to close the spotlight and reset
                    // direction tracking so the next visit starts
                    // with a clean fade-in instead of a leftover slide.
                    if (!focusState.hasFocus) {
                        focusedIndex   = null
                        lastFocusedIdx = -1
                    }
                },
            ) {
                itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                    MediaCard(
                        item             = item,
                        style            = style,
                        hideForSpotlight = canSpotlight && spotlightOpen && index == focusedIndex,
                        onFocused        = { focusedItem ->
                            // Direction of the slide — derived BEFORE we
                            // overwrite focusedIndex so we can compare
                            // old vs new.
                            slideDirection = when {
                                lastFocusedIdx < 0     -> 0
                                index > lastFocusedIdx -> 1
                                index < lastFocusedIdx -> -1
                                else                   -> 0
                            }
                            lastFocusedIdx = index
                            focusedIndex   = index
                            onFocused(focusedItem)
                            scrollFocusedToStart(scope, listState, index)
                        },
                        onClick          = onClick,
                    )
                }
            }

            // Manual alpha drive instead of AnimatedVisibility to
            // sidestep the BoxScope/ColumnScope receiver ambiguity that
            // makes the compiler pick ColumnScope.AnimatedVisibility
            // (whose generated content lambda then can't see the
            // surrounding @Composable). Same fade visual, simpler call
            // site.
            val spotlightAlpha by animateFloatAsState(
                targetValue   = if (canSpotlight && spotlightOpen && focusedIndex != null) 1f else 0f,
                animationSpec = tween(SPOTLIGHT_ANIM_MS),
                label         = "spotlight-alpha",
            )
            val current = focusedIndex?.let { items.getOrNull(it) }
            if (canSpotlight && spotlightAlpha > 0.01f && current != null) {
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

            // Reserve title-strip space below the spotlight on portrait
            // rails so the rail's vertical footprint matches a card's
            // total height (poster + title + subtitle). MediaCard
            // already renders that strip; this Spacer is just a safety
            // net for short rails where the spotlight could otherwise
            // overflow downward into the next rail.
            if (canSpotlight) {
                Box(modifier = Modifier
                    .height(SpotlightDims.rowHeight + 56.dp))
            }
        }
    }
}

/**
 * Specialised rail for the "En directo ahora" section. Same shell as
 * HomeRail but renders [LiveChannelCard] instead of MediaCard so that
 * channels without a logo get the initials placeholder, and the
 * channel name lives under the card (the only thing that tells the
 * user which channel they're hovering over). No spotlight — channels
 * don't have an overview / rating / year to populate one.
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
                        scrollFocusedToStart(scope, listState, index)
                    },
                    onClick   = onClick,
                )
            }
        }
    }
}

/**
 * Animated scroll so that [index]'s left edge lands exactly at
 * `contentPadding.start`. Cannot use `animateScrollToItem` because
 * Compose treats it as a no-op when the item is already visible —
 * which it always is, given the cards under the spotlight are still
 * "visible" from the LazyList's perspective. Compute the delta from
 * current visible offset to target offset and animate that instead.
 *
 * Falls back to non-animated `scrollToItem` when the target is
 * off-screen (e.g. just after a config change repopulates the rail).
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
