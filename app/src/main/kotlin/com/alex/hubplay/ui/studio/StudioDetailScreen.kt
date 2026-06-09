package com.alex.hubplay.ui.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alex.hubplay.R
import com.alex.hubplay.data.MediaKind
import com.alex.hubplay.data.StudioDetail
import com.alex.hubplay.ui.catalog.PortraitCatalogCard
import com.alex.hubplay.ui.components.BackPill

/**
 * Studio / network profile + catalogue. Reached by tapping the studio
 * chip on the Detail screen. Header is the studio logo (or name) + a back
 * pill; the body is the same poster grid the catalogue uses.
 */
@Composable
fun StudioDetailScreen(
    viewModel:  StudioDetailViewModel,
    onOpenItem: (String, MediaKind) -> Unit,
    onBack:     () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            ui.isLoading      -> CenteredSpinner()
            ui.error != null  -> ErrorBanner(message = ui.error!!, onRetry = viewModel::load)
            ui.studio != null -> StudioContent(
                studio     = ui.studio!!,
                onOpenItem = onOpenItem,
                onBack     = onBack,
            )
        }
    }
}

@Composable
private fun StudioContent(
    studio:     StudioDetail,
    onOpenItem: (String, MediaKind) -> Unit,
    onBack:     () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        StudioHeader(studio = studio, onBack = onBack)
        if (studio.items.isEmpty()) {
            EmptyCatalogue()
        } else {
            LazyVerticalGrid(
                columns               = GridCells.Adaptive(minSize = 150.dp),
                contentPadding        = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement   = Arrangement.spacedBy(14.dp),
                modifier              = Modifier.fillMaxSize(),
            ) {
                items(studio.items, key = { it.id }) { item ->
                    PortraitCatalogCard(item, onOpenItem)
                }
            }
        }
    }
}

@Composable
private fun StudioHeader(studio: StudioDetail, onBack: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackPill(onBack = onBack)
        Spacer(Modifier.width(20.dp))
        if (!studio.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model              = studio.logoUrl,
                contentDescription = studio.name,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier
                    .heightIn(min = 36.dp, max = 64.dp)
                    .widthIn(max = 220.dp),
            )
        } else {
            Text(
                text       = studio.name,
                style      = MaterialTheme.typography.headlineMedium,
                color      = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
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
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
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
private fun EmptyCatalogue() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text  = stringResource(R.string.studio_no_items),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
