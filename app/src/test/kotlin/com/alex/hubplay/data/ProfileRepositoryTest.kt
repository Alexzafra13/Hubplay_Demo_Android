package com.alex.hubplay.data

import com.alex.hubplay.data.api.HubplayApi
import com.alex.hubplay.data.api.dto.BulkScheduleRequest
import com.alex.hubplay.data.api.dto.BulkScheduleResponse
import com.alex.hubplay.data.api.dto.ChannelOrderRequest
import com.alex.hubplay.data.api.dto.CollectionDetailResponse
import com.alex.hubplay.data.api.dto.CollectionsListResponse
import com.alex.hubplay.data.api.dto.ChannelVisibilityRequest
import com.alex.hubplay.data.api.dto.ChannelsResponse
import com.alex.hubplay.data.api.dto.ChildrenResponse
import com.alex.hubplay.data.api.dto.ContinueWatchingResponse
import com.alex.hubplay.data.api.dto.FavoriteIdsResponse
import com.alex.hubplay.data.api.dto.FavoriteToggleResponse
import com.alex.hubplay.data.api.dto.GroupsResponse
import com.alex.hubplay.data.api.dto.HomeLayoutResponse
import com.alex.hubplay.data.api.dto.ItemDetailResponse
import com.alex.hubplay.data.api.dto.ItemFavoriteToggleResponse
import com.alex.hubplay.data.api.dto.LatestResponse
import com.alex.hubplay.data.api.dto.LibrariesResponse
import com.alex.hubplay.data.api.dto.LiveNowResponse
import com.alex.hubplay.data.api.dto.NextUpResponse
import com.alex.hubplay.data.api.dto.PersonDetailResponse
import com.alex.hubplay.data.api.dto.ProfileSummaryDto
import com.alex.hubplay.data.api.dto.ProfilesResponse
import com.alex.hubplay.data.api.dto.SearchResponse
import com.alex.hubplay.data.api.dto.StatusResponse
import com.alex.hubplay.data.api.dto.StreamInfoResponse
import com.alex.hubplay.data.api.dto.SwitchProfileData
import com.alex.hubplay.data.api.dto.SwitchProfileRequest
import com.alex.hubplay.data.api.dto.SwitchProfileResponse
import com.alex.hubplay.data.api.dto.RecommendedResponse
import com.alex.hubplay.data.api.dto.TrendingResponse
import com.alex.hubplay.data.api.dto.UpdateProgressRequest
import com.alex.hubplay.data.api.dto.WatchBeaconResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Spec around [ProfileRepository] — the layer that backs the
 * "Who's watching?" picker.
 *
 * Each test wires the repo against an in-memory [FakeApi] + capture
 * lambdas so we can assert "exact tokens persisted, exact profile
 * pinned" without touching DataStore / Android Context. The lambda
 * constructor exists for exactly this reason; production wires it
 * through [TokenStore] via the convenience overload.
 */
class ProfileRepositoryTest {

    @Test
    fun `list maps DTO fields into domain Profile and absolutizes avatar URL`() = runTest {
        val api = FakeApi(profiles = listOf(
            ProfileSummaryDto(
                id             = "p1",
                username       = "alice",
                displayName    = "Alice",
                hasPin         = false,
                avatarColor    = "#15803d",
                avatarImageUrl = "/api/v1/users/p1/avatar?v=abc",
            ),
            ProfileSummaryDto(
                id          = "p2",
                username    = "bob",
                displayName = null,  // falls back to username
                hasPin      = true,
            ),
        ))
        val repo = newRepo(api, server = "https://hub.example")

        val result = repo.list() as ProfileListResult.Ok
        val profiles = result.profiles

        assertThat(profiles).hasSize(2)
        assertThat(profiles[0].id).isEqualTo("p1")
        assertThat(profiles[0].displayName).isEqualTo("Alice")
        assertThat(profiles[0].hasPin).isFalse()
        assertThat(profiles[0].avatarColor).isEqualTo("#15803d")
        // Coil needs an absolute URL — server-relative paths get the
        // paired host prepended at the repo boundary, mirroring how
        // HomeRepository handles media URLs.
        assertThat(profiles[0].avatarUrl).isEqualTo("https://hub.example/api/v1/users/p1/avatar?v=abc")
        assertThat(profiles[1].displayName).isEqualTo("bob")  // username fallback
        assertThat(profiles[1].hasPin).isTrue()
        assertThat(profiles[1].avatarUrl).isNull()
    }

