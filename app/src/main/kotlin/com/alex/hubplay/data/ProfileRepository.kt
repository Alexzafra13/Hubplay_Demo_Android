package com.alex.hubplay.data

import com.alex.hubplay.data.api.HubplayApi
import com.alex.hubplay.data.api.dto.ProfileSummaryDto
import com.alex.hubplay.data.api.dto.SwitchProfileRequest
import retrofit2.HttpException

/**
 * Read + switch the multi-profile tree under the current account.
 *
 * The "Who's watching?" picker calls [list] on mount, then [switch] when
 * the user picks one. Both endpoints are authenticated; the bearer
 * comes from [TokenStore] via the main OkHttp client.
 *
 * On a successful switch the server mints a fresh `access_token` /
 * `refresh_token` for the target profile — we persist them and the
 * `activeProfileId` in the same transaction so the next request the app
 * makes already carries the new identity. Subsequent in-flight requests
 * pick up the new tokens because [AuthInterceptor] reads them from the
 * store on each call.
 */
class ProfileRepository(
    private val api:              HubplayApi,
    private val readServerUrl:    suspend () -> String?,
    private val storeTokens:      suspend (access: String, refresh: String) -> Unit,
    private val setActiveProfile: suspend (id: String, displayName: String?) -> Unit,
) {

    /**
     * Convenience overload — wires the repository against a real
     * [TokenStore]. Keeps the lambda-based primary constructor for
     * tests that don't want to bring up the DataStore-backed store.
     */
    constructor(api: HubplayApi, tokenStore: TokenStore) : this(
        api              = api,
        readServerUrl    = { tokenStore.snapshot().serverUrl },
        storeTokens      = { a, r -> tokenStore.storeTokens(a, r) },
        setActiveProfile = { id, name -> tokenStore.setActiveProfile(id, name) },
    )

    /**
     * Snapshot of the profile tree (parent + children).
     *
     * - `null` → fetch failed (network / server error). Caller should
     *   surface an error state with retry, NOT auto-skip to Home —
     *   we don't know if the account has 1 or 12 profiles.
     * - empty  → server returned a legit empty list. Treat as solo +
     *   nothing to pick, fall through to Home.
     * - 1+     → render the picker.
     */
    suspend fun list(): List<Profile>? {
        val server = readServerUrl().orEmpty()
        val response = runCatching { api.listProfiles() }.getOrNull() ?: return null
        return response.data?.map { it.toDomain(server) }.orEmpty()
    }

    /**
     * Exchange the current bearer for one bound to [profileId]. Returns
     * [SwitchResult.Success] with the chosen profile (label + avatar
     * attribution) so the caller can render confirmation UI without a
     * second fetch. PIN-protected profiles must pass [pin]; the server
     * answers 401 on mismatch and we surface it as
     * [SwitchResult.InvalidPin].
     */
    suspend fun switch(
        profileId:   String,
        pin:         String? = null,
        displayName: String? = null,
    ): SwitchResult {
        val resp = try {
            api.switchProfile(SwitchProfileRequest(profileId = profileId, pin = pin.orEmpty()))
        } catch (e: HttpException) {
            return when (e.code()) {
                401 -> SwitchResult.InvalidPin
                403 -> SwitchResult.NotAllowed
                else -> SwitchResult.Failure(e.message())
            }
        } catch (e: Exception) {
            return SwitchResult.Failure(e.message ?: "network error")
        }
        val payload = resp.data
        val access  = payload?.accessToken
        val refresh = payload?.refreshToken
        if (access.isNullOrBlank() || refresh.isNullOrBlank()) {
            return SwitchResult.Failure("missing tokens in response")
        }
        storeTokens(access, refresh)
        setActiveProfile(profileId, displayName)
        return SwitchResult.Success(profileId = profileId, displayName = displayName)
    }

    /**
     * Persist a profile selection that didn't require a token exchange —
     * used when the picker auto-skips because the tree only has the
     * current user (solo account). The existing bearer is already bound
     * to that profile, so we only need to flip the gating flag.
     */
    suspend fun pinCurrentAsActive(profileId: String, displayName: String?) {
        setActiveProfile(profileId, displayName)
    }

    private fun ProfileSummaryDto.toDomain(server: String) = Profile(
        id           = id,
        displayName  = displayName?.ifBlank { null } ?: username.orEmpty(),
        hasPin       = hasPin,
        avatarColor  = avatarColor,
        avatarUrl    = absolutize(avatarImageUrl, server),
    )

    private fun absolutize(path: String?, server: String): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        if (server.isBlank()) return null
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return "$server$cleanPath"
    }
}

/**
 * Trimmed domain type the picker consumes. We deliberately collapse
 * `username` + `display_name` into a single label since the TV picker
 * only renders one line per tile.
 */
@androidx.compose.runtime.Immutable
data class Profile(
    val id:          String,
    val displayName: String,
    val hasPin:      Boolean,
    val avatarColor: String?,
    val avatarUrl:   String?,
)

sealed class SwitchResult {
    data class Success(val profileId: String, val displayName: String?) : SwitchResult()
    data object InvalidPin : SwitchResult()
    data object NotAllowed : SwitchResult()
    data class Failure(val message: String) : SwitchResult()
}
