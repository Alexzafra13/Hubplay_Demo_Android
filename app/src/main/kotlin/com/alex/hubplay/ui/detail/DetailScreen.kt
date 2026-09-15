package com.alex.hubplay.ui.detail

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
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
import com.alex.hubplay.ui.components.HeroMeta
import com.alex.hubplay.ui.components.HeroMetaRow
import com.alex.hubplay.ui.components.HeroNav
import com.alex.hubplay.ui.components.HeroRails
import com.alex.hubplay.ui.components.HeroToggles
import com.alex.hubplay.ui.components.IdentifyOverlay

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
    val tools by viewModel.toolsState.collectAsState()
    val context = LocalContext.current

    // Avisos de las acciones de metadatos: un Toast basta en TV.
    LaunchedEffect(tools.notice) {
        val notice = tools.notice ?: return@LaunchedEffect
        Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
        viewModel.tools.clearNotice()
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
                    toggles = HeroToggles(showWatched = true, canEditMetadata = tools.canEditMetadata),
                    actions = HeroActions(
                        onBack           = onBack,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onToggleWatched  = viewModel::toggleWatched,
                        onIdentify       = { viewModel.tools.openIdentify(item.title, item.year) },
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
                    dialogOpen       = tools.identify != null,
                )
                tools.identify?.let { state ->
                    IdentifyOverlay(
                        state     = state,
                        onSearch  = viewModel.tools::searchCandidates,
                        onPick    = viewModel.tools::applyIdentify,
                        onRefresh = {
                            viewModel.tools.closeIdentify()
                            viewModel.tools.refreshMetadata()
                        },
                        onDismiss = viewModel.tools::closeIdentify,
                    )
                }
            }
        }
    }
}

/** `★ nota · año · duración · géneros` de una película, en un solo Text ([HeroMetaRow]). */
@Composable
private fun MovieMetaRow(item: Content) {
    val durationSec = (item as? Content.Resumable)?.durationSec ?: 0L
    val parts = buildList {
        item.rating?.let { add(HeroMeta("★ ${"%.1f".format(it)}", accent = true)) }
        item.year?.let { add(HeroMeta(it.toString())) }
        if (durationSec > 0) add(HeroMeta(stringResource(R.string.detail_duration_minutes, durationSec / 60)))
        item.genres.take(MAX_GENRES).forEach { add(HeroMeta(it)) }
    }
    HeroMetaRow(parts)
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
