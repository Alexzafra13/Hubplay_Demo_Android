package com.alex.hubplay.ui.shared

import androidx.compose.runtime.Immutable
import com.alex.hubplay.data.Content
import com.alex.hubplay.data.SharedProgress
import com.alex.hubplay.ui.series.SeriesData
import com.alex.hubplay.ui.series.SeriesResumeResolver
import com.alex.hubplay.ui.series.SeriesResumeTarget

/**
 * Temporadas y episodios de una serie de un amigo. Los episodios se piden
 * por temporada al elegirla (cada temporada es una llamada al amigo) y su
 * progreso, que vive en NUESTRO servidor, a la vez.
 */
@Immutable
data class SharedSeriesState(
    val seasons:          List<Content.Shared> = emptyList(),
    val selectedSeasonId: String? = null,
    val episodesBySeason: Map<String, List<Content.Shared>> = emptyMap(),
    /** Progreso propio por episodio (de las temporadas ya cargadas). */
    val progress:         Map<String, SharedProgress> = emptyMap(),
    val isLoading:        Boolean = true,
    /** El amigo no responde o su HubPlay aún no sabe listar episodios. */
    val unavailable:      Boolean = false,
) {
    val selectedEpisodes: List<Content.Shared>
        get() = selectedSeasonId?.let { episodesBySeason[it] }.orEmpty()

    fun episode(id: String): Content.Shared? =
        episodesBySeason.values.firstNotNullOfOrNull { eps -> eps.firstOrNull { it.id == id } }
}

/** La temporada que se abre primero: la 1 (o la primera "normal"), no Especiales. */
internal fun firstRegularSeason(seasons: List<Content.Shared>): Content.Shared? =
    seasons.firstOrNull { (it.seasonNumber ?: 0) >= 1 } ?: seasons.firstOrNull()

/** Visto: marcado como completado o pasado del 90 % (lo que ya no se reanuda). */
private val SharedProgress.isWatched: Boolean
    get() = completed || (positionSec > 0 && resumeSec == 0L)

/** Un episodio de amigo con la forma de la serie local (mismo panel de episodios). */
internal fun Content.Shared.asEpisode(seriesId: String, progress: SharedProgress?): Content.Episode {
    val pct = when {
        progress == null           -> 0f
        progress.isWatched         -> 1f
        progress.durationSec > 0   -> (progress.positionSec.toFloat() / progress.durationSec).coerceIn(0f, 1f)
        else                       -> 0f
    }
    return Content.Episode(
        id            = id,
        title         = title,
        // La carátula de tarjeta (400 px) basta para la miniatura de 220 dp.
        posterUrl     = posterUrl,
        durationSec   = durationSec,
        progressPct   = pct,
        resumePosSec  = progress?.resumeSec ?: 0L,
        seriesId      = seriesId,
        seasonNumber  = seasonNumber,
        episodeNumber = episodeNumber,
        watched       = progress?.isWatched == true,
    )
}

/**
 * Qué pone Reproducir, con el mismo criterio que una serie propia
 * ([SeriesResumeResolver]) dentro de la temporada abierta: el episodio
 * a medias; si no, el siguiente al último visto; si no, el primero.
 */
internal fun SharedSeriesState.resumeTarget(seriesId: String): SeriesResumeTarget {
    val episodes = selectedEpisodes.map { it.asEpisode(seriesId, progress[it.id]) }
    val inProgress = episodes.filter { it.resumePosSec > 0 }
    val lastWatched = episodes.indexOfLast { it.watched }
    val next = if (lastWatched >= 0) episodes.getOrNull(lastWatched + 1) else null
    return SeriesResumeResolver.resolve(
        seriesId            = seriesId,
        continueWatching    = inProgress,
        nextUp              = listOfNotNull(next),
        firstSeasonEpisodes = episodes,
    )
}

/** Todo lo que pinta el panel de episodios de la serie local. */
internal fun SharedSeriesState.toSeriesData(series: Content.Shared): SeriesData = SeriesData(
    series             = Content.Series(
        id          = series.id,
        title       = series.title,
        posterUrl   = series.posterUrl,
        backdropUrl = series.backdropUrl,
        overview    = series.overview,
        year        = series.year,
    ),
    seasons            = seasons.map {
        Content.Season(
            id           = it.id,
            title        = it.title,
            posterUrl    = it.posterUrl,
            parentId     = series.id,
            seasonNumber = it.seasonNumber,
        )
    },
    episodesBySeasonId = episodesBySeason.mapValues { (_, eps) -> eps.map { it.asEpisode(series.id, progress[it.id]) } },
    selectedSeasonId   = selectedSeasonId,
    resume             = resumeTarget(series.id),
)
