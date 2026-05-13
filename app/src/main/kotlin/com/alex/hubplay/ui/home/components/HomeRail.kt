package com.alex.hubplay.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alex.hubplay.data.MediaItem
import kotlinx.coroutines.delay

private val RailContentPadding = 24.dp

/**
 * Hover dwell before the in-rail spotlight panel appears below the
 * row. Long enough that a fast D-pad sweep reads as plain navigation;
 * short enough that a deliberate stop feels snappy.
 */
private const val SPOTLIGHT_OPEN_DELAY_MS = 800L

/**
 * A titled horizontal rail.
 *
 *   - Renders nothing when items is empty (Plex pattern).
 *   - Compact cards always — the row is never covered.
 *   - Portrait rails grow a spotlight panel UNDER the row when the
 *     user dwells on a card for [SPOTLIGHT_OPEN_DELAY_MS]. The panel
 *     pushes the next rail down when it appears, slides its content
 *     when the user navigates between cards in the same rail, and
 *     retracts when focus leaves the rail entirely.
 *   - Landscape rails (Continue Watching, Next Up) skip the spotlight
 *     — their 16:9 stills already read as a preview.
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
    val canSpotlight = style == CardStyle.Portrait

    var focusedIndex   by remember { mutableStateOf<Int?>(null) }
    var lastFocusedIdx by remember { mutableIntStateOf(-1) }
    var spotlightOpen  by remember { mutableStateOf(false) }
    var slideDirection by remember { mutableIntStateOf(0) }

    // First focus inside the rail waits the open delay before showing
    // the panel; once open, focus changes within the rail keep it
    // mounted (only its content swaps). Focus leaving the rail closes
    // it via the onFocusChanged on the LazyRow below.
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

        LazyRow(
            state                 = listState,
            contentPadding        = PaddingValues(horizontal = RailContentPadding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier              = Modifier.onFocusChanged { focusState ->
                // hasFocus stays true while ANY descendant card is
                // focused; goes false when D-pad up/down moves to a
                // different rail or the top nav. That's the signal to
                // close the spotlight and reset direction tracking so
                // the next visit starts with a clean fade-in instead
                // of a leftover slide.
                if (!focusState.hasFocus) {
                    focusedIndex   = null
                    lastFocusedIdx = -1
                }
            },
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                MediaCard(
                    item      = item,
                    style     = style,
                    onFocused = { focusedItem ->
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
                    onClick   = onClick,
                )
            }
        }

        if (canSpotlight) {
            // Spotlight slot lives BELOW the row so the row itself is
            // never covered. AnimatedVisibility makes the slot
            // collapse to 0 height when there's nothing to show, so
            // rails without a focused card sit flush with their cards.
            AnimatedVisibility(
                visible = spotlightOpen && focusedIndex != null,
                enter   = expandVertically(tween(SPOTLIGHT_ANIM_MS)) +
                          fadeIn(tween(SPOTLIGHT_ANIM_MS)),
                exit    = shrinkVertically(tween(SPOTLIGHT_ANIM_MS)) +
                          fadeOut(tween(SPOTLIGHT_ANIM_MS)),
            ) {
                val current = focusedIndex?.let { items.getOrNull(it) }
                if (current != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = RailContentPadding, top = 12.dp),
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
            itemsIndexed(items, key = { _, it -> it.id }) { _, item ->
                LiveChannelCard(
                    item      = item,
                    onFocused = onFocused,
                    onClick   = onClick,
                )
            }
        }
    }
}
