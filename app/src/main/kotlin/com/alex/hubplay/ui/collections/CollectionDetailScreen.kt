package com.alex.hubplay.ui.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alex.hubplay.R
import com.alex.hubplay.data.MediaKind
import com.alex.hubplay.ui.components.BackPill
import com.alex.hubplay.ui.home.components.CardStyle
import com.alex.hubplay.ui.home.components.MediaCard

/**
 * Saga detail surface — backdrop hero with title + overview overlaid,
 * then a grid of the member movies in release order. Same poster grid
 * as Movies/Series so the user opens each card into the existing
 * Detail flow without a context switch.
 */
@Composable
fun CollectionDetailScreen(
    viewModel: CollectionDetailViewModel,
    onOpenItem: (String, MediaKind) -> Unit,
    onBack:    () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            ui.isLoading && ui.detail == null    -> CenteredSpinner()
            ui.error != null && ui.detail == null -> ErrorBanner(
                message = ui.error!!,
                onRetry = viewModel::load,
                onBack  = onBack,
            )
            ui.detail != null -> Hero(detail = ui.detail!!, onOpenItem = onOpenItem, onBack = onBack)
        }
    }
}

@Composable
private fun Hero(
    detail:     com.alex.hubplay.data.CollectionDetail,
    onOpenItem: (String, MediaKind) -> Unit,
    onBack:     () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // ── Backdrop layer — full bleed, faded so the grid below stays
        //    legible against the art.
        if (!detail.backdropUrl.isNullOrBlank()) {
            AsyncImage(
                model              = detail.backdropUrl,
                contentDescription = detail.name,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize().alpha(0.55f),
            )
            // Bottom gradient so the grid reads against the backdrop.
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f   to Color.Transparent,
                        0.5f to MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                        1f   to MaterialTheme.colorScheme.background,
                    ),
                ),
            )
        }

        // ── Content: header + member grid in a single scrollable column.
        LazyVerticalGrid(
            columns               = GridCells.Adaptive(minSize = 160.dp),
            contentPadding        = PaddingValues(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement   = Arrangement.spacedBy(14.dp),
            modifier              = Modifier.fillMaxSize(),
        ) {
            // Header occupies the full width — title + overview.
            item(span = { GridItemSpan(maxLineSpan) }) {
                Header(detail = detail)
            }
            items(detail.items, key = { it.id }) { item ->
                MediaCard(
                    item      = item,
                    style     = CardStyle.Portrait,
                    onFocused = { /* no focus preview on collection grid */ },
                    onClick   = { onOpenItem(it.id, it.kind) },
                )
            }
        }

        // Back pill, always on top of the hero.
        Box(modifier = Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 20.dp)) {
            BackPill(onBack = onBack)
        }
    }
}

@Composable
private fun Header(detail: com.alex.hubplay.data.CollectionDetail) {
    Column(
        modifier            = Modifier.fillMaxWidth().padding(top = 60.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text       = detail.name,
            style      = MaterialTheme.typography.displaySmall,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text  = stringResource(R.string.collections_member_count, detail.items.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!detail.overview.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text     = detail.overview,
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onBackground,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 720.dp),
            )
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
private fun ErrorBanner(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 20.dp)) {
            BackPill(onBack = onBack)
        }
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
}

