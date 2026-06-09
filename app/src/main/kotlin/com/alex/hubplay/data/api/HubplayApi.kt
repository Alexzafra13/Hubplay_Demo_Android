package com.alex.hubplay.data.api

import com.alex.hubplay.data.api.dto.BulkScheduleRequest
import com.alex.hubplay.data.api.dto.BulkScheduleResponse
import com.alex.hubplay.data.api.dto.ChannelOrderRequest
import com.alex.hubplay.data.api.dto.ChannelVisibilityRequest
import com.alex.hubplay.data.api.dto.ChannelsResponse
import com.alex.hubplay.data.api.dto.ChildrenResponse
import com.alex.hubplay.data.api.dto.CollectionDetailResponse
import com.alex.hubplay.data.api.dto.CollectionsListResponse
import com.alex.hubplay.data.api.dto.ContinueWatchingResponse
import com.alex.hubplay.data.api.dto.FavoriteIdsResponse
import com.alex.hubplay.data.api.dto.FavoriteToggleResponse
import com.alex.hubplay.data.api.dto.GroupsResponse
import com.alex.hubplay.data.api.dto.HomeLayoutResponse
import com.alex.hubplay.data.api.dto.ItemDetailResponse
import com.alex.hubplay.data.api.dto.ItemFavoriteToggleResponse
import com.alex.hubplay.data.api.dto.ItemRecommendationsResponse
import com.alex.hubplay.data.api.dto.LatestResponse
import com.alex.hubplay.data.api.dto.LibrariesResponse
import com.alex.hubplay.data.api.dto.LiveNowResponse
import com.alex.hubplay.data.api.dto.NextUpResponse
import com.alex.hubplay.data.api.dto.PersonDetailResponse
import com.alex.hubplay.data.api.dto.ProfilesResponse
import com.alex.hubplay.data.api.dto.RecommendedResponse
import com.alex.hubplay.data.api.dto.SearchResponse
import com.alex.hubplay.data.api.dto.SwitchProfileRequest
import com.alex.hubplay.data.api.dto.SwitchProfileResponse
import com.alex.hubplay.data.api.dto.StatusResponse
import com.alex.hubplay.data.api.dto.StreamInfoResponse
import com.alex.hubplay.data.api.dto.StudioDetailResponse
import com.alex.hubplay.data.api.dto.TrendingResponse
import com.alex.hubplay.data.api.dto.UpdateProgressRequest
import com.alex.hubplay.data.api.dto.WatchBeaconResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query


/**
 * Hand-written Retrofit interface for the catalogue / home / stream
 * endpoints the mobile UI consumes today.
 *
 * Every return type is a `…Response` envelope (the server always wraps
 * payloads in `{ "data": ... }`). The repositories peel off the
 * envelope and hand the UI a clean domain type.
 */
interface HubplayApi {

    // ─── Home rails ─────────────────────────────────────────────────────────

    /** GET /api/v1/me/continue-watching */
    @GET("me/continue-watching")
    suspend fun getContinueWatching(): ContinueWatchingResponse

    /** GET /api/v1/me/home/layout */
    @GET("me/home/layout")
    suspend fun getHomeLayout(): HomeLayoutResponse

    /** GET /api/v1/me/home/trending — enriched with backdrop / logo / overview. */
    @GET("me/home/trending")
    suspend fun getTrending(@Query("limit") limit: Int = 12): TrendingResponse

    /**
     * GET /api/v1/me/home/recommended — genre-affinity picks seeded from
     * the user's watch history. Powers the third tier of the home hero
     * carousel ("Porque te gusta {{genre}}"). Server default limit is 5,
     * max 20.
     */
    @GET("me/home/recommended")
    suspend fun getRecommended(@Query("limit") limit: Int = 5): RecommendedResponse

    /**
     * GET /api/v1/items/latest — newest items in a library.
     *
     * - When `libraryId` is set + `type=series`, the server returns
     *   activity-aware ordering (latest series with new episodes).
     * - Without `type`, the server mixes movies + series + episodes.
     *   Always pass `type` so episodes don't pollute Latest rails.
     */
    @GET("items/latest")
    suspend fun getLatest(
        @Query("limit")      limit:     Int     = 20,
        @Query("library_id") libraryId: String? = null,
        @Query("type")       type:      String? = null,
    ): LatestResponse

    /**
     * GET /api/v1/items — full catalogue listing with filters, sort
     * and pagination. Used by the dedicated /movies and /series
     * screens; same response shape as `/items/latest`. Pass `type` to
     * narrow to a single content kind and `sort_by` (e.g. "title",
     * "added_at", "year", "community_rating") to control ordering.
     */
    @GET("items")
    suspend fun listItems(
        @Query("type")       type:      String? = null,
        @Query("limit")      limit:     Int     = 60,
        @Query("offset")     offset:    Int     = 0,
        @Query("sort_by")    sortBy:    String? = null,
        @Query("sort_order") sortOrder: String? = null,
    ): LatestResponse

