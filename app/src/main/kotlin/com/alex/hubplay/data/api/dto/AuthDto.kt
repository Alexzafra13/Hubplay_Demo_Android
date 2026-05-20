package com.alex.hubplay.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTOs for the auth surface.
 *
 * All HubPlay JSON responses follow the envelope convention
 * `{ "data": <payload> }` (and `{ "error": { code, message } }` for
 * error bodies, which Retrofit surfaces as HTTPException). Each
 * response DTO here is a thin wrapper around its payload type so the
 * call site reads `resp.data?.userCode` rather than struggling with
 * an inline anonymous shape.
 */

// ─── Requests ────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class RefreshRequest(
    @Json(name = "refresh_token") val refreshToken: String,
)

@JsonClass(generateAdapter = true)
data class DeviceStartRequest(
    /**
     * Friendly label the operator sees in their session list. Backend
     * spec calls this `device_name` (NOT `client_name`); mismatching it
     * means the server logs the device as "Unknown".
     */
    @Json(name = "device_name") val deviceName: String? = "HubPlay-Android",
)

@JsonClass(generateAdapter = true)
data class DevicePollRequest(
    @Json(name = "device_code") val deviceCode: String,
)

// ─── Response payloads ───────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class AuthToken(
    @Json(name = "access_token")  val accessToken:  String? = null,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    @Json(name = "expires_at")    val expiresAt:    String? = null,
)

@JsonClass(generateAdapter = true)
data class DeviceStartData(
    @Json(name = "user_code")        val userCode:        String? = null,
    @Json(name = "device_code")      val deviceCode:      String? = null,
    /**
     * Where the operator should go to type the code. Backend prefers
     * `verification_url`; `verification_uri` is the RFC 8628 alias and
     * may be absent. Pick whichever has a value.
     */
    @Json(name = "verification_url") val verificationUrl: String? = null,
    @Json(name = "verification_uri") val verificationUri: String? = null,
    /**
     * RFC 8628 §3.3.1 — verification URL with the `user_code` already
     * embedded as `?code=…`. This is exactly what the QR code in the
     * login screen encodes; the web client's QRScannerModal extracts
     * the `code` query param and POSTs `/auth/device/approve`.
     *
     * Falls back to building it client-side from `verification_url` +
     * `user_code` if the server doesn't return it (older deployments).
     */
    @Json(name = "verification_uri_complete") val verificationUriComplete: String? = null,
    @Json(name = "expires_in")       val expiresIn:       Int?    = null,
    val interval:                                          Int?    = null,
)

// ─── Response envelopes (always `{ data: ... }`) ────────────────────────────

@JsonClass(generateAdapter = true)
data class AuthTokenEnvelopeDto(
    val data: AuthToken? = null,
)

@JsonClass(generateAdapter = true)
data class DeviceStartResponse(
    val data: DeviceStartData? = null,
)

/**
 * /auth/device/poll returns 200 with the same shape as AuthTokenEnvelope
 * once the operator approves. While pending the server returns 4xx with
 * an ErrorEnvelope, which Retrofit surfaces as HttpException and we
 * catch as "still pending."
 */
@JsonClass(generateAdapter = true)
data class DevicePollResponse(
    val data: AuthToken? = null,
)
