package com.alex.hubplay.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * One row in the `/collections` index — TMDb saga matched by the
 * scanner against the user's movie libraries. `item_count` is shown
 * on the tile so the user gets a feel for how full a saga is (Lord
 * of the Rings 3, Star Wars 12, MCU 33…). poster / backdrop are
 * optional because brand-new collections may not have artwork yet.
 *
 * Wire shape mirrors openapi `CollectionListEntry`. Server-relative
 * image URLs get absolutized at the repository boundary like every
 * other media URL.
 */
@JsonClass(generateAdapter = true)
data class CollectionListEntryDto(
    val id:                                     String,
    val name:                                   String,
    @Json(name = "item_count") val itemCount:   Int,
    @Json(name = "poster_url") val posterUrl:   String? = null,
    @Json(name = "backdrop_url") val backdropUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class CollectionsListPayload(
    val collections: List<CollectionListEntryDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class CollectionsListResponse(
    val data: CollectionsListPayload? = null,
)

/**
 * `/collections/{id}` payload — the saga hero + its members in
 * release order. We reuse [ItemSummaryDto] for `items` because the
 * server returns the StudioItemRef shape (id / type / title / year /
 * poster_url) which is a subset of ItemSummary fields — every extra
 * field defaults to null on Moshi and we render fine without them.
 */
@JsonClass(generateAdapter = true)
data class CollectionDetailDto(
    val id:                                      String,
    @Json(name = "tmdb_id")  val tmdbId:         Int?    = null,
    val name:                                    String,
    val overview:                                String? = null,
    @Json(name = "poster_url")   val posterUrl:   String? = null,
    @Json(name = "backdrop_url") val backdropUrl: String? = null,
    val items: List<ItemSummaryDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class CollectionDetailResponse(
    val data: CollectionDetailDto? = null,
)

/**
 * Lightweight collection ref carried on `/items/{id}` when a movie
 * belongs to a TMDb saga. Drives the "Parte de: [Saga]" chip on the
 * Detail screen so the user can jump to the full collection in one
 * tap. Backend writes only id + name (see
 * `internal/api/handlers/items.go::attachCollection` — full poster /
 * member list comes from /collections/{id} on demand).
 */
@JsonClass(generateAdapter = true)
data class CollectionRefDto(
    val id:   String,
    val name: String,
)
