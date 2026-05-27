package com.alex.hubplay.ui.home.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alex.hubplay.data.Content

private val RailContentPadding = 24.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun <T : Content> HomeRail(
    title:                String,
    items:                List<T>,
    onFocused:            (T) -> Unit,
    onClick:              (T) -> Unit,
    style:                CardStyle = CardStyle.Landscape,
    modifier:             Modifier  = Modifier,
    initialFocusedItemId: String?   = null,
    railFocusRequester:   FocusRequester? = null,
) {
    BaseRail(
        title = title,
        items = items,
        onFocused = onFocused,
        modifier = modifier,
        initialFocusedItemId = initialFocusedItemId,
        railFocusRequester = railFocusRequester,
    ) { item, onItemFocused, mod ->
        // MediaCard's lambdas widen to `Content` because the card itself is
        // not generic — we narrow back through wrapper lambdas so the
        // caller's `onClick: (T) -> Unit` (e.g. Continue-Watching gets
        // Content.Resumable) stays type-safe.
        MediaCard(
            item      = item,
            style     = style,
            onFocused = { onItemFocused(item) },
            onClick   = { onClick(item) },
            modifier  = mod,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LiveNowRail(
    title:                String,
    items:                List<Content.LiveChannel>,
    onFocused:            (Content.LiveChannel) -> Unit,
    onClick:              (Content.LiveChannel) -> Unit,
    modifier:             Modifier = Modifier,
    initialFocusedItemId: String?  = null,
    railFocusRequester:   FocusRequester? = null,
) {
    BaseRail(
        title = title,
        items = items,
        onFocused = onFocused,
        modifier = modifier,
        initialFocusedItemId = initialFocusedItemId,
        railFocusRequester = railFocusRequester,
    ) { item, onItemFocused, mod ->
        LiveChannelCard(
            item      = item,
            onFocused = { onItemFocused(item) },
            onClick   = { onClick(item) },
            modifier  = mod,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun <T : Content> BaseRail(
    title:                String,
    items:                List<T>,
    onFocused:            (T) -> Unit,
    modifier:             Modifier = Modifier,
    initialFocusedItemId: String?  = null,
    railFocusRequester:   FocusRequester? = null,
    card: @Composable (item: T, onItemFocused: (T) -> Unit, modifier: Modifier) -> Unit,
) {
    if (items.isEmpty()) return

    val restoreRequester = remember { FocusRequester() }
    val restoreIndex = remember(initialFocusedItemId, items) {
        if (initialFocusedItemId != null) items.indexOfFirst { it.id == initialFocusedItemId } else -1
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = restoreIndex.coerceAtLeast(0),
    )
    var focusedIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(focusedIndex) {
        if (focusedIndex >= 0) {
            listState.scrollToItem(index = focusedIndex, scrollOffset = 0)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text       = title,
            style      = MaterialTheme.typography.titleLarge,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(
                start = RailContentPadding,
                end = RailContentPadding,
                top = 12.dp,
                bottom = 10.dp,
            ),
        )

        LazyRow(
            state                 = listState,
            contentPadding        = PaddingValues(horizontal = RailContentPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier              = Modifier.fillMaxWidth()
                .let { m -> if (railFocusRequester != null) m.focusRequester(railFocusRequester) else m }
                .focusGroup()
                .focusRestorer { if (restoreIndex >= 0) restoreRequester else FocusRequester.Default },
        ) {
            items(
                count = items.size,
                key   = { index -> items[index].id },
            ) { index ->
                val item = items[index]
                val attachRequester = index == restoreIndex
                card(
                    item,
                    { focusedItem ->
                        focusedIndex = index
                        onFocused(focusedItem)
                    },
                    if (attachRequester) Modifier.focusRequester(restoreRequester) else Modifier,
                )
            }
        }
    }
}
