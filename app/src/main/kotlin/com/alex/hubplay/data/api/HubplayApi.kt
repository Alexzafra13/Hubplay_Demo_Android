package com.alex.hubplay.data.api

import com.alex.hubplay.data.api.dto.ChildrenResponse
import com.alex.hubplay.data.api.dto.ContinueWatchingResponse
import com.alex.hubplay.data.api.dto.HomeLayoutResponse
import com.alex.hubplay.data.api.dto.ItemDetailResponse
import com.alex.hubplay.data.api.dto.LatestResponse
import com.alex.hubplay.data.api.dto.LibrariesResponse
import com.alex.hubplay.data.api.dto.LiveNowResponse
import com.alex.hubplay.data.api.dto.NextUpResponse
import com.alex.hubplay.data.api.dto.StreamInfoResponse
import com.alex.hubplay.data.api.dto.TrendingResponse
import retrofit2.http.GET
import retrofit2.http.Header
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
}
