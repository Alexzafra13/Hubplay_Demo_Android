package com.alex.hubplay.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Network-layer DTOs for the catalogue / home / stream endpoints.
 *
 * Shape rules HubPlay's backend follows that the previous round of
 * DTOs got wrong:
 *
 *   1. Every response body is `{ "data": <payload> }`. Each top-level
 *      response DTO here is a thin wrapper around its payload type.
 *   2. The "name of the thing" field is `title`, not `name`.
 *   3. There are NO inline image URLs. Items expose `poster_image_id`
 *      and `backdrop_image_id`; the URL is built client-side as
 *      `{serverUrl}/api/v1/images/file/{id}`.
 *   4. Runtime is `runtime_minutes` (int), not seconds.
 *   5. Continue Watching's resume timestamp is `position_seconds`
 *      (float) on the entry itself, not nested in user_data.
 *   6. StreamInfo uses `strategy` (direct_play|direct_stream|transcode)
 *      and exposes `master_playlist_url` + `direct_url`.
 */

// ─── Item summary / detail ───────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class ItemSummaryDto(
    val id:    String,
    val type:  String?  = null,
    val title: String?  = null,
    val year:  Int?     = null,
    val rating: Float?  = null,
    @Json(name = "runtime_minutes")     val runtimeMinutes:   Int?    = null,
    @Json(name = "poster_image_id")     val posterImageId:    String? = null,
    @Json(name = "backdrop_image_id")   val backdropImageId:  String? = null,
    @Json(name = "poster_blurhash")     val posterBlurhash:   String? = null,
    // Series/season/episode hierarchy
    @Json(name = "parent_id")           val parentId:         String? = null,
    @Json(name = "season_number")       val seasonNumber:     Int?    = null,
    @Json(name = "episode_number")      val episodeNumber:    Int?    = null,
    @Json(name = "user_data")           val userData:         UserDataDto? = null,
)

@JsonClass(generateAdapter = true)
data class UserDataDto(
    val played:                                 Boolean? = null,
    @Json(name = "position_seconds")            val positionSeconds: Float? = null,
    @Json(name = "is_favorite")                 val isFavorite:      Boolean? = null,
)

/**
 * Continue Watching entry = ItemSummary + the resume position. The
 * server inlines `position_seconds` and `updated_at` here even though
 * `user_data` would carry the same data — convenience field. Use the
 * top-level one when present, fall back to `user_data.positionSeconds`.
 */
@JsonClass(generateAdapter = true)
data class ContinueWatchingEntryDto(
    val id:    String,
    val type:  String?  = null,
    val title: String?  = null,
    val year:  Int?     = null,
    @Json(name = "runtime_minutes")     val runtimeMinutes:   Int?    = null,
    @Json(name = "poster_image_id")     val posterImageId:    String? = null,
    @Json(name = "backdrop_image_id")   val backdropImageId:  String? = null,
    @Json(name = "parent_id")           val parentId:         String? = null,
    @Json(name = "season_number")       val seasonNumber:     Int?    = null,
    @Json(name = "episode_number")      val episodeNumber:    Int?    = null,
    @Json(name = "user_data")           val userData:         UserDataDto? = null,
    /** Inlined resume position in seconds — convenience over user_data. */
    @Json(name = "position_seconds")    val positionSeconds:  Float?  = null,
    @Json(name = "updated_at")          val updatedAt:        String? = null,
)

@JsonClass(generateAdapter = true)
data class ItemDetailDto(
    val id:    String,
    val type:  String?  = null,
    val title: String?  = null,
    val year:  Int?     = null,
    val rating: Float?  = null,
    val overview:                                String? = null,
    val tagline:                                 String? = null,
    val studio:                                  String? = null,
    @Json(name = "studio_logo_url")     val studioLogoUrl:    String? = null,
    val genres:                                  List<String> = emptyList(),
    @Json(name = "runtime_minutes")     val runtimeMinutes:   Int?    = null,
    @Json(name = "poster_image_id")     val posterImageId:    String? = null,
    @Json(name = "backdrop_image_id")   val backdropImageId:  String? = null,
    @Json(name = "user_data")           val userData:         UserDataDto? = null,
)

