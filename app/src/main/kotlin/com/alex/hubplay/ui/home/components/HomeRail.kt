package com.alex.hubplay.ui.home.components

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alex.hubplay.data.MediaItem

private val RailContentPadding = 24.dp
private val RailScaleHeadroom = 12.dp

@Composable
fun HomeRail(
    title:     String,
    items:     List<MediaItem>,
    onFocused: (MediaItem) -> Unit,
    onClick:   (MediaItem) -> Unit,
    style:     CardStyle = CardStyle.Landscape,
    modifier:  Modifier  = Modifier,
) {
    if (items.isEmpty()) return

    val listState = rememberLazyListState()
    var focusedIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(focusedIndex) {
        val target = focusedIndex ?: return@LaunchedEffect
        listState.scrollToItem(index = target, scrollOffset = 0)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text       = title,
            style      = MaterialTheme.typography.titleLarge,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(
                start = RailContentPadding,
                end = RailContentPadding,
                top = 16.dp,
                bottom = 10.dp,
            ),
        )

        LazyRow(
            state                 = listState,
            contentPadding        = PaddingValues(horizontal = RailContentPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier              = Modifier
                .fillMaxWidth()
                .padding(vertical = RailScaleHeadroom)
                .onFocusChanged { focusState ->
                    if (!focusState.hasFocus) focusedIndex = null
                },
        ) {
            items(
                count = items.size,
                key   = { index -> index },
            ) { index ->
                val item = items[index]
                MediaCard(
                    item      = item,
                    style     = style,
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
    var focusedIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(focusedIndex) {
        val target = focusedIndex ?: return@LaunchedEffect
        listState.scrollToItem(index = target, scrollOffset = 0)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text       = title,
            style      = MaterialTheme.typography.titleLarge,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(
                start = RailContentPadding,
                end = RailContentPadding,
                top = 16.dp,
                bottom = 10.dp,
            ),
        )

        LazyRow(
            state                 = listState,
            contentPadding        = PaddingValues(horizontal = RailContentPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier              = Modifier
                .fillMaxWidth()
                .padding(vertical = RailScaleHeadroom)
                .onFocusChanged { focusState ->
                    if (!focusState.hasFocus) focusedIndex = null
                },
        ) {
            items(
                count = items.size,
                key   = { index -> index },
            ) { index ->
                val item = items[index]
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
