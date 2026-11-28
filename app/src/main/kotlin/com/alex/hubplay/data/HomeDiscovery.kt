package com.alex.hubplay.data

import com.alex.hubplay.data.api.dto.BecauseYouWatchedPayload
import com.alex.hubplay.data.api.dto.ChannelDto
import com.alex.hubplay.data.api.dto.RecommendedItemDto

/**
 * "Porque viste X" listo para pintar. Sin título semilla no hay rail: el
 * servidor devuelve `seed = null` cuando el usuario aún no ha terminado nada.
 */
internal fun BecauseYouWatchedPayload.toDomain(map: (RecommendedItemDto) -> Content): BecauseYouWatched? {
    val title = seed?.title?.takeIf { it.isNotBlank() } ?: return null
    return BecauseYouWatched(seedTitle = title, items = items.map(map))
}

/** Un canal favorito como tarjeta del rail de canales (mismo aspecto que "En directo"). */
internal fun ChannelDto.toFavorite(logo: String?): Content.LiveChannel = Content.LiveChannel(
    id           = id,
    title        = cleanChannelName(name),
    posterUrl    = logo,
    backdropUrl  = logo,
    logoUrl      = logo,
    logoInitials = logoInitials,
    logoBg       = logoBg,
    logoFg       = logoFg,
)
