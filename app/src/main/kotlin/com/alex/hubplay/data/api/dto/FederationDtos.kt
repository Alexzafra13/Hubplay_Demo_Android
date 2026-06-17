package com.alex.hubplay.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ─── Federation (peer servers) wire DTOs ──────────────────────────────────────
//
// All responses follow the backend envelope `{ "data": ... }`. Poster URLs
// come back as same-origin proxy paths (the peer's images are fetched through
// our server), so they absolutize against the paired serverUrl just like local
// artwork. Unknown fields (e.g. backdrop_colors, used by the web's aurora) are
// ignored by Moshi — the TV client doesn't need them.

@JsonClass(generateAdapter = true)
data class PeerDto(
    val id:          String,
    @Json(name = "server_uuid") val serverUuid: String? = null,
    val name:        String? = null,
    @Json(name = "base_url")    val baseUrl:    String? = null,
    val status:      String? = null,
    val fingerprint: String? = null,
)

@JsonClass(generateAdapter = true)
data class PeersResponse(val data: List<PeerDto>? = null)

/** One (library × peer) row, flattened across every paired peer. */
@JsonClass(generateAdapter = true)
data class UnifiedLibraryDto(
    @Json(name = "peer_id")      val peerId:      String,
    @Json(name = "peer_name")    val peerName:    String? = null,
    @Json(name = "library_id")   val libraryId:   String,
    @Json(name = "library_name") val libraryName: String? = null,
    @Json(name = "content_type") val contentType: String? = null,
    @Json(name = "can_play")     val canPlay:     Boolean = false,
    @Json(name = "can_download") val canDownload: Boolean = false,
    @Json(name = "can_livetv")   val canLivetv:   Boolean = false,
)

@JsonClass(generateAdapter = true)
data class UnifiedLibrariesResponse(val data: List<UnifiedLibraryDto>? = null)

/** A single item in a peer's library catalogue. */
@JsonClass(generateAdapter = true)
data class RemoteItemDto(
    val id:       String,
    val type:     String? = null,
    val title:    String? = null,
    val year:     Int? = null,
    val overview: String? = null,
    @Json(name = "poster_url") val posterUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class RemoteItemsData(
    val items:      List<RemoteItemDto> = emptyList(),
    val total:      Int = 0,
    @Json(name = "from_cache") val fromCache: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class RemoteItemsResponse(val data: RemoteItemsData? = null)

/** One row of a federated search / "recently added on peers" response. */
@JsonClass(generateAdapter = true)
data class FederationHitDto(
    @Json(name = "peer_id")    val peerId:    String,
    @Json(name = "peer_name")  val peerName:  String? = null,
    @Json(name = "library_id") val libraryId: String? = null,
    val id:       String,
    val type:     String? = null,
    val title:    String? = null,
    val year:     Int? = null,
    val overview: String? = null,
    @Json(name = "poster_url") val posterUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class FederationHitsData(val hits: List<FederationHitDto> = emptyList())

@JsonClass(generateAdapter = true)
data class FederationHitsResponse(val data: FederationHitsData? = null)

/** Cross-peer continue-watching row (carries resume position + progress). */
@JsonClass(generateAdapter = true)
data class PeerContinueDto(
    val id:        String,
    @Json(name = "peer_id")    val peerId:    String,
    @Json(name = "peer_name")  val peerName:  String? = null,
    @Json(name = "library_id") val libraryId: String? = null,
    val type:     String? = null,
    val title:    String? = null,
    val year:     Int? = null,
    @Json(name = "poster_url")      val posterUrl:     String? = null,
    @Json(name = "position_ticks")  val positionTicks: Long? = null,
    @Json(name = "duration_ticks")  val durationTicks: Long? = null,
    val percentage: Float? = null,
)

@JsonClass(generateAdapter = true)
data class PeerContinueResponse(val data: List<PeerContinueDto>? = null)

/** Result of opening a playback session against a peer item. */
@JsonClass(generateAdapter = true)
data class PeerStreamSessionDto(
    val strategy: String? = null,
    @Json(name = "master_playlist_url") val masterPlaylistUrl: String? = null,
    @Json(name = "peer_session_id")     val peerSessionId:     String? = null,
)

@JsonClass(generateAdapter = true)
data class PeerStreamSessionResponse(val data: PeerStreamSessionDto? = null)

/** Body for POST /me/peers/{peer}/items/{item}/progress. */
@JsonClass(generateAdapter = true)
data class PeerProgressRequest(
    @Json(name = "position_ticks") val positionTicks: Long,
    val completed: Boolean,
)
