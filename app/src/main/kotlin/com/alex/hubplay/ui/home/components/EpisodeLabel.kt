package com.alex.hubplay.ui.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.alex.hubplay.R
import com.alex.hubplay.data.Content

/** Prefijo "S2 · E4" que el repositorio pone delante del nombre del episodio. */
private val RepoEpisodePrefix = Regex("""^S\d+ · E\d+(?: · )?""")

/**
 * Nombre del episodio sin el código: en Inicio el `title` de un episodio es
 * la serie y el nombre viaja en `subtitle` ("S3 · E1 · Fuego cruzado").
 */
internal fun episodeName(item: Content.Episode): String? =
    item.subtitle?.replace(RepoEpisodePrefix, "")?.trim()?.ifBlank { null }

/** "T3 · E1" en el idioma de la app, o null si faltan los números. */
@Composable
internal fun episodeCode(item: Content.Episode): String? {
    val season = item.seasonNumber ?: return null
    val episode = item.episodeNumber ?: return null
    return stringResource(R.string.episode_code, season, episode)
}

/** "T3 · E1 · Fuego cruzado" (lo que haya de las dos partes). */
@Composable
internal fun episodeLine(item: Content.Episode): String? =
    listOfNotNull(episodeCode(item), episodeName(item)).joinToString(" · ").ifBlank { null }