// ─── Home layout ─────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class HomeLayoutDto(
    val version:  Int = 0,
    val sections: List<HomeSectionDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class HomeSectionDto(
    val id:                       String,
    val type:                     String,                   // continue_watching | next_up | trending | live_now | latest_in_library
    val visible:                  Boolean = true,
    @Json(name = "library_id")    val libraryId:   String? = null,
    @Json(name = "library_name")  val libraryName: String? = null,
)

// ─── Stream info ─────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class StreamInfoDto(
    /** direct_play | direct_stream | transcode */
    val strategy: String? = null,
    @Json(name = "master_playlist_url") val masterPlaylistUrl: String? = null,
    /** Set only when strategy == direct_play; serves the original file with Range support. */
    @Json(name = "direct_url")          val directUrl:         String? = null,
    @Json(name = "decision_reason")     val decisionReason:    String? = null,
)

// ─── Response envelopes — every endpoint wraps its body in `{ data: ... }` ──

@JsonClass(generateAdapter = true)
data class ContinueWatchingResponse(
    val data: List<ContinueWatchingEntryDto>? = null,
)

@JsonClass(generateAdapter = true)
data class HomeLayoutResponse(
    val data: HomeLayoutDto? = null,
)

@JsonClass(generateAdapter = true)
data class ItemDetailResponse(
    val data: ItemDetailDto? = null,
)

@JsonClass(generateAdapter = true)
data class StreamInfoResponse(
    val data: StreamInfoDto? = null,
)

// ─── Home rails (trending / recommended / live-now / latest) ────────────────
//
// These rails come back enriched: image *URLs* (not just ids), overview,
// genres, etc. So the hero can render rich without a follow-up
// /items/{id} fetch when a card is focused.

@JsonClass(generateAdapter = true)
data class TrendingItemDto(
    val id:    String,
    val type:  String?  = null,
    val title: String?  = null,
    val year:  Int?     = null,
    @Json(name = "community_rating") val communityRating: Float? = null,
    @Json(name = "library_id")       val libraryId:       String? = null,
    @Json(name = "play_count")       val playCount:       Int? = null,
    @Json(name = "poster_url")       val posterUrl:       String? = null,
    @Json(name = "backdrop_url")     val backdropUrl:     String? = null,
    @Json(name = "logo_url")         val logoUrl:         String? = null,
    @Json(name = "poster_blurhash")  val posterBlurhash:  String? = null,
    val overview: String? = null,
    val genres:   List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TrendingPayload(
    val items: List<TrendingItemDto> = emptyList(),
    val total: Int = 0,
)

@JsonClass(generateAdapter = true)
data class TrendingResponse(
    val data: TrendingPayload? = null,
)

/**
 * Verified against the live HubPlay handler at me_home.go::LiveNow().
 * Field names are NOT what the spec says — the backend ships its
 * actual response shape and the openapi.yaml in the repo has drifted.
 * When in doubt, trust the handler over the spec.
 */
@JsonClass(generateAdapter = true)
data class LiveNowChannelDto(
    @Json(name = "channel_id")     val channelId:       String,
    @Json(name = "channel_name")   val channelName:     String?  = null,
    @Json(name = "library_id")     val libraryId:       String?  = null,
    @Json(name = "library_name")   val libraryName:     String?  = null,
    @Json(name = "channel_logo")   val channelLogo:     String?  = null,  // relative path
    @Json(name = "logo_initials")  val logoInitials:    String?  = null,  // fallback when no logo
    @Json(name = "logo_bg")        val logoBg:          String?  = null,
    @Json(name = "logo_fg")        val logoFg:          String?  = null,
    @Json(name = "program_title")  val programTitle:    String?  = null,
    @Json(name = "program_start")  val programStart:    String?  = null,
    @Json(name = "program_end")    val programEnd:      String?  = null,
)

@JsonClass(generateAdapter = true)
data class LiveNowPayload(
    val items: List<LiveNowChannelDto> = emptyList(),
    val total: Int = 0,
)

@JsonClass(generateAdapter = true)
data class LiveNowResponse(
    val data: LiveNowPayload? = null,
)

/**
 * /items/latest returns `{ data: { items, total, offset, limit } }` —
 * verified against library.go::LatestItems(). The openapi.yaml claims
 * `data: array` but the actual handler nests it under `items`.
 */
@JsonClass(generateAdapter = true)
data class LatestPayload(
    val items: List<ItemSummaryDto> = emptyList(),
    val total: Int = 0,
)

@JsonClass(generateAdapter = true)
data class LatestResponse(
    val data: LatestPayload? = null,
)
