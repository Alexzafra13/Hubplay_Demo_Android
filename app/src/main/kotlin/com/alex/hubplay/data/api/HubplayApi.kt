package com.alex.hubplay.data.api

import com.alex.hubplay.data.api.dto.ContinueWatchingResponse
import com.alex.hubplay.data.api.dto.HomeLayoutResponse
import com.alex.hubplay.data.api.dto.ItemDetailResponse
import com.alex.hubplay.data.api.dto.LatestResponse
import com.alex.hubplay.data.api.dto.LiveNowResponse
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

    /** GET /api/v1/items/latest — newest items across the user's libraries. */
    @GET("items/latest")
    suspend fun getLatest(@Query("limit") limit: Int = 20): LatestResponse

    /** GET /api/v1/me/home/live-now — channels currently broadcasting. */
    @GET("me/home/live-now")
    suspend fun getLiveNow(@Query("limit") limit: Int = 10): LiveNowResponse

    // ─── Item detail / stream ───────────────────────────────────────────────

    /** GET /api/v1/items/{id} */
    @GET("items/{id}")
    suspend fun getItem(@Path("id") itemId: String): ItemDetailResponse

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
