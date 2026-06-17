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
import com.alex.hubplay.data.api.dto.ItemRecommendationsResponse
import com.alex.hubplay.data.api.dto.LatestResponse
import com.alex.hubplay.data.api.dto.LibrariesResponse
import com.alex.hubplay.data.api.dto.LiveNowResponse
import com.alex.hubplay.data.api.dto.NextUpResponse
import com.alex.hubplay.data.api.dto.PersonDetailResponse
import com.alex.hubplay.data.api.dto.ProfilesResponse
import com.alex.hubplay.data.api.dto.SearchResponse
import com.alex.hubplay.data.api.dto.SwitchProfileRequest
import com.alex.hubplay.data.api.dto.SwitchProfileResponse
import com.alex.hubplay.data.api.dto.StatusResponse
import com.alex.hubplay.data.api.dto.StreamInfoResponse
import com.alex.hubplay.data.api.dto.StudioDetailResponse
import com.alex.hubplay.data.api.dto.RecommendedResponse
import com.alex.hubplay.data.api.dto.TrendingResponse
import com.alex.hubplay.data.api.dto.UpdateProgressRequest
import com.alex.hubplay.data.api.dto.WatchBeaconResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Spec around how ProgressReporter throttles writes and triggers the
 * one-shot markPlayed. Uses a fake HubplayApi so we can count calls
 * deterministically without spinning up MockWebServer for each test.
 *
 * Dispatcher choice — runTest with [UnconfinedTestDispatcher]:
 *
 *  The first version of these tests used the default StandardTestDispatcher
 *  + backgroundScope + advanceUntilIdle(). Every assertion that expected
 *  a write count > 0 failed with `expected: N, but was: 0` — the
 *  scheduler wasn't running the secondary launches inside the
 *  Reporter's `launchUnique` (an outer scope.launch that itself does
 *  scope.launch inside a Mutex.withLock). The chain doesn't survive
 *  the standard scheduler's lazy advancement reliably.
 *
 *  UnconfinedTestDispatcher runs every launch eagerly until first
 *  suspension. The Reporter's chain has no real suspensions in the
 *  FakeApi path, so everything completes inline before assertPosition
 *  returns. No `advanceUntilIdle` dance, no flaky ordering.
 *
 *  Trade-off: we lose the ability to test "throttle window > 10 s
 *  without 10 s of real wall clock" — but the throttle logic uses
 *  System.currentTimeMillis() not the scheduler, so virtual time
 *  wouldn't have helped anyway. Those tests would need to inject a
 *  Clock abstraction; deferred until we actually need them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressReporterTest {

    @Test
    fun `first reportPosition writes immediately`() = runTest(UnconfinedTestDispatcher()) {
        val api = FakeApi()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val reporter = ProgressReporter(api, scope, "item-1")

        reporter.reportPosition(positionSec = 30, durationSec = 6000, isPlaying = true)

        assertThat(api.updateProgressCalls.get()).isEqualTo(1)
        assertThat(api.lastPositionTicks).isEqualTo(30L * TICKS_PER_SECOND)
        assertThat(api.markPlayedCalls.get()).isEqualTo(0)
        scope.cancel()
    }

    @Test
    fun `paused player never writes regardless of position changes`() = runTest(UnconfinedTestDispatcher()) {
        val api = FakeApi()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val reporter = ProgressReporter(api, scope, "item-2")

        reporter.reportPosition(positionSec = 30,  durationSec = 6000, isPlaying = false)
        reporter.reportPosition(positionSec = 60,  durationSec = 6000, isPlaying = false)
        reporter.reportPosition(positionSec = 120, durationSec = 6000, isPlaying = false)

        assertThat(api.updateProgressCalls.get()).isEqualTo(0)
        scope.cancel()
    }

    @Test
    fun `flush bypasses throttle and writes the supplied position`() = runTest(UnconfinedTestDispatcher()) {
        val api = FakeApi()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val reporter = ProgressReporter(api, scope, "item-3")

        reporter.reportPosition(positionSec = 10, durationSec = 6000, isPlaying = true)
        reporter.flush(positionSec = 42, completed = false)

        assertThat(api.updateProgressCalls.get()).isEqualTo(2)
        assertThat(api.lastPositionTicks).isEqualTo(42L * TICKS_PER_SECOND)
        scope.cancel()
    }

    @Test
    fun `crossing 95 percent fires markPlayed and skips the position write`() = runTest(UnconfinedTestDispatcher()) {
        val api = FakeApi()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val reporter = ProgressReporter(api, scope, "item-4")

        // duration = 100s; position 95s = 95% → triggers markPlayed branch.
        reporter.reportPosition(positionSec = 95, durationSec = 100, isPlaying = true)

        assertThat(api.markPlayedCalls.get()).isEqualTo(1)
        // The branch returns BEFORE calling updateProgress.
        assertThat(api.updateProgressCalls.get()).isEqualTo(0)
        scope.cancel()
    }

    @Test
    fun `markPlayed fires exactly once even on repeated near-end reports`() = runTest(UnconfinedTestDispatcher()) {
        val api = FakeApi()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val reporter = ProgressReporter(api, scope, "item-5")

        repeat(3) {
            reporter.reportPosition(positionSec = 97, durationSec = 100, isPlaying = true)
        }

        assertThat(api.markPlayedCalls.get()).isEqualTo(1)
        scope.cancel()
    }

    // ─── Throttle window tests (require a controllable clock) ──────────────
    //
    // Previously these couldn't exist because the Reporter read
    // System.currentTimeMillis() directly. With the TimeSource injection
    // we can now advance virtual wall clock between calls and prove the
    // throttle behaves correctly — both that it SUPPRESSES writes inside
    // the window and that it ALLOWS them once the window elapses.

    @Test
    fun `second report within the 10s throttle window is suppressed`() = runTest(UnconfinedTestDispatcher()) {
        val api = FakeApi()
        val time = MutableTimeSource(initialMs = 1_000_000L)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val reporter = ProgressReporter(api, scope, "item-6", time)

        reporter.reportPosition(positionSec = 30, durationSec = 6000, isPlaying = true)
        assertThat(api.updateProgressCalls.get()).isEqualTo(1)

        // 5 s later → inside the 10 s cooldown → should NOT write.
        time.advanceMs(5_000L)
        reporter.reportPosition(positionSec = 35, durationSec = 6000, isPlaying = true)
        assertThat(api.updateProgressCalls.get()).isEqualTo(1)

        scope.cancel()
    }

    @Test
    fun `report past the 10s throttle window writes again`() = runTest(UnconfinedTestDispatcher()) {
        val api = FakeApi()
        val time = MutableTimeSource(initialMs = 1_000_000L)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val reporter = ProgressReporter(api, scope, "item-7", time)

        reporter.reportPosition(positionSec = 30, durationSec = 6000, isPlaying = true)
        assertThat(api.updateProgressCalls.get()).isEqualTo(1)

        // 11 s later → throttle elapsed → write should happen.
        time.advanceMs(11_000L)
        reporter.reportPosition(positionSec = 41, durationSec = 6000, isPlaying = true)
        assertThat(api.updateProgressCalls.get()).isEqualTo(2)
        assertThat(api.lastPositionTicks).isEqualTo(41L * TICKS_PER_SECOND)

        scope.cancel()
    }

    /** Test double: a TimeSource whose value the test advances manually. */
    private class MutableTimeSource(initialMs: Long) : TimeSource {
        @Volatile private var nowMs: Long = initialMs
        override fun nowMs(): Long = nowMs
        fun advanceMs(delta: Long) { nowMs += delta }
    }

    /**
     * Minimal HubplayApi that counts the two endpoints under test and
     * defaults the rest to "throw if anyone calls it". Defining the
     * unused endpoints as TODO() keeps the test obviously broken if a
     * future change accidentally pulls in another endpoint.
     */
    private class FakeApi : HubplayApi {
        val updateProgressCalls = AtomicInteger(0)
        val markPlayedCalls     = AtomicInteger(0)
        @Volatile var lastPositionTicks: Long = -1L

        override suspend fun updateProgress(itemId: String, body: UpdateProgressRequest) {
            updateProgressCalls.incrementAndGet()
            lastPositionTicks = body.positionTicks
        }
        override suspend fun markPlayed(itemId: String) {
            markPlayedCalls.incrementAndGet()
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
        override suspend fun getRecommendations(itemId: String): ItemRecommendationsResponse = TODO()
        override suspend fun getStudio(slug: String): StudioDetailResponse = TODO()
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
        override suspend fun listRecentChannels(limit: Int): ChannelsResponse = TODO()
        override suspend fun markUnplayed(itemId: String) = TODO()
        override suspend fun toggleItemFavorite(itemId: String): ItemFavoriteToggleResponse = TODO()
        override suspend fun searchItems(query: String, limit: Int, offset: Int): SearchResponse = TODO()
        override suspend fun listProfiles(): ProfilesResponse                 = TODO()
        override suspend fun switchProfile(body: SwitchProfileRequest): SwitchProfileResponse = TODO()
        override suspend fun listCollections(): CollectionsListResponse = TODO()
        override suspend fun getCollection(id: String): CollectionDetailResponse = TODO()
    }

    companion object {
        private const val TICKS_PER_SECOND = 10_000_000L
    }
}