    @Test
    fun `list returns Failed on transient api failure so caller can surface retry`() = runTest {
        val api = FakeApi(throwOnList = true)
        val repo = newRepo(api)

        // Transient failures (network blip, 5xx) keep the user on the
        // picker with a Retry. Distinct from Unauthorized (401), which
        // is unrecoverable from this surface and bounces to Login.
        assertThat(repo.list()).isInstanceOf(ProfileListResult.Failed::class.java)
    }

    @Test
    fun `list returns Unauthorized on 401 so caller can bounce to Login`() = runTest {
        val api = FakeApi(listThrows = http401())
        val repo = newRepo(api)

        // The interceptor already tried to refresh and wiped the
        // tokens — surfacing this distinctly lets the VM kick the user
        // straight back to Login instead of looping on Retry.
        assertThat(repo.list()).isEqualTo(ProfileListResult.Unauthorized)
    }

    @Test
    fun `list returns empty Ok when server reports no profiles`() = runTest {
        val api = FakeApi(profiles = emptyList())
        val repo = newRepo(api)

        // Empty means "solo deploy, no picker needed"; Failed/Unauthorized
        // are the error branches. Three distinct outcomes, three distinct
        // call-site behaviours.
        val result = repo.list() as ProfileListResult.Ok
        assertThat(result.profiles).isEmpty()
    }

    @Test
    fun `switch persists new tokens and active profile on success`() = runTest {
        val api = FakeApi(switchResult = SwitchProfileResponse(
            data = SwitchProfileData(
                accessToken  = "new-access",
                refreshToken = "new-refresh",
                userId       = "p2",
            ),
        ))
        val tokens = mutableListOf<Pair<String, String>>()
        val pinned = mutableListOf<Pair<String, String?>>()
        val repo = ProfileRepository(
            api              = api,
            readServerUrl    = { "https://hub.example" },
            storeTokens      = { a, r -> tokens += a to r },
            setActiveProfile = { id, name -> pinned += id to name },
        )

        val result = repo.switch("p2", pin = null, displayName = "Bob")

        assertThat(result).isInstanceOf(SwitchResult.Success::class.java)
        assertThat(tokens).containsExactly("new-access" to "new-refresh")
        assertThat(pinned).containsExactly("p2" to "Bob")
        assertThat(api.switchPayload?.profileId).isEqualTo("p2")
        assertThat(api.switchPayload?.pin).isEqualTo("")
    }

    @Test
    fun `switch returns InvalidPin on 401 and skips persistence`() = runTest {
        val api = FakeApi(switchThrows = http401())
        val tokens = mutableListOf<Pair<String, String>>()
        val repo = ProfileRepository(
            api              = api,
            readServerUrl    = { "https://hub.example" },
            storeTokens      = { a, r -> tokens += a to r },
            setActiveProfile = { _, _ -> },
        )

        val result = repo.switch("p2", pin = "9999", displayName = "Bob")

        assertThat(result).isEqualTo(SwitchResult.InvalidPin)
        // Wrong PIN must NOT swap tokens — otherwise the brute-force
        // attempt would log the attacker in as the previous identity's
        // refreshed session.
        assertThat(tokens).isEmpty()
    }

    @Test
    fun `switch returns Failure when response body lacks tokens`() = runTest {
        val api = FakeApi(switchResult = SwitchProfileResponse(
            data = SwitchProfileData(accessToken = null, refreshToken = null),
        ))
        val repo = newRepo(api)

        val result = repo.switch("p2", pin = null, displayName = "Bob")

        assertThat(result).isInstanceOf(SwitchResult.Failure::class.java)
    }

