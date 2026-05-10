package com.alex.hubplay.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Network-layer DTOs.
 *
 * These mirror the JSON shape returned by the HubPlay backend and are
 * intentionally close to the wire format — string-typed timestamps, nullable
 * everywhere the spec allows it. Mapping to richer in-app types happens in
 * the repositories so the rest of the app doesn't see Json artefacts.
 *
 * `@JsonClass(generateAdapter = true)` triggers Moshi's KSP codegen — no
 * reflection at runtime, smaller R8 footprint, and Proguard-clean.
 */

@JsonClass(generateAdapter = true)
data class ContinueWatchingItemDto(
    val id:           String,
    val name:         String?           = null,
    @Json(name = "type")          val type:        String?  = null,  // movie | episode | series
    @Json(name = "parent_id")     val parentId:    String?  = null,
    @Json(name = "thumb_url")     val thumbUrl:    String?  = null,  // 16:9 (added in May 2026)
    @Json(name = "backdrop_url")  val backdropUrl: String?  = null,
    @Json(name = "poster_url")    val posterUrl:   String?  = null,
    @Json(name = "user_data")     val userData:    UserDataDto? = null,
    @Json(name = "series_name")   val seriesName:  String?  = null,
    @Json(name = "season_index")  val seasonIndex: Int?     = null,
    @Json(name = "episode_index") val episodeIndex: Int?    = null,
    val runtime:      Long?             = null,  // seconds
)

@JsonClass(generateAdapter = true)
data class UserDataDto(
    val played:                Boolean? = null,
    @Json(name = "play_count")            val playCount:        Int?  = null,
    @Json(name = "playback_position")     val playbackPosition: Long? = null,  // seconds
    @Json(name = "last_played_at")        val lastPlayedAt:     String? = null,
    val favorite:              Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class HomeLayoutDto(
    val rails: List<HomeRailDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class HomeRailDto(
    val type:    String,           // continueWatching | nextUp | latest | trending | liveNow
    val visible: Boolean = true,
    val order:   Int     = 0,
)

@JsonClass(generateAdapter = true)
data class ItemDto(
    val id:                String,
    val name:              String?  = null,
    val type:              String?  = null,
    val overview:          String?  = null,
    @Json(name = "thumb_url")     val thumbUrl:    String? = null,
    @Json(name = "backdrop_url")  val backdropUrl: String? = null,
    @Json(name = "poster_url")    val posterUrl:   String? = null,
    val runtime:           Long?    = null,
    @Json(name = "user_data") val userData: UserDataDto? = null,
)

@JsonClass(generateAdapter = true)
data class StreamInfoDto(
    @Json(name = "playback_method")  val playbackMethod: String? = null,  // direct_play | direct_stream | transcode
    @Json(name = "stream_url")       val streamUrl:      String? = null,  // absolute path the player should hit
    @Json(name = "session_token")    val sessionToken:   String? = null,
    @Json(name = "container")        val container:      String? = null,
    @Json(name = "video_codec")      val videoCodec:     String? = null,
    @Json(name = "audio_codec")      val audioCodec:     String? = null,
)
