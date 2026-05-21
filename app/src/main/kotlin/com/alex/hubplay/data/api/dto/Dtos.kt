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

/**
 * Verified against library.go::itemSummaryResponse() + enrichItemSummaries().
 * Field shape ground truth — the openapi.yaml claims `runtime_minutes`
 * and `poster_image_id`, but the wire actually carries `duration_ticks`
 * (.NET ticks; 10_000_000 per second) and `poster_url` (relative path).
 */
@JsonClass(generateAdapter = true)
data class ItemSummaryDto(
    val id:    String,
    val type:  String?  = null,
    val title: String?  = null,
    val year:  Int?     = null,
    @Json(name = "community_rating") val communityRating: Float? = null,
    @Json(name = "duration_ticks")   val durationTicks:   Long?  = null,
    @Json(name = "poster_url")       val posterUrl:       String? = null,  // relative
    @Json(name = "backdrop_url")     val backdropUrl:     String? = null,  // relative
    @Json(name = "logo_url")         val logoUrl:         String? = null,
    val overview:                                          String? = null,
    val genres:                                            List<String> = emptyList(),
    // Series/season/episode hierarchy
    @Json(name = "parent_id")        val parentId:        String? = null,
    @Json(name = "season_number")    val seasonNumber:    Int?    = null,
    @Json(name = "episode_number")   val episodeNumber:   Int?    = null,
    @Json(name = "user_data")        val userData:        UserDataDto? = null,
)

/**
 * The user_data envelope every item-shaped endpoint carries. Progress
 * is nested under `progress` to mirror the Jellyfin convention the
 * backend follows.
 */
@JsonClass(generateAdapter = true)
data class UserDataDto(
    val played:                       Boolean? = null,
    @Json(name = "play_count")        val playCount:    Int?      = null,
    @Json(name = "is_favorite")       val isFavorite:   Boolean?  = null,
    @Json(name = "last_played_at")    val lastPlayedAt: String?   = null,
    val progress:                     ProgressDto? = null,
)

@JsonClass(generateAdapter = true)
data class ProgressDto(
    @Json(name = "position_ticks")    val positionTicks: Long?  = null,
    val percentage:                                       Float? = null,
)

/**
 * /me/continue-watching response items. Ground truth in
 * progress.go::ContinueWatching(). Different shape than ItemSummary —
 * uses position_ticks at top level, has thumb_url for the 16:9 still
 * on movies, and inlines title/type rather than embedding ItemSummary.
 */
@JsonClass(generateAdapter = true)
data class ContinueWatchingEntryDto(
    val id:    String,
    val type:  String?  = null,
    val title: String?  = null,
    @Json(name = "position_ticks")   val positionTicks: Long?   = null,
    @Json(name = "duration_ticks")   val durationTicks: Long?   = null,
    @Json(name = "last_played_at")   val lastPlayedAt:  String? = null,
    @Json(name = "parent_id")        val parentId:      String? = null,
    /** 16:9 marketing still on movies; null on episodes (use backdrop). */
    @Json(name = "thumb_url")        val thumbUrl:      String? = null,
    @Json(name = "poster_url")       val posterUrl:     String? = null,
    @Json(name = "backdrop_url")     val backdropUrl:   String? = null,
    @Json(name = "logo_url")         val logoUrl:       String? = null,
    /** Set on episodes — series-level art the home rail prefers. */
    @Json(name = "series_id")        val seriesId:      String? = null,
    @Json(name = "series_title")     val seriesTitle:   String? = null,
    @Json(name = "series_year")      val seriesYear:    Int?    = null,
    @Json(name = "season_index")     val seasonIndex:   Int?    = null,
    @Json(name = "episode_index")    val episodeIndex:  Int?    = null,
    @Json(name = "user_data")        val userData:      UserDataDto? = null,
)

/**
 * /items/{id} response. Same envelope drift — verified against
 * items.go::Get + the helpers it composes.
 */
@JsonClass(generateAdapter = true)
data class ItemDetailDto(
    val id:    String,
    val type:  String?  = null,
    val title: String?  = null,
    val year:  Int?     = null,
    @Json(name = "community_rating") val communityRating: Float? = null,
    val overview:                                          String? = null,
    val tagline:                                           String? = null,
    val studio:                                            String? = null,
    @Json(name = "studio_logo_url")  val studioLogoUrl:   String? = null,
    val genres:                                            List<String> = emptyList(),
    @Json(name = "duration_ticks")   val durationTicks:   Long?    = null,
    @Json(name = "poster_url")       val posterUrl:       String? = null,
    @Json(name = "backdrop_url")     val backdropUrl:     String? = null,
    @Json(name = "logo_url")         val logoUrl:         String? = null,
    /** Set only when scanner found an embeddable trailer (YouTube/Vimeo). */
    val trailer:                                           TrailerDto? = null,
    /**
     * TMDb saga this movie belongs to, when the scanner matched one.
     * Backend writes `{ id, name }`; UI renders a "Parte de: <name>"
     * chip on the Detail screen that jumps to /collections/{id}.
     */
    val collection:                                        CollectionRefDto? = null,
    @Json(name = "user_data")        val userData:        UserDataDto? = null,
)

