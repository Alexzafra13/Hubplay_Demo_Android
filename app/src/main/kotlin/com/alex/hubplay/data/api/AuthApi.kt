package com.alex.hubplay.data.api

import com.alex.hubplay.data.api.dto.AuthTokenEnvelopeDto
import com.alex.hubplay.data.api.dto.DevicePollRequest
import com.alex.hubplay.data.api.dto.DeviceStartRequest
import com.alex.hubplay.data.api.dto.DeviceStartResponse
import com.alex.hubplay.data.api.dto.RefreshRequest
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Hand-written Retrofit interface for the authentication endpoints.
 *
 * Why hand-written instead of the openapi-generator output: the
 * generator names inline request schemas with auto-derived class names
 * that change with each `refreshOpenApiSpec` if the spec gets a
 * cosmetic tweak. Owning these signatures locally makes the call sites
 * stable across spec refreshes.
 */
interface AuthApi {

    /**
     * POST /api/v1/auth/refresh — exchange a refresh token for a fresh
     * access+refresh pair. Used by [com.alex.hubplay.data.AuthInterceptor]
     * when an upstream call returns 401.
     */
    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): AuthTokenEnvelopeDto

    /**
     * POST /api/v1/auth/device/start — begin device-code pairing. The
     * server returns a short user_code we display and a long
     * device_code we keep secret to poll with.
     */
    @POST("auth/device/start")
    suspend fun deviceStart(@Body body: DeviceStartRequest): DeviceStartResponse

    /**
     * POST /api/v1/auth/device/poll — poll for the user's approval. Returns
     * the same token envelope shape on success; 202/403 statuses are
     * surfaced via empty/null token fields rather than HTTP errors so
     * we can stay on the loop without juggling exceptions.
     */
    @POST("auth/device/poll")
    suspend fun devicePoll(@Body body: DevicePollRequest): DevicePollResponse
}
