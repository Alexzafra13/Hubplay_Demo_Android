package com.alex.hubplay.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alex.hubplay.data.ContinueWatchingItem

/**
 * The first screen the user sees after login. For now: just the Continue
 * Watching rail — enough to demo end-to-end (login → tap card → player).
 *
 * Other rails (Latest, Trending, Next Up, LiveNow) plug into this same
 * shell as additional [HomeRail] sections following the same pattern.
 */
@Composable
fun HomeScreen(
    viewModel:   HomeViewModel,
    onPlayItem:  (itemId: String, resumePosSec: Long) -> Unit,
    onLogOut:    () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
            // Top bar — minimal: brand on the left, log-out on the right.
            // A real top bar with avatar / search / settings comes later.
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text       = "HubPlay",
                    style      = MaterialTheme.typography.headlineMedium,
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onLogOut) { Text("Cerrar sesión") }
            }
            Spacer(Modifier.height(16.dp))

            when {
                ui.isLoading && ui.continueWatching.isEmpty() -> CenteredSpinner()
                ui.error != null -> ErrorBanner(message = ui.error!!, onRetry = viewModel::refresh)
                ui.continueWatching.isEmpty()                  -> EmptyStateMessage()
                else                                           -> ContinueWatchingRail(
                    items     = ui.continueWatching,
                    onTap     = onPlayItem,
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingRail(
    items: List<ContinueWatchingItem>,
    onTap: (itemId: String, resumePosSec: Long) -> Unit,
) {
    Column {
        Text(
            text     = "Continuar viendo",
            style    = MaterialTheme.typography.titleLarge,
            color    = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { item ->
                ContinueWatchingCard(item = item, onTap = onTap)
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    onTap: (itemId: String, resumePosSec: Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .clickable { onTap(item.id, item.resumePosSec) },
    ) {
        // 16:9 thumb. AsyncImage is Coil 3's Composable; the
        // OkHttp-backed network engine carries our auth headers
        // automatically thanks to coil-network-okhttp.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model              = item.imageUrl,
                contentDescription = item.title,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
            )
            // Resume progress bar overlaid at the bottom — the Plex tell.
            if (item.progressPct > 0f) {
                LinearProgressIndicator(
                    progress = { item.progressPct },
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                    color    = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text       = item.title,
            style      = MaterialTheme.typography.bodyMedium,
            color      = MaterialTheme.colorScheme.onBackground,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
        )
        item.subtitle?.let { sub ->
            Text(
                text     = sub,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
private fun EmptyStateMessage() {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text  = "Aún no has empezado a ver nada.\nElige algo desde la web y lo verás aquí.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        Text(
            text  = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text("Reintentar") }
    }
}
