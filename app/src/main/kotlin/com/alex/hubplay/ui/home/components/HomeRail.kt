package com.alex.hubplay.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
            contentPadding        = PaddingValues(horizontal = RailContentPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier              = Modifier
                .fillMaxWidth()
                .padding(vertical = RailScaleHeadroom),
        ) {
            items(
                count = items.size,
                key   = { index -> index },
            ) { index ->
                val item = items[index]
                MediaCard(
                    item      = item,
                    style     = style,
                    onFocused = { onFocused(it) },
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
            contentPadding        = PaddingValues(horizontal = RailContentPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier              = Modifier
                .fillMaxWidth()
                .padding(vertical = RailScaleHeadroom),
        ) {
            items(
                count = items.size,
                key   = { index -> index },
            ) { index ->
                val item = items[index]
                LiveChannelCard(
                    item      = item,
                    onFocused = { onFocused(it) },
                    onClick   = onClick,
                )
            }
        }
    }
}
