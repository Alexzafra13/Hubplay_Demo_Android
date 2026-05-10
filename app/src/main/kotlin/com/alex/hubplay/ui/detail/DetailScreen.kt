package com.alex.hubplay.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alex.hubplay.data.MediaItem
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.BgBase
import com.alex.hubplay.ui.theme.OnAccent

/**
 * Item detail page.
 *
 * Layout (top → bottom):
 *   - Backdrop full-bleed, faded into the page background at the
 *     bottom 40% so the info overlay reads cleanly.
 *   - Floating back button top-left.
 *   - Info row at the bottom of the backdrop: poster (left) + title /
 *     meta / overview / CTAs (right).
 *   - Below the backdrop: extra metadata sections (cast,
 *     recommendations) in a future iteration.
 */
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onPlay:    (itemId: String, resumePosSec: Long) -> Unit,
    onBack:    () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = BgBase,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            when {
                ui.isLoading -> CenteredSpinner()
                ui.error != null -> ErrorBanner(message = ui.error!!, onRetry = viewModel::load)
                ui.item != null -> Hero(item = ui.item!!, onPlay = onPlay)
            }
        }

        // Floating back button — always visible above the backdrop.
        IconButton(
            onClick  = onBack,
            modifier = Modifier
                .padding(12.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f)),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver",
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun Hero(item: MediaItem, onPlay: (String, Long) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(540.dp)) {
        // Backdrop
        AsyncImage(
            model              = item.backdropUrl ?: item.posterUrl,
            contentDescription = item.title,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize(),
        )
        // Vertical fade to bg base
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f   to Color.Transparent,
                        0.55f to BgBase.copy(alpha = 0.55f),
                        1f   to BgBase,
                    ),
                ),
        )
        // Horizontal fade left so text reads
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.45f)
                .background(
                    Brush.horizontalGradient(
                        0f to BgBase.copy(alpha = 0.85f),
                        1f to Color.Transparent,
                    ),
                ),
        )

        // Info row at the bottom of the backdrop
        Row(
            modifier              = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 28.dp),
            verticalAlignment     = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Poster (left)
            item.posterUrl?.let {
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    AsyncImage(
                        model              = it,
                        contentDescription = item.title,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize(),
                    )
                }
            }

            // Info (right)
            Column(modifier = Modifier.widthIn(max = 720.dp)) {
                Text(
                    text       = item.title,
                    style      = MaterialTheme.typography.displayMedium,
                    color      = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                )
                if (!item.subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = item.subtitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))

                MetaRow(item)
                Spacer(Modifier.height(16.dp))

                if (!item.overview.isNullOrBlank()) {
                    Text(
                        text  = item.overview,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(20.dp))
                }

                CtaRow(item = item, onPlay = onPlay)
            }
        }
    }
}

@Composable
private fun MetaRow(item: MediaItem) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item.rating?.let {
            Text(
                text       = "★ ${"%.1f".format(it)}",
                style      = MaterialTheme.typography.bodyMedium,
                color      = Accent,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item.year?.let {
            Text(it.toString(), style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item.genres.take(4).forEach { genre ->
            Text("· $genre", style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CtaRow(item: MediaItem, onPlay: (String, Long) -> Unit) {
    val playLabel = if (item.resumePosSec > 0) {
        val mins = item.resumePosSec / 60
        val secs = item.resumePosSec % 60
        "Reanudar ${mins}:${secs.toString().padStart(2, '0')}"
    } else "Reproducir"

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = { onPlay(item.id, item.resumePosSec) },
            shape   = RoundedCornerShape(10.dp),
            colors  = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor   = OnAccent,
            ),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(playLabel, fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(
            onClick = { /* Add-to-list — endpoint exists; wire in next sprint. */ },
            shape   = RoundedCornerShape(10.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Mi lista")
        }
    }
}

@Composable
private fun CenteredSpinner() {
    Box(
        modifier = Modifier.fillMaxSize().height(540.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text("Reintentar") }
    }
}
