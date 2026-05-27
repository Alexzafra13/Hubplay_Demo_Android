package com.alex.hubplay.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alex.hubplay.R
import com.alex.hubplay.data.Content
import com.alex.hubplay.data.MediaKind
import com.alex.hubplay.ui.home.components.CardStyle
import com.alex.hubplay.ui.home.components.MediaCard
import com.alex.hubplay.ui.home.components.Tab
import com.alex.hubplay.ui.home.components.TopNav
import com.alex.hubplay.ui.theme.BgBase

@Composable
fun CatalogScreen(
    selectedTab:   Tab,
    title:         String,
    items:         List<Content>,
    isLoading:     Boolean,
    isLoadingMore: Boolean        = false,
    canLoadMore:   Boolean        = false,
    error:         String?,
    onRetry:       () -> Unit,
    onLoadMore:    () -> Unit     = {},
    onTabSelected: (Tab) -> Unit,
    onLogOut:      () -> Unit,
    onSettings:    () -> Unit     = {},
    cardContent:   @Composable (Content) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = BgBase) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopNav(
                selectedTab   = selectedTab,
                onTabSelected = onTabSelected,
                onLogOut      = onLogOut,
                onSettings    = onSettings,
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
                else -> {
                    val gridState = rememberLazyGridState()

                    val nearBottom by remember {
                        derivedStateOf {
                            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            val total = gridState.layoutInfo.totalItemsCount
                            total > 0 && last >= total - LOAD_MORE_THRESHOLD
                        }
                    }
                    LaunchedEffect(nearBottom) {
                        if (nearBottom && canLoadMore) onLoadMore()
                    }

                    LazyVerticalGrid(
                        columns               = GridCells.Adaptive(minSize = 160.dp),
                        state                 = gridState,
                        contentPadding        = PaddingValues(start = 32.dp, end = 32.dp, top = 12.dp, bottom = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement   = Arrangement.spacedBy(14.dp),
                        modifier              = Modifier.fillMaxSize(),
                    ) {
                        items(items, key = { it.id }) { item ->
                            cardContent(item)
                        }
                        if (isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 8

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
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text  = stringResource(R.string.catalog_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun PortraitCatalogCard(
    item:       Content,
    onOpen:     (String, MediaKind) -> Unit,
) {
    MediaCard(
        item      = item,
        style     = CardStyle.Portrait,
        onFocused = {},
        onClick   = { onOpen(it.id, it.kind) },
    )
}
