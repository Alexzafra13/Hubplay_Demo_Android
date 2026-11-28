package com.alex.hubplay.ui.shared

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.alex.hubplay.ui.series.SeriesEpisodesPanel

/**
 * Ficha de un título que comparte un amigo. Mismo hero que una película
 * propia ([HeroDetailScaffold]) con lo que la federación da: póster (que
 * hace de fondo), título, año y sinopsis. Sin favorito, visto ni
 * reparto. Reproducir reanuda si hay posición guardada; "Desde el
 * principio" aparece entonces debajo.
 */
@Composable
fun SharedDetailScreen(
    viewModel: SharedDetailViewModel,
    onPlay:    (item: Content.Shared, resumeSec: Long) -> Unit,
    onBack:    () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()
    // Al volver del reproductor la posición ha cambiado.
    LaunchedEffect(Unit) { if (ui.item != null) viewModel.refreshProgress() }

    val item = ui.item
    when {
        ui.isLoading && item == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        item == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text  = stringResource(R.string.shared_not_found),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item.remoteKind == MediaKind.Series -> SharedSeriesDetail(
            item           = item,
            series         = ui.series ?: SharedSeriesState(),
            onSelectSeason = viewModel::selectSeason,
            onPlay         = onPlay,
            onBack         = onBack,
        )
        else -> SharedMovieHero(item = item, resumeSec = ui.resumeSec, onPlay = onPlay, onBack = onBack)
    }
}

@Composable
private fun SharedMovieHero(
    item:      Content.Shared,
    resumeSec: Long,
    onPlay:    (Content.Shared, Long) -> Unit,
    onBack:    () -> Unit,
) {
    val playLabel = if (resumeSec > 0) {
        stringResource(R.string.detail_resume_format, resumeSec / SECONDS_PER_MINUTE, resumeSec % SECONDS_PER_MINUTE)
    } else {
        stringResource(R.string.detail_play)
    }
    val fromStart = HeroCta(
        label   = stringResource(R.string.shared_play_from_start),
        icon    = Icons.Default.Replay,
        onClick = { onPlay(item, 0L) },
    )
    SharedHero(
        item   = item,
        ctas   = HeroCtas(
            play      = HeroCta(label = playLabel, icon = Icons.Default.PlayArrow, onClick = { onPlay(item, resumeSec) }),
            secondary = fromStart.takeIf { resumeSec > 0 },
        ),
        onBack = onBack,
    )
}

/**
 * Serie de un amigo: el hero reproduce el episodio que toca (a medias, el
 * siguiente al último visto o el primero) y "Episodios" abre el mismo
 * panel de temporadas que una serie propia.
 */
@Composable
private fun SharedSeriesDetail(
    item:           Content.Shared,
    series:         SharedSeriesState,
    onSelectSeason: (String) -> Unit,
    onPlay:         (Content.Shared, Long) -> Unit,
    onBack:         () -> Unit,
) {
    var showEpisodes by rememberSaveable { mutableStateOf(false) }
    val data = remember(item, series) { series.toSeriesData(item) }
    val playEpisode: (String, Long) -> Unit = { id, resume -> series.episode(id)?.let { onPlay(it, resume) } }
    if (showEpisodes) {
        SeriesEpisodesPanel(
            data           = data,
            onSelectSeason = onSelectSeason,
            onPlayEpisode  = playEpisode,
            onBack         = { showEpisodes = false },
        )
        return
    }
    val context = LocalContext.current
    val unavailable = stringResource(R.string.shared_series_unavailable)
    val resume = data.resume
    val playLabel = when {
        resume.playLabel != null -> resume.playLabel
        series.isLoading         -> stringResource(R.string.shared_series_loading)
        else                     -> stringResource(R.string.series_play_empty)
    }
    SharedHero(
        item   = item,
        ctas   = HeroCtas(
            play      = HeroCta(
                label   = playLabel,
                icon    = Icons.Default.PlayArrow,
                enabled = resume.episodeId != null || series.unavailable,
                onClick = {
                    val id = resume.episodeId
                    when {
                        id != null         -> playEpisode(id, resume.resumeSec)
                        series.unavailable -> Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
                    }
                },
            ),
            secondary = HeroCta(
                label   = stringResource(R.string.series_episodes_action),
                icon    = Icons.Default.VideoLibrary,
                onClick = { showEpisodes = true },
            ).takeIf { series.seasons.isNotEmpty() },
        ),
        onBack = onBack,
    )
}

@Composable
private fun SharedHero(
    item:   Content.Shared,
    ctas:   HeroCtas,
    onBack: () -> Unit,
) {
    val config = HeroDetailConfig(
        // Sin subtítulo: el amigo ya sale en la fila de metadatos.
        item    = item.copy(subtitle = null),
        header  = HeroHeader(meta = { SharedMetaRow(item) }),
        ctas    = ctas,
        toggles = HeroToggles(showWatched = false, canEditMetadata = false, showFavorite = false),
        actions = HeroActions(
            onBack           = onBack,
            onToggleFavorite = {},
            onToggleWatched  = null,
            onIdentify       = {},
        ),
        nav     = NoNav,
    )
    HeroDetailScaffold(config = config, rails = HeroRails())
}

/** `año · Película · Compartido por Pedro`. */
@Composable
private fun SharedMetaRow(item: Content.Shared) {
    val kind = if (item.remoteKind == MediaKind.Series) R.string.shared_series else R.string.shared_movie
    val parts = buildList {
        item.year?.let { add(HeroMeta(it.toString())) }
        add(HeroMeta(stringResource(kind)))
        if (item.peerName.isNotBlank()) add(HeroMeta(stringResource(R.string.shared_by, item.peerName), accent = true))
    }
    HeroMetaRow(parts)
}

/** La ficha de un amigo no enlaza a reparto, estudios ni sagas (no viajan). */
private val NoNav = HeroNav(
    onOpenCollection = {},
    onOpenStudio     = {},
    onOpenPerson     = {},
    onOpenItem       = { _, _ -> },
)

private const val SECONDS_PER_MINUTE = 60L