    @Test
    fun `pinCurrentAsActive only writes the active flag`() = runTest {
        val tokens = mutableListOf<Pair<String, String>>()
        val pinned = mutableListOf<Pair<String, String?>>()
        val repo = ProfileRepository(
            api              = FakeApi(),
            readServerUrl    = { "https://hub.example" },
            storeTokens      = { a, r -> tokens += a to r },
            setActiveProfile = { id, name -> pinned += id to name },
        )

        repo.pinCurrentAsActive("solo-id", "Solo")

        assertThat(tokens).isEmpty()
        assertThat(pinned).containsExactly("solo-id" to "Solo")
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private fun newRepo(api: HubplayApi, server: String = "https://hub.example") = ProfileRepository(
        api              = api,
        readServerUrl    = { server },
        storeTokens      = { _, _ -> },
        setActiveProfile = { _, _ -> },
    )

    private fun http401(): HttpException =
        HttpException(Response.error<Any>(
            401,
            """{"error":{"code":"INVALID_PIN","message":"wrong pin"}}"""
                .toResponseBody("application/json".toMediaTypeOrNull()),
        ))

    private class FakeApi(
        private val profiles:     List<ProfileSummaryDto> = emptyList(),
        private val throwOnList:  Boolean                 = false,
        private val listThrows:   HttpException?          = null,
        private val switchResult: SwitchProfileResponse?  = null,
        private val switchThrows: HttpException?          = null,
    ) : HubplayApi {

        var switchPayload: SwitchProfileRequest? = null
            private set

        override suspend fun listProfiles(): ProfilesResponse {
            listThrows?.let { throw it }
            if (throwOnList) throw RuntimeException("network")
            return ProfilesResponse(data = profiles)
        }

        override suspend fun switchProfile(body: SwitchProfileRequest): SwitchProfileResponse {
            switchPayload = body
            switchThrows?.let { throw it }
            return switchResult ?: SwitchProfileResponse()
        }

        // ─── Unused for these tests ────────────────────────────────────────
        override suspend fun getContinueWatching(): ContinueWatchingResponse = TODO()
        override suspend fun getHomeLayout(): HomeLayoutResponse              = TODO()
        override suspend fun getTrending(limit: Int): TrendingResponse        = TODO()
        override suspend fun getRecommended(limit: Int): RecommendedResponse = TODO()
        override suspend fun getLatest(limit: Int, libraryId: String?, type: String?): LatestResponse = TODO()
        override suspend fun listItems(type: String?, limit: Int, offset: Int, sortBy: String?, sortOrder: String?): LatestResponse = TODO()
        override suspend fun getLibraries(): LibrariesResponse                = TODO()
        override suspend fun getLiveNow(limit: Int): LiveNowResponse          = TODO()
        override suspend fun getItem(itemId: String): ItemDetailResponse      = TODO()
        override suspend fun getChildren(itemId: String): ChildrenResponse    = TODO()
        override suspend fun getPerson(personId: String): PersonDetailResponse = TODO()
        override suspend fun getNextUp(): NextUpResponse                      = TODO()
        override suspend fun getStreamInfo(itemId: String, capabilities: String): StreamInfoResponse = TODO()
        override suspend fun listChannels(libraryId: String, active: Boolean, includeHidden: Boolean): ChannelsResponse = TODO()
        override suspend fun listChannelGroups(libraryId: String): GroupsResponse = TODO()
        override suspend fun replaceChannelOrder(body: ChannelOrderRequest): StatusResponse = TODO()
        override suspend fun resetChannelOrder(): StatusResponse = TODO()
        override suspend fun setChannelVisibility(channelId: String, body: ChannelVisibilityRequest): StatusResponse = TODO()
        override suspend fun bulkSchedule(body: BulkScheduleRequest): BulkScheduleResponse = TODO()
        override suspend fun listFavoriteChannelIds(): FavoriteIdsResponse    = TODO()
        override suspend fun addFavoriteChannel(channelId: String): FavoriteToggleResponse = TODO()
        override suspend fun removeFavoriteChannel(channelId: String): FavoriteToggleResponse = TODO()
        override suspend fun recordChannelWatch(channelId: String): WatchBeaconResponse = TODO()
        override suspend fun updateProgress(itemId: String, body: UpdateProgressRequest) = TODO()
        override suspend fun markPlayed(itemId: String)                       = TODO()
        override suspend fun markUnplayed(itemId: String)                     = TODO()
        override suspend fun toggleItemFavorite(itemId: String): ItemFavoriteToggleResponse = TODO()
        override suspend fun searchItems(query: String, limit: Int): SearchResponse = TODO()
        override suspend fun listCollections(): CollectionsListResponse = TODO()
        override suspend fun getCollection(id: String): CollectionDetailResponse = TODO()
    }
}
