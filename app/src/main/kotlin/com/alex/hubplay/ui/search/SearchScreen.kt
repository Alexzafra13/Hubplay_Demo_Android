package com.alex.hubplay.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alex.hubplay.R
import com.alex.hubplay.data.MediaKind
import com.alex.hubplay.ui.catalog.PortraitCatalogCard
import com.alex.hubplay.ui.home.components.Tab
import com.alex.hubplay.ui.home.components.TopNav
import com.alex.hubplay.ui.theme.BgBase

/**
 * Search surface — TopNav + search input + result grid.
 *
 * Keyboard auto-focuses on the input the first time the screen lands so a
 * user opening the tab can start typing immediately. A trailing clear-icon
 * appears as soon as the query is non-empty.
 *
 * Result grid reuses the same Portrait card the Movies / Series tabs use,
 * so tapping a card routes through the standard openItem rule (series →
 * SeriesScreen, everything else → Detail). The card factory handles the
 * routing on its own; we just hand it the openItem callback.
 */
@Composable
fun SearchScreen(
    viewModel:     SearchViewModel,
    onTabSelected: (Tab) -> Unit,
    onOpenItem:    (itemId: String, kind: MediaKind) -> Unit,
    onLogOut:      () -> Unit,
    onSettings:    () -> Unit = {},
) {
    val ui by viewModel.ui.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = BgBase) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopNav(
                selectedTab   = Tab.Search,
                onTabSelected = onTabSelected,
                onLogOut      = onLogOut,
                onSettings    = onSettings,
            )

            SearchInputBar(
                query       = ui.query,
                onChange    = viewModel::onQueryChange,
                onClear     = viewModel::clear,
                isSearching = ui.isSearching,
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    ui.error != null && ui.results.isEmpty() -> ErrorMessage(ui.error!!)
                    ui.query.trim().length < 2               -> EmptyHint()
                    !ui.isSearching && ui.results.isEmpty()  -> NoMatches(ui.query)
                    else                                     -> ResultsGrid(
                        items         = ui.results,
                        isLoadingMore = ui.isLoadingMore,
                        canLoadMore   = ui.canLoadMore,
                        onLoadMore    = viewModel::loadMore,
                        onOpenItem    = onOpenItem,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchInputBar(
    query:       String,
    onChange:    (String) -> Unit,
    onClear:     () -> Unit,
    isSearching: Boolean,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    OutlinedTextField(
        value           = query,
        onValueChange   = onChange,
        singleLine      = true,
        placeholder     = { Text(stringResource(R.string.search_placeholder)) },
        leadingIcon     = {
            Icon(
                imageVector        = Icons.Default.Search,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            when {
                isSearching -> CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier    = Modifier.size(20.dp),
                    color       = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                query.isNotEmpty() -> IconButton(onClick = onClear) {
                    Icon(
                        imageVector        = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_clear),
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> { /* nothing */ }
            }
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction      = ImeAction.Search,
        ),
        shape           = RoundedCornerShape(14.dp),
        modifier        = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 12.dp)
            .focusRequester(focus),
    )
}

@Composable
private fun ResultsGrid(
    items:         List<com.alex.hubplay.data.Content>,
    isLoadingMore: Boolean,
    canLoadMore:   Boolean,
    onLoadMore:    () -> Unit,
    onOpenItem:    (String, MediaKind) -> Unit,
) {
    val gridState = rememberLazyGridState()

    val nearBottom by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = gridState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 8
        }
    }
    LaunchedEffect(nearBottom) {
        if (nearBottom && canLoadMore) onLoadMore()
    }

    LazyVerticalGrid(
        columns               = GridCells.Adaptive(minSize = 160.dp),
        state                 = gridState,
        contentPadding        = PaddingValues(start = 32.dp, end = 32.dp, top = 4.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement   = Arrangement.spacedBy(14.dp),
        modifier              = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.id }) { item ->
            PortraitCatalogCard(item, onOpenItem)
        }
        if (isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Column(
        modifier            = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector        = Icons.Default.Search,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text      = stringResource(R.string.search_empty_hint),
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NoMatches(query: String) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text       = stringResource(R.string.search_no_results, query.trim()),
            style      = MaterialTheme.typography.titleMedium,
            color      = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text  = stringResource(R.string.search_no_results_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Row(
        modifier              = Modifier.fillMaxSize().padding(32.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text      = message,
            color     = MaterialTheme.colorScheme.error,
            style     = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}