    /** GET /api/v1/libraries — used to map library_id → content_type. */
    @GET("libraries")
    suspend fun getLibraries(): LibrariesResponse

    /** GET /api/v1/me/home/live-now — channels currently broadcasting. */
    @GET("me/home/live-now")
    suspend fun getLiveNow(@Query("limit") limit: Int = 10): LiveNowResponse

    // ─── Item detail / stream ───────────────────────────────────────────────

    /** GET /api/v1/items/{id} */
    @GET("items/{id}")
    suspend fun getItem(@Path("id") itemId: String): ItemDetailResponse

    /**
     * GET /api/v1/items/{id}/children
     *
     * For a series  → returns seasons (type=season, season_number set).
     * For a season  → returns episodes (type=episode, episode_number set).
     * For anything else → empty (or 404).
     */
    @GET("items/{id}/children")
    suspend fun getChildren(@Path("id") itemId: String): ChildrenResponse

    /**
     * GET /api/v1/people/{id} — person profile + deduped filmography.
     * Powers the cast/crew tap-through from the Detail screen.
     */
    @GET("people/{id}")
    suspend fun getPerson(@Path("id") personId: String): PersonDetailResponse

    /**
     * GET /api/v1/items/{id}/recommendations — TMDb "more like this".
     * Powers the "Más como esto" rail on the Detail screen.
     */
    @GET("items/{id}/recommendations")
    suspend fun getRecommendations(@Path("id") itemId: String): ItemRecommendationsResponse

    /**
     * GET /api/v1/studios/{slug} — studio/network profile + its items.
     * Powers the tap-through from the studio chip on the Detail screen.
     */
    @GET("studios/{slug}")
    suspend fun getStudio(@Path("slug") slug: String): StudioDetailResponse

    /**
     * GET /api/v1/me/next-up
     *
     * The user's queued episodes across every series. Used both for the
     * Home "Next Up" rail and as input to the Series resume resolver
     * (matching `series_id` to find which episode plays when the user
     * hits Reproducir on a series detail page).
     */
    @GET("me/next-up")
    suspend fun getNextUp(): NextUpResponse

    /**
     * GET /api/v1/stream/{itemId}/info — server's playback decision for this
     * client. Send the X-Hubplay-Client-Capabilities header so the server
     * can pick direct-play vs. transcode based on our codec support.
     */
    @GET("stream/{itemId}/info")
    suspend fun getStreamInfo(
        @Path("itemId") itemId: String,
        @Header("X-Hubplay-Client-Capabilities") capabilities: String,
    ): StreamInfoResponse

    // ─── IPTV: channels + EPG + favorites ───────────────────────────────────

    /**
     * GET /api/v1/libraries/{id}/channels — every channel in an IPTV library.
     *
     * For authenticated callers the backend already applies the user's
     * personal order + hidden overlay on top of the admin defaults
     * (`GetChannelsForUser` on the server). `include_hidden=true` flips
     * to the personalisation view: ALL rows (including the ones the
     * user has hidden) come back ordered by the user's overlay so the
     * reorder screen can show them.
     */
    @GET("libraries/{id}/channels")
    suspend fun listChannels(
        @Path("id") libraryId: String,
        @Query("active") active: Boolean = true,
        @Query("include_hidden") includeHidden: Boolean = false,
    ): ChannelsResponse

    /**
     * PUT /api/v1/me/iptv/channels/order — atomically replace the
     * caller's full per-user order + hidden overlay.
     *
     * Channels NOT in `ordered_channel_ids` (and not hidden) lose their
     * override row and fall back to the admin default position.
     */
    @PUT("me/iptv/channels/order")
    suspend fun replaceChannelOrder(@Body body: ChannelOrderRequest): StatusResponse

    /** DELETE /api/v1/me/iptv/channels/order — wipe overrides; restore admin defaults. */
    @DELETE("me/iptv/channels/order")
    suspend fun resetChannelOrder(): StatusResponse

    /**
     * PUT /api/v1/me/iptv/channels/{channelId}/visibility — inline
     * single-channel hide toggle. Used when the user taps the eye icon
     * on one row instead of touching the global ordering.
     */
    @PUT("me/iptv/channels/{channelId}/visibility")
    suspend fun setChannelVisibility(
        @Path("channelId") channelId: String,
        @Body body: ChannelVisibilityRequest,
    ): StatusResponse

    /** GET /api/v1/libraries/{id}/channels/groups — group_name strings for filter chips. */
    @GET("libraries/{id}/channels/groups")
    suspend fun listChannelGroups(@Path("id") libraryId: String): GroupsResponse

    /**
     * POST /api/v1/channels/schedule — EPG for many channels in one round-trip.
     *
     * Backend caps at 5_000 channels per request and is happy with either
     * RFC3339 timestamps or relative-hours integers in `from`/`to`. We use
     * the integer form for simplicity; see [BulkScheduleRequest] docs.
     *
     * POST (vs the GET sibling) avoids 414 query-string length errors on
     * libraries with hundreds of channels.
     */
    @POST("channels/schedule")
    suspend fun bulkSchedule(@Body body: BulkScheduleRequest): BulkScheduleResponse

