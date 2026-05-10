package com.alex.hubplay.data.api

import com.alex.hubplay.data.api.dto.ContinueWatchingResponse
import com.alex.hubplay.data.api.dto.HomeLayoutResponse
import com.alex.hubplay.data.api.dto.ItemDetailResponse
import com.alex.hubplay.data.api.dto.StreamInfoResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

/**
 * Hand-written Retrofit interface for the catalogue / home / stream
 * endpoints the mobile UI consumes today.
 *
 * Every return type is a `…Response` envelope (the server always wraps
 * payloads in `{ "data": ... }`). The repositories peel off the
 * envelope and hand the UI a clean domain type.
 */
interface HubplayApi {

    /** GET /api/v1/me/continue-watching — recently-played, partially-watched items. */
    @GET("me/continue-watching")
    suspend fun getContinueWatching(): ContinueWatchingResponse

    /** GET /api/v1/me/home/layout — per-user rail order + visibility config. */
    @GET("me/home/layout")
    suspend fun getHomeLayout(): HomeLayoutResponse

    /** GET /api/v1/items/{id} — single item details (for the player title bar). */
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
