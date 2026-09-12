package com.alex.hubplay.ui.detail

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alex.hubplay.R
import com.alex.hubplay.data.Content
import com.alex.hubplay.data.MediaKind
import com.alex.hubplay.ui.components.HeroActions
import com.alex.hubplay.ui.components.HeroCta
import com.alex.hubplay.ui.components.HeroCtas
import com.alex.hubplay.ui.components.HeroDetailConfig
import com.alex.hubplay.ui.components.HeroDetailScaffold
import com.alex.hubplay.ui.components.HeroHeader
import com.alex.hubplay.ui.components.HeroNav
import com.alex.hubplay.ui.components.HeroRails
import com.alex.hubplay.ui.components.HeroToggles
import com.alex.hubplay.ui.components.IdentifyDialog
import com.alex.hubplay.ui.theme.Accent

/**
 * Ficha de película. La UI vive en [HeroDetailScaffold] (compartida con
 * Series): aquí solo se traduce el estado del ViewModel a la configuración
 * del hero (Reproducir/Reanudar, fila de metadatos de película, rails de
 * reparto, saga y "más como esto") y se cuelga el diálogo de Identificar.
 */
@Composable
fun DetailScreen(
    viewModel:          DetailViewModel,
    onPlay:             (itemId: String, resumePosSec: Long) -> Unit,
    onBack:             () -> Unit,
    onOpenCollection:   (collectionId: String) -> Unit = {},
    onOpenPerson:       (personId: String) -> Unit = {},
    onOpenItem:         (itemId: String, kind: MediaKind) -> Unit = { _, _ -> },
    onOpenStudio:       (studioSlug: String) -> Unit = {},
    trailerResumeSec:   Long = 0L,
) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current

    // Avisos de las acciones de metadatos: un Toast basta en TV.
    LaunchedEffect(ui.notice) {
        val notice = ui.notice ?: return@LaunchedEffect
        Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
        viewModel.clearNotice()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        val item = ui.item
        when {
            ui.isLoading     -> CenteredSpinner()
            ui.error != null -> ErrorBanner(message = ui.error!!, onRetry = viewModel::load)
            item != null     -> {
                val resumePosSec = (item as? Content.Resumable)?.resumePosSec ?: 0L
                val playLabel = if (resumePosSec > 0) {
                    stringResource(R.string.detail_resume_format, resumePosSec / 60, resumePosSec % 60)
                } else {
                    stringResource(R.string.detail_play)
                }
                val nav = remember(onOpenCollection, onOpenStudio, onOpenPerson, onOpenItem) {
                    HeroNav(
                        onOpenCollection = onOpenCollection,
                        onOpenStudio     = onOpenStudio,
                        onOpenPerson     = onOpenPerson,
                        onOpenItem       = onOpenItem,
                    )
                }
                val config = HeroDetailConfig(
                    item    = item,
                    header  = HeroHeader(meta = { MovieMetaRow(item) }),
                    ctas    = HeroCtas(
                        play = HeroCta(
                            label   = playLabel,
                            icon    = Icons.Default.PlayArrow,
                            onClick = { onPlay(item.id, resumePosSec) },
                        ),
                    ),
                    toggles = HeroToggles(showWatched = true, canEditMetadata = ui.canEditMetadata),
                    actions = HeroActions(
                        onBack           = onBack,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onToggleWatched  = viewModel::toggleWatched,
                        onIdentify       = viewModel::openIdentify,
                    ),
                    nav     = nav,
                )
                HeroDetailScaffold(
                    config           = config,
                    rails            = HeroRails(
                        people     = (item as? Content.Movie)?.people.orEmpty(),
                        collection = ui.collection,
                        related    = ui.related,
                    ),
                    trailerResumeSec = trailerResumeSec,
                    dialogOpen       = ui.identify != null,
                )
                ui.identify?.let { state ->
                    IdentifyDialog(
                        state     = state,
                        onSearch  = viewModel::searchCandidates,
                        onPick    = viewModel::applyIdentify,
                        onRefresh = {
                            viewModel.closeIdentify()
                            viewModel.refreshMetadata()
                        },
                        onDismiss = viewModel::closeIdentify,
                    )
                }
            }
        }
    }
}

/** `★ nota · año · duración · géneros` de una película. */
@Composable
private fun MovieMetaRow(item: Content) {
    val durationSec = (item as? Content.Resumable)?.durationSec ?: 0L
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
                maxLines   = 1,
            )
        }
        item.year?.let {
            Text(
                text     = it.toString(),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
        }
        if (durationSec > 0) {
            // Separador solo si hay algo delante (sin año ni nota, la
            // duración es el primer dato y un "· 140 min" suelto queda mal).
            if (item.year != null || item.rating != null) {
                MetaDot()
            }
            Text(
                text     = stringResource(R.string.detail_duration_minutes, durationSec / 60),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
        }
        item.genres.take(MAX_GENRES).forEach { genre ->
            MetaDot()
            Text(
                text     = genre,
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MetaDot() {
    Text("·", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val MAX_GENRES = 3

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