/**
 * Trailer ref carried on /items/{id} for items that have a TMDb-picked
 * trailer. `site` is "YouTube" or "Vimeo" (the picker on the Go side
 * filters anything else); `key` is the platform-specific video id.
 */
@JsonClass(generateAdapter = true)
data class TrailerDto(
    val key:  String,
    val site: String,
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
//
// Ground truth: internal/api/handlers/stream.go::Info() writes
//   { "data": {
//     "item_id":     ...,
//     "method":      "direct_play" | "direct_stream" | "transcode",
//     "video_codec": "h264", "audio_codec": "aac", "container": "mp4",
//     "profiles":    ["1080p","720p",...]
//   } }
//
// It does NOT include `strategy`, `master_playlist_url` or `direct_url` —
// despite what openapi.yaml claims (drift). The client constructs the URLs
// itself (matching web/src/pages/itemDetail/usePlayback.ts), so we only
// need the method to pick the right path.

@JsonClass(generateAdapter = true)
data class StreamInfoDto(
    /** direct_play | direct_stream | transcode */
    val method: String? = null,
    @Json(name = "item_id")     val itemId:     String? = null,
    @Json(name = "video_codec") val videoCodec: String? = null,
    @Json(name = "audio_codec") val audioCodec: String? = null,
    val container: String? = null,
    val profiles:  List<String> = emptyList(),
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

// ─── Series children + Next-Up ──────────────────────────────────────────────
//
// /items/{id}/children — verified against items.go::Children(). Returns the
// raw itemSummaryResponse() shape with backdrop_url / poster_url folded in.
//   - For a series: `type=season`, `season_number` set, `parent_id=seriesId`.
//   - For a season: `type=episode`, `episode_number` set, `parent_id=seasonId`.
// Reusing ItemSummaryDto since it already carries the fields we need.

@JsonClass(generateAdapter = true)
data class ChildrenResponse(
    val data: List<ItemSummaryDto>? = null,
)

/**
 * /me/next-up entry — verified against progress.go::NextUp(). Carries
 * series_id so the resume resolver can match by series without needing
 * a follow-up /items/{episodeId} fetch.
 */
@JsonClass(generateAdapter = true)
data class NextUpItemDto(
    val id: String,
    @Json(name = "episode_title")  val episodeTitle:  String? = null,
    @Json(name = "season_number")  val seasonNumber:  Int? = null,
    @Json(name = "episode_number") val episodeNumber: Int? = null,
    @Json(name = "duration_ticks") val durationTicks: Long? = null,
    @Json(name = "series_title")   val seriesTitle:   String? = null,
    @Json(name = "series_id")      val seriesId:      String? = null,
)

@JsonClass(generateAdapter = true)
data class NextUpResponse(
    val data: List<NextUpItemDto>? = null,
)

// ─── Libraries ──────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class LibraryDto(
    val id:                          String,
    val name:                        String? = null,
    @Json(name = "content_type")     val contentType: String? = null,  // movies | shows | livetv | mixed
)

@JsonClass(generateAdapter = true)
data class LibrariesResponse(
    val data: List<LibraryDto>? = null,
)

// ─── IPTV: channels, groups, schedule, favorites ────────────────────────────
//
// Ground truth: internal/api/handlers/iptv_dto.go::channelDTO,
// iptv_channels.go (ListChannels / Groups / BulkSchedule) and
// iptv_favorites.go (ListFavoriteIDs / Add / Remove / RecordChannelWatch).
//
// Notes on wire shape:
//   - All endpoints respond `{ data: ... }`.
//   - `logo_url` is a server-relative path; absolutize with serverUrl.
//   - `stream_url` is also a server-relative path. List endpoints set it;
//     the detail endpoint omits it (clients build it themselves).
//   - `bulk_schedule` response data is `{ "<channelId>": [program, ...], ... }`
//     — a map, not an array. Programs may be empty for channels with no EPG.

@JsonClass(generateAdapter = true)
data class ChannelDto(
    val id:                                String,
    val name:                              String,
    val number:                            Int     = 0,
    val group:                             String? = null,
    @Json(name = "group_name")             val groupName:     String? = null,
    val category:                          String? = null,
    @Json(name = "logo_url")               val logoUrl:       String? = null,
    @Json(name = "logo_initials")          val logoInitials:  String? = null,
    @Json(name = "logo_bg")                val logoBg:        String? = null,
    @Json(name = "logo_fg")                val logoFg:        String? = null,
    @Json(name = "stream_url")             val streamUrl:     String? = null,
    @Json(name = "library_id")             val libraryId:     String? = null,
    @Json(name = "tvg_id")                 val tvgId:         String? = null,
    val language:                          String? = null,
    val country:                           String? = null,
    @Json(name = "is_active")              val isActive:      Boolean = true,
    @Json(name = "health_status")          val healthStatus:  String? = null,
    /**
     * Only populated when the list is requested with `?include_hidden=true`
     * (the personalisation panel). Bare list calls leave it `false`
     * because the backend has already filtered hidden channels out.
     */
    val hidden:                            Boolean = false,
    @Json(name = "user_position")          val userPosition:  Int? = null,
)

@JsonClass(generateAdapter = true)
data class ChannelsResponse(
    val data: List<ChannelDto>? = null,
)

/**
 * Body for PUT /me/iptv/channels/order. The backend's contract is to
 * REPLACE the full per-user overlay in one go: anything not in
 * [orderedChannelIds] (and not in [hiddenChannelIds]) loses its override
 * row and falls back to the admin default position.
 */
@JsonClass(generateAdapter = true)
data class ChannelOrderRequest(
    @Json(name = "ordered_channel_ids") val orderedChannelIds: List<String>,
    @Json(name = "hidden_channel_ids")  val hiddenChannelIds:  List<String>,
)

/** Body for PUT /me/iptv/channels/{channelId}/visibility. */
@JsonClass(generateAdapter = true)
data class ChannelVisibilityRequest(
    val hidden: Boolean,
)

/** Common `{ "data": { "status": "ok" } }` envelope returned by status endpoints. */
@JsonClass(generateAdapter = true)
data class StatusResponse(
    val data: StatusPayload? = null,
) {
    @JsonClass(generateAdapter = true)
    data class StatusPayload(val status: String? = null)
}

@JsonClass(generateAdapter = true)
data class GroupsResponse(
    /** Plain list of group_name strings as they appear in the M3U. */
    val data: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class EPGProgramDto(
    val id:                                String? = null,
    val title:                             String? = null,
    val description:                       String? = null,
    val category:                          String? = null,
    @Json(name = "icon_url")               val iconUrl:    String? = null,
    /** RFC3339 timestamps (Go time.Time marshals to RFC3339 by default). */
    @Json(name = "start_time")             val startTime:  String? = null,
    @Json(name = "end_time")               val endTime:    String? = null,
)

@JsonClass(generateAdapter = true)
data class BulkScheduleRequest(
    val channels: List<String>,
    /**
     * Either RFC3339 timestamps or bare integers as "hours relative to now"
     * (negative for past). Empty falls back to backend default ±window
     * (-2h .. +24h). We send "-1" / "6" so the response carries `now - 1h`
     * to `now + 6h` — enough for "now and next" plus a small margin.
     */
    val from: String? = null,
    val to:   String? = null,
)

@JsonClass(generateAdapter = true)
data class BulkScheduleResponse(
    /** channelId → programs (sorted by start_time ascending on the server). */
    val data: Map<String, List<EPGProgramDto>>? = null,
)

@JsonClass(generateAdapter = true)
data class FavoriteIdsResponse(
    val data: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class FavoriteTogglePayload(
    @Json(name = "channel_id")  val channelId:  String,
    @Json(name = "is_favorite") val isFavorite: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class FavoriteToggleResponse(
    val data: FavoriteTogglePayload? = null,
)

@JsonClass(generateAdapter = true)
data class WatchBeaconPayload(
    @Json(name = "channel_id")      val channelId:     String,
    @Json(name = "last_watched_at") val lastWatchedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class WatchBeaconResponse(
    val data: WatchBeaconPayload? = null,
)

// ─── Item progress + favourite + search ──────────────────────────────────────

/**
 * Body for `PUT /me/progress/{itemId}`. Ticks are 100-nanosecond units
 * (10_000_000 per second), the Jellyfin/Emby legacy unit the backend
 * stores natively in user_data.position_ticks.
 *
 * `completed` is set to true on natural end-of-stream — the server keeps
 * the row but flips the played flag. The client also POSTs
 * `/me/progress/{id}/played` separately on 95%+ so the play count bumps
 * even when the user navigates away before EOF.
 */
@JsonClass(generateAdapter = true)
data class UpdateProgressRequest(
    @Json(name = "position_ticks") val positionTicks: Long,
    val completed:                                    Boolean? = null,
)

/** Wire shape of `POST /me/progress/{itemId}/favorite`. */
@JsonClass(generateAdapter = true)
data class ItemFavoriteTogglePayload(
    @Json(name = "item_id")     val itemId:     String? = null,
    @Json(name = "is_favorite") val isFavorite: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class ItemFavoriteToggleResponse(
    val data: ItemFavoriteTogglePayload? = null,
)

/** `GET /items/search` reuses the LatestResponse data envelope shape. */
@JsonClass(generateAdapter = true)
data class SearchResponse(
    val data: List<ItemSummaryDto> = emptyList(),
)
