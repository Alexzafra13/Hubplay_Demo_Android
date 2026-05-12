package com.alex.hubplay.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alex.hubplay.data.MediaItem
import com.alex.hubplay.data.MediaKind
import com.alex.hubplay.ui.home.components.CardStyle
import com.alex.hubplay.ui.home.components.MediaCard
import com.alex.hubplay.ui.home.components.Tab
import com.alex.hubplay.ui.home.components.TopNav
import com.alex.hubplay.ui.theme.BgBase

/**
 * Shared shell for the Películas / Series / TV en vivo screens.
 *
 * Each tab in the TopNav resolves to a CatalogScreen flavoured by:
 *   - The active [Tab] (so the TopNav highlights the right entry).
 *   - The [items] to render in the grid.
 *   - The card factory ([cardContent]) so movies/series get
 *     MediaCard with Portrait posters and channels get
 *     LiveChannelCard with their initials placeholder.
 *
 * The TopNav lives inside the screen rather than in a parent shell so
 * each route owns its own scaffolding — simpler today, easy to lift
 * up later if we add a persistent layout.
 */
@Composable
fun CatalogScreen(
    selectedTab: Tab,
    title:       String,
    items:       List<MediaItem>,
    isLoading:   Boolean,
    error:       String?,
    onRetry:     () -> Unit,
    onTabSelected: (Tab) -> Unit,
    onLogOut:    () -> Unit,
    cardContent: @Composable (MediaItem) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = BgBase) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopNav(
                selectedTab   = selectedTab,
                onTabSelected = onTabSelected,
                onSearch      = { /* search modal — next sprint */ },
                onLogOut      = onLogOut,
            )
            Text(
                text       = title,
                style      = MaterialTheme.typography.headlineMedium,
                color      = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
            )
            when {
                isLoading && items.isEmpty() -> CenteredSpinner()
                error != null && items.isEmpty() -> ErrorBanner(message = error, onRetry = onRetry)
                items.isEmpty() -> EmptyBanner()
                else -> LazyVerticalGrid(
                    // Slot ~160dp matches the Portrait card's 150dp footprint
                    // closely, so most of the visual "air" between cards
                    // comes from the explicit horizontal spacing — not
                    // from the slot being wider than its content.
                    columns               = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding        = PaddingValues(start = 32.dp, end = 32.dp, top = 12.dp, bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement   = Arrangement.spacedBy(14.dp),
                    modifier              = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.id }) { item ->
                        cardContent(item)
                    }
                }
            }
        }
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
        TextButton(onClick = onRetry) { Text("Reintentar") }
    }
}

@Composable
private fun EmptyBanner() {
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text  = "Sin contenido en esta biblioteca",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Helper card factories so each catalog screen doesn't repeat the
 * MediaCard wiring inline.
 */
@Composable
fun PortraitCatalogCard(
    item:       MediaItem,
    onOpen:     (String, MediaKind) -> Unit,
) {
    MediaCard(
        item          = item,
        style         = CardStyle.Portrait,
        expandOnFocus = false,
        onFocused     = { /* no focus preview on catalog screens */ },
        onClick       = { onOpen(it.id, it.kind) },
    )
}

