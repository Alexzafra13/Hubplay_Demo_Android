package com.alex.hubplay.ui.shared

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
        else -> SharedHero(item = item, resumeSec = ui.resumeSec, onPlay = onPlay, onBack = onBack)
    }
}

@Composable
private fun SharedHero(
    item:      Content.Shared,
    resumeSec: Long,
    onPlay:    (Content.Shared, Long) -> Unit,
    onBack:    () -> Unit,
) {
    val context = LocalContext.current
    val unavailable = stringResource(R.string.shared_series_unavailable)
    val playLabel = if (resumeSec > 0) {
        stringResource(R.string.detail_resume_format, resumeSec / SECONDS_PER_MINUTE, resumeSec % SECONDS_PER_MINUTE)
    } else {
        stringResource(R.string.detail_play)
    }
    val play: () -> Unit = {
        if (item.isPlayable) {
            onPlay(item, resumeSec)
        } else {
            Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
        }
    }
    val fromStart = HeroCta(
        label   = stringResource(R.string.shared_play_from_start),
        icon    = Icons.Default.Replay,
        onClick = { onPlay(item, 0L) },
    )
    val config = HeroDetailConfig(
        // Sin subtítulo: el amigo ya sale en la fila de metadatos.
        item    = item.copy(subtitle = null),
        header  = HeroHeader(meta = { SharedMetaRow(item) }),
        ctas    = HeroCtas(
            play      = HeroCta(label = playLabel, icon = Icons.Default.PlayArrow, onClick = play),
            secondary = fromStart.takeIf { resumeSec > 0 && item.isPlayable },
        ),
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
