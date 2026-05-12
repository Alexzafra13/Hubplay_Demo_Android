package com.alex.hubplay.ui.home.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alex.hubplay.data.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A titled horizontal rail. Renders nothing when empty (the Plex
 * pattern — empty rails feel like broken UI, the right thing is to
 * not render them at all).
 *
 * `style` decides the card aspect: pass `Portrait` for movie/series
 * caratulas, `Landscape` for episode stills and channel logos.
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
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text       = title,
            style      = MaterialTheme.typography.titleLarge,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyRow(
            state                 = listState,
            contentPadding        = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                MediaCard(
                    item      = item,
                    style     = style,
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
 * Specialised rail for the "En directo ahora" section. Same shell as
 * HomeRail but renders [LiveChannelCard] instead of MediaCard so that
 * channels without a logo get the initials placeholder, and the
 * channel name lives under the card (the only thing that tells the
 * user which channel they're hovering over).
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
            modifier   = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyRow(
            state                 = listState,
            contentPadding        = PaddingValues(horizontal = 24.dp),
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
 * Snap the LazyRow so that [index] is at the very start of the
 * viewport. Uses non-animated `scrollToItem` rather than
 * `animateScrollToItem` because Compose treats animated scroll as a
 * no-op when the item is already "visible enough", which led to the
 * focused card drifting toward the middle as the user navigated
 * sideways. The previous card has already collapsed (tween 0ms in
 * MediaCard), so a snap scroll just slides it off-screen instantly
 * — no jarring retraction visible.
 */
private fun scrollFocusedToStart(
    scope:     CoroutineScope,
    listState: LazyListState,
    index:     Int,
) {
    scope.launch {
        listState.scrollToItem(index = index, scrollOffset = 0)
    }
}
