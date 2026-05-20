package com.alex.hubplay.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * One entry in the "Who's watching?" picker.
 *
 * Slim wire payload returned by `GET /me/profiles` (server source:
 * `internal/api/handlers/auth.go → profileListResponse`). Wraps the
 * fields the picker actually needs: identity, has-PIN flag, optional
 * avatar attribution. `password_hash` and `pin_hash` are intentionally
 * never sent.
 */
@JsonClass(generateAdapter = true)
data class ProfileSummaryDto(
    val id:                                            String,
    val username:                                      String? = null,
    @Json(name = "display_name")    val displayName:   String? = null,
    val role:                                          String? = null,
    @Json(name = "is_active")       val isActive:      Boolean? = null,
    @Json(name = "parent_user_id")  val parentUserId:  String? = null,
    @Json(name = "has_pin")         val hasPin:        Boolean = false,
    @Json(name = "avatar_color")    val avatarColor:   String? = null,
    @Json(name = "avatar_image_url") val avatarImageUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class ProfilesResponse(
    val data: List<ProfileSummaryDto>? = null,
)

@JsonClass(generateAdapter = true)
data class SwitchProfileRequest(
    @Json(name = "profile_id")  val profileId:  String,
    val pin:                                    String = "",
    @Json(name = "device_name") val deviceName: String = "HubPlay-Android",
    @Json(name = "device_id")   val deviceId:   String = "android-tv",
)

/**
 * `/auth/switch-profile` returns the same wire shape as `/auth/login`:
 * fresh tokens for the target profile + the user object + the full
 * profile tree. We only consume the tokens here — the picker has already
 * shown the destination profile, so refetching the tree is wasted work.
 */
@JsonClass(generateAdapter = true)
data class SwitchProfileData(
    @Json(name = "access_token")  val accessToken:  String? = null,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    @Json(name = "user_id")       val userId:       String? = null,
    val role:                                       String? = null,
)

@JsonClass(generateAdapter = true)
data class SwitchProfileResponse(
    val data: SwitchProfileData? = null,
)
