package com.alex.hubplay.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alex.hubplay.data.MediaItem

/**
 * A titled horizontal rail. Renders nothing when empty (the Plex
 * pattern — empty rails feel like broken UI, the right thing is to
 * not render them at all).
 */
@Composable
fun HomeRail(
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
            modifier   = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding        = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(items, key = { it.id }) { item ->
                MediaCard(
                    item      = item,
                    onFocused = onFocused,
                    onClick   = onClick,
                )
            }
        }
    }
}