    /** GET /api/v1/favorites/channels/ids — light payload to hydrate the favorites set. */
    @GET("favorites/channels/ids")
    suspend fun listFavoriteChannelIds(): FavoriteIdsResponse

    /** PUT /api/v1/favorites/channels/{channelId} — idempotent add. */
    @PUT("favorites/channels/{channelId}")
    suspend fun addFavoriteChannel(@Path("channelId") channelId: String): FavoriteToggleResponse

    /** DELETE /api/v1/favorites/channels/{channelId} — idempotent remove. */
    @DELETE("favorites/channels/{channelId}")
    suspend fun removeFavoriteChannel(@Path("channelId") channelId: String): FavoriteToggleResponse

    /** POST /api/v1/channels/{channelId}/watch — beacon (records last-watched). */
    @POST("channels/{channelId}/watch")
    suspend fun recordChannelWatch(@Path("channelId") channelId: String): WatchBeaconResponse

    // ─── Playback progress / favourites for items ───────────────────────────

    /**
     * PUT /api/v1/me/progress/{itemId}
     *
     * Persists the current playback position so Continue Watching and
     * Next Up reflect reality across devices. Body carries
     * `position_ticks` in 100-ns ticks (10_000_000 per second — Jellyfin
     * legacy unit the backend uses everywhere) and an optional
     * `completed` flag the client sets true on natural end-of-stream.
     *
     * Returns 204 No Content — `Unit` is the right Retrofit return.
     */
    @PUT("me/progress/{itemId}")
    suspend fun updateProgress(
        @Path("itemId") itemId: String,
        @Body           body:   UpdateProgressRequest,
    )

    /**
     * POST /api/v1/me/progress/{itemId}/played — marks fully played.
     * Bumps `play_count`, clears `position_ticks`, removes the item from
     * Continue Watching. Idempotent.
     */
    @POST("me/progress/{itemId}/played")
    suspend fun markPlayed(@Path("itemId") itemId: String)

    /** POST /api/v1/me/progress/{itemId}/unplayed — undo for the Detail screen. */
    @POST("me/progress/{itemId}/unplayed")
    suspend fun markUnplayed(@Path("itemId") itemId: String)

    /**
     * POST /api/v1/me/progress/{itemId}/favorite
     *
     * Toggles the favourite flag on a movie / series / episode (NOT a
     * live channel — those use the favorites/channels routes). Response
     * shape: `{ data: { item_id, is_favorite } }`.
     */
    @POST("me/progress/{itemId}/favorite")
    suspend fun toggleItemFavorite(@Path("itemId") itemId: String): ItemFavoriteToggleResponse

    // ─── Search ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/items/search?q=… — full-text catalogue search. The
     * backend returns the same ItemSummary shape as `/items` so the UI
     * reuses MediaCard / Detail navigation without translation.
     */
    @GET("items/search")
    suspend fun searchItems(
        @Query("q")     query: String,
        @Query("limit") limit: Int = 60,
    ): SearchResponse

    // ─── Multi-profile ("Who's watching?") ─────────────────────────────────

    /**
     * GET /api/v1/me/profiles — the profile tree under the authenticated
     * account. Returns siblings + parent, each with `has_pin` so the
     * picker can render a lock icon. Solo accounts get a 1-entry list
     * (just the caller). The Android picker auto-skips when ≤ 1.
     */
    @GET("me/profiles")
    suspend fun listProfiles(): ProfilesResponse

    /**
     * POST /api/v1/auth/switch-profile — mint a new token pair for a
     * sibling/parent profile. Caller authenticates with their current
     * Bearer; the server verifies the target shares the same parent
     * tree before issuing fresh tokens. PIN-protected profiles require
     * the matching PIN — wrong PIN returns 401.
     */
    @POST("auth/switch-profile")
    suspend fun switchProfile(@Body body: SwitchProfileRequest): SwitchProfileResponse

    // ─── Movie collections (TMDb sagas) ─────────────────────────────────────

    /**
     * GET /api/v1/collections — every movie collection (saga) with at
     * least one member in the catalogue. Backed by TMDb's
     * `belongs_to_collection` metadata; ordered by member count desc.
     * Powers the Collections tab and its index grid.
     */
    @GET("collections")
    suspend fun listCollections(): CollectionsListResponse

    /**
     * GET /api/v1/collections/{id} — saga detail + member movies in
     * release order. `id` is the canonical `collection:<tmdb_id>`
     * string the index endpoint returns verbatim; Retrofit URL-encodes
     * the colon for us so we don't have to.
     */
    @GET("collections/{id}")
    suspend fun getCollection(@Path("id") id: String): CollectionDetailResponse
}
