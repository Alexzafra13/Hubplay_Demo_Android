package com.alex.hubplay.ui.collections

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alex.hubplay.R
import com.alex.hubplay.data.CollectionSummary
import com.alex.hubplay.ui.home.components.Tab
import com.alex.hubplay.ui.home.components.TopNav
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.BgBase

/**
 * Collections tab — grid of TMDb sagas matched against the user's movie
 * libraries (Marvel Cinematic Universe, Lord of the Rings, etc.). Same
 * shell as Movies/Series but the tile shows an item-count pill in the
 * corner instead of just a poster, mirroring the web client's
 * Collections page.
 */
@Composable
fun CollectionsScreen(
    viewModel:     CollectionsViewModel,
    onOpen:        (collectionId: String) -> Unit,
    onTabSelected: (Tab) -> Unit,
    onLogOut:      () -> Unit,
    onSettings:    () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = BgBase) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopNav(
                selectedTab   = Tab.Collections,
                onTabSelected = onTabSelected,
                onLogOut      = onLogOut,
                onSettings    = onSettings,
            )
            Text(
                text       = stringResource(R.string.collections_title),
                style      = MaterialTheme.typography.headlineMedium,
                color      = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
            )
            when {
                ui.isLoading && ui.entries.isEmpty() -> CenteredSpinner()
                ui.error != null && ui.entries.isEmpty() -> ErrorBanner(
                    message = ui.error!!,
                    onRetry = viewModel::load,
                )
                ui.entries.isEmpty() -> EmptyBanner()
                else -> LazyVerticalGrid(
                    columns               = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding        = PaddingValues(start = 32.dp, end = 32.dp, top = 12.dp, bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement   = Arrangement.spacedBy(14.dp),
                    modifier              = Modifier.fillMaxSize(),
                ) {
                    items(ui.entries, key = { it.id }) { entry ->
                        CollectionTile(entry = entry, onClick = { onOpen(entry.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionTile(entry: CollectionSummary, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (focused) 1.05f else 1f,
        animationSpec = tween(180),
        label         = "coll-tile-scale",
    )
    Column(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(12.dp)) else Modifier),
        ) {
            if (!entry.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model              = entry.posterUrl,
                    contentDescription = entry.name,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            } else {
                // No poster from TMDb yet — fall back to a big initial
                // letter on the surface tint so the tile isn't empty.
                Text(
                    text       = entry.name.firstOrNull()?.uppercase() ?: "?",
                    style      = MaterialTheme.typography.displayMedium,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.align(Alignment.Center),
                )
            }
            // Item-count badge, top-right. Reads as "this saga has N
            // movies in your catalogue" — same idea as the web tile.
            ItemCountBadge(
                count    = entry.itemCount,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text       = entry.name,
            style      = MaterialTheme.typography.bodyMedium,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ItemCountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.68f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text       = count.toString(),
            style      = MaterialTheme.typography.labelSmall,
            color      = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Column(
        modifier              = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        Text(
            text      = message,
            style     = MaterialTheme.typography.bodyLarge,
            color     = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun EmptyBanner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text  = stringResource(R.string.collections_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

