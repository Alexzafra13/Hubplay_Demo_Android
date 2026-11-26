package com.alex.hubplay.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ─── Federación: lo que comparten servidores amigos (`/me/peers/…`) ──────────
//
// Forma verificada contra `internal/api/handlers/me/me_peers.go`,
// `me_peer_stream.go` y `me_peer_progress.go` del backend. El servidor solo
// comparte items de primer nivel (películas y series, `parent_id IS NULL`):
// los episodios de una serie compartida no viajan, así que una serie de un
// amigo se puede ver en la ficha pero no reproducir.

/** `GET /me/peers` — servidores emparejados (solo los `paired`). */
@JsonClass(generateAdapter = true)
data class PeerDto(
    val id:     String,
    val name:   String? = null,
    val status: String? = null,
)

@JsonClass(generateAdapter = true)
data class PeersResponse(val data: List<PeerDto>? = null)

/** `GET /me/peers/libraries` — bibliotecas compartidas de todos los amigos. */
@JsonClass(generateAdapter = true)
data class PeerLibraryDto(
    @Json(name = "peer_id")      val peerId:      String,
    @Json(name = "peer_name")    val peerName:    String? = null,
    @Json(name = "library_id")   val libraryId:   String,
    @Json(name = "library_name") val libraryName: String? = null,
    @Json(name = "content_type") val contentType: String? = null,
    @Json(name = "can_play")     val canPlay:     Boolean = false,
)

@JsonClass(generateAdapter = true)
data class PeerLibrariesResponse(val data: List<PeerLibraryDto>? = null)

/**
 * Item de un amigo. `poster_url` es una ruta de NUESTRO servidor (proxy):
 * la app nunca habla con el amigo directamente. Los campos de amigo /
 * biblioteca solo vienen en búsqueda y recientes (en el listado por
 * biblioteca van implícitos en la ruta).
 */
@JsonClass(generateAdapter = true)
data class PeerItemDto(
    val id:       String,
    val type:     String? = null,
    val title:    String? = null,
    val year:     Int?    = null,
    val overview: String? = null,
    @Json(name = "poster_url") val posterUrl: String? = null,
    @Json(name = "peer_id")    val peerId:    String? = null,
    @Json(name = "peer_name")  val peerName:  String? = null,
    @Json(name = "library_id") val libraryId: String? = null,
)

/** `GET /me/peers/{peer}/libraries/{lib}/items` → `{data:{items,total,from_cache}}`. */
@JsonClass(generateAdapter = true)
data class PeerItemsPage(
    val items:     List<PeerItemDto> = emptyList(),
    val total:     Int = 0,
    @Json(name = "from_cache") val fromCache: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class PeerItemsResponse(val data: PeerItemsPage? = null)

/** `GET /me/peers/recent` y `/me/peers/search` → `{data:{hits:[…]}}`. */
@JsonClass(generateAdapter = true)
data class PeerHits(val hits: List<PeerItemDto> = emptyList())

@JsonClass(generateAdapter = true)
data class PeerHitsResponse(val data: PeerHits? = null)

/** `GET /me/peers/continue-watching` — progreso propio sobre items de amigos. */
@JsonClass(generateAdapter = true)
data class PeerContinueDto(
    val id:       String,
    @Json(name = "peer_id")    val peerId:    String,
    @Json(name = "peer_name")  val peerName:  String? = null,
    @Json(name = "library_id") val libraryId: String? = null,
    val type:     String? = null,
    val title:    String? = null,
    val year:     Int?    = null,
    val overview: String? = null,
    @Json(name = "poster_url")     val posterUrl:     String? = null,
    @Json(name = "position_ticks") val positionTicks: Long = 0L,
    @Json(name = "duration_ticks") val durationTicks: Long = 0L,
    val percentage: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class PeerContinueResponse(val data: List<PeerContinueDto>? = null)

/** `POST /me/peers/{peer}/stream/{item}/session`. */
@JsonClass(generateAdapter = true)
data class PeerStreamSessionDto(
    /** direct_play | direct_stream | transcode (decidido por el amigo con nuestras capacidades). */
    val strategy: String? = null,
    /** Ruta de NUESTRO servidor al master HLS (las variantes son relativas). */
    @Json(name = "master_playlist_url") val masterPlaylistUrl: String? = null,
    @Json(name = "peer_session_id")     val peerSessionId:     String? = null,
)

@JsonClass(generateAdapter = true)
data class PeerStreamSessionResponse(val data: PeerStreamSessionDto? = null)

/** `GET /me/peers/{peer}/items/{item}/progress` (todo a cero si no hay nada). */
@JsonClass(generateAdapter = true)
data class PeerProgressDto(
    @Json(name = "position_ticks") val positionTicks: Long = 0L,
    @Json(name = "duration_ticks") val durationTicks: Long = 0L,
    val completed: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class PeerProgressResponse(val data: PeerProgressDto? = null)

/** Cuerpo de `POST /me/peers/{peer}/items/{item}/progress` (204). */
@JsonClass(generateAdapter = true)
data class PeerProgressRequest(
    @Json(name = "position_ticks") val positionTicks: Long,
    @Json(name = "duration_ticks") val durationTicks: Long,
    val completed:                                    Boolean? = null,
)
