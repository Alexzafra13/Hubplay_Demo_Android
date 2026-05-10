package com.alex.hubplay.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTOs for the auth surface.
 *
 * Mirror the JSON shapes the HubPlay backend emits, with all fields
 * nullable where the spec allows it. Bodies are sent with snake_case
 * so we use @Json(name = ...) to bridge into Kotlin's idiomatic
 * camelCase.
 */

@JsonClass(generateAdapter = true)
data class RefreshRequest(
    @Json(name = "refresh_token") val refreshToken: String,
)

@JsonClass(generateAdapter = true)
data class AuthTokenEnvelopeDto(
    @Json(name = "access_token")  val accessToken:  String? = null,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    @Json(name = "expires_at")    val expiresAt:    String? = null,
)

@JsonClass(generateAdapter = true)
data class DeviceStartRequest(
    // The backend accepts an empty body here — included as a field-less
    // class so Retrofit/Moshi serialise it as `{}` rather than the
    // confusing empty string a Unit type would produce.
    @Json(name = "client_name") val clientName: String? = "HubPlay-Android",
)

@JsonClass(generateAdapter = true)
data class DeviceStartResponse(
    @Json(name = "user_code")        val userCode:        String? = null,
    @Json(name = "device_code")      val deviceCode:      String? = null,
    @Json(name = "verification_uri") val verificationUri: String? = null,
    @Json(name = "interval")         val interval:        Int?    = null,  // poll seconds
    @Json(name = "expires_in")       val expiresIn:       Int?    = null,  // total ttl seconds
)

@JsonClass(generateAdapter = true)
data class DevicePollRequest(
    @Json(name = "device_code") val deviceCode: String,
)

/**
 * Same shape as [AuthTokenEnvelopeDto] when the server has approved the
 * pairing; tokens are null while still pending. Kept as its own class
 * so a future `pending=true/denied=true` field can be added without
 * touching refresh.
 */
@JsonClass(generateAdapter = true)
data class DevicePollResponse(
    @Json(name = "access_token")  val accessToken:  String? = null,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    @Json(name = "expires_at")    val expiresAt:    String? = null,
    @Json(name = "pending")       val pending:      Boolean? = null,
    @Json(name = "denied")        val denied:       Boolean? = null,
)
