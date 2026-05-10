package com.alex.hubplay.data.api

import com.alex.hubplay.data.api.dto.ContinueWatchingItemDto
import com.alex.hubplay.data.api.dto.HomeLayoutDto
import com.alex.hubplay.data.api.dto.ItemDto
import com.alex.hubplay.data.api.dto.StreamInfoDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

/**
 * Hand-written Retrofit interface for the endpoints the mobile UI consumes
 * today.
 *
 * Why hand-written and not the openapi-generator output? The generator
 * gives us *every* endpoint — useful as we expand, but at ~2900 LOC the
 * generated surface is overkill while we only need 4 calls. Maintaining a
 * focused interface here keeps the autocompletion clean and the binary
 * smaller until we genuinely need everything.
 *
 * Migration path: when we start consuming dozens of endpoints, swap this
 * for the generated `com.alex.hubplay.api.*` interfaces — call sites
 * change `repository.fetchHome()` → `homeApi.getLayout()` only.
 */
interface HubplayApi {

    /** GET /api/v1/me/continue-watching — recently-played, partially-watched items. */
    @GET("me/continue-watching")
    suspend fun getContinueWatching(): List<ContinueWatchingItemDto>

    /** GET /api/v1/me/home/layout — per-user rail order + visibility config. */
    @GET("me/home/layout")
    suspend fun getHomeLayout(): HomeLayoutDto

    /** GET /api/v1/items/{id} — single item details (for the player title bar). */
    @GET("items/{id}")
    suspend fun getItem(@Path("id") itemId: String): ItemDto

    /**
     * GET /api/v1/stream/{itemId}/info — server's playback decision for this
     * client. Send the X-Hubplay-Client-Capabilities header so the server
     * can pick direct-play vs. transcode based on our codec support.
     */
    @GET("stream/{itemId}/info")
    suspend fun getStreamInfo(
        @Path("itemId") itemId: String,
        @Header("X-Hubplay-Client-Capabilities") capabilities: String,
    ): StreamInfoDto
}
