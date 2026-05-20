package com.alex.hubplay.data

import com.alex.hubplay.data.api.HubplayApi
import com.alex.hubplay.data.api.dto.BulkScheduleRequest
import com.alex.hubplay.data.api.dto.BulkScheduleResponse
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
import com.alex.hubplay.data.api.dto.SearchResponse
import com.alex.hubplay.data.api.dto.StreamInfoResponse
import com.alex.hubplay.data.api.dto.TrendingResponse
import com.alex.hubplay.data.api.dto.UpdateProgressRequest
import com.alex.hubplay.data.api.dto.WatchBeaconResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Spec around how ProgressReporter throttles writes and triggers the
 * one-shot markPlayed. Uses a fake HubplayApi so we can count calls
 * deterministically without spinning up MockWebServer for each test.
 *
 * Note: ProgressReporter throttles on wall-clock time (System.currentTimeMillis)
 * rather than the kotlinx-coroutines TestDispatcher's virtual time, so we
 * sleep real milliseconds in the few tests that exercise the cooldown.
 * Wall-clock dependency is documented in ProgressReporter's KDoc; these
 * tests are the canary if anyone changes the throttle source.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressReporterTest {

    @Test
    fun `first reportPosition writes immediately`() = runTest {
        val api = FakeApi()
        val reporter = ProgressReporter(api, TestScope(coroutineContext), "item-1")

        reporter.reportPosition(positionSec = 30, durationSec = 6000, isPlaying = true)
        advanceUntilIdle()

        assertThat(api.updateProgressCalls.get()).isEqualTo(1)
        assertThat(api.lastPositionTicks).isEqualTo(30L * TICKS_PER_SECOND)
        assertThat(api.markPlayedCalls.get()).isEqualTo(0)
    }

    @Test
    fun `paused player never writes regardless of position changes`() = runTest {
        val api = FakeApi()
        val reporter = ProgressReporter(api, TestScope(coroutineContext), "item-2")

        reporter.reportPosition(positionSec = 30,  durationSec = 6000, isPlaying = false)
        reporter.reportPosition(positionSec = 60,  durationSec = 6000, isPlaying = false)
        reporter.reportPosition(positionSec = 120, durationSec = 6000, isPlaying = false)
        advanceUntilIdle()

        assertThat(api.updateProgressCalls.get()).isEqualTo(0)
    }

    @Test
    fun `flush bypasses throttle and writes the supplied position`() = runTest {
        val api = FakeApi()
        val reporter = ProgressReporter(api, TestScope(coroutineContext), "item-3")

        reporter.reportPosition(positionSec = 10, durationSec = 6000, isPlaying = true)
        advanceUntilIdle()
        reporter.flush(positionSec = 42, completed = false)
        advanceUntilIdle()

        assertThat(api.updateProgressCalls.get()).isEqualTo(2)
        assertThat(api.lastPositionTicks).isEqualTo(42L * TICKS_PER_SECOND)
    }

    @Test
    fun `crossing 95 percent fires markPlayed and skips the position write`() = runTest {
        val api = FakeApi()
        val reporter = ProgressReporter(api, TestScope(coroutineContext), "item-4")

        // duration = 100s; position 95s = 95% → triggers markPlayed branch.
        reporter.reportPosition(positionSec = 95, durationSec = 100, isPlaying = true)
        advanceUntilIdle()

        assertThat(api.markPlayedCalls.get()).isEqualTo(1)
        // The branch returns BEFORE calling updateProgress.
        assertThat(api.updateProgressCalls.get()).isEqualTo(0)
    }

    @Test
    fun `markPlayed fires exactly once even on repeated near-end reports`() = runTest {
        val api = FakeApi()
        val reporter = ProgressReporter(api, TestScope(coroutineContext), "item-5")

        repeat(3) {
            reporter.reportPosition(positionSec = 97, durationSec = 100, isPlaying = true)
            advanceUntilIdle()
        }

        assertThat(api.markPlayedCalls.get()).isEqualTo(1)
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
        override suspend fun getLatest(limit: Int, libraryId: String?, type: String?): LatestResponse = TODO()
        override suspend fun listItems(type: String?, limit: Int, offset: Int, sortBy: String?, sortOrder: String?): LatestResponse = TODO()
        override suspend fun getLibraries(): LibrariesResponse                = TODO()
        override suspend fun getLiveNow(limit: Int): LiveNowResponse          = TODO()
        override suspend fun getItem(itemId: String): ItemDetailResponse      = TODO()
        override suspend fun getChildren(itemId: String): ChildrenResponse    = TODO()
        override suspend fun getNextUp(): NextUpResponse                      = TODO()
        override suspend fun getStreamInfo(itemId: String, capabilities: String): StreamInfoResponse = TODO()
        override suspend fun listChannels(libraryId: String, active: Boolean): ChannelsResponse = TODO()
        override suspend fun listChannelGroups(libraryId: String): GroupsResponse = TODO()
        override suspend fun bulkSchedule(body: BulkScheduleRequest): BulkScheduleResponse = TODO()
        override suspend fun listFavoriteChannelIds(): FavoriteIdsResponse    = TODO()
        override suspend fun addFavoriteChannel(channelId: String): FavoriteToggleResponse = TODO()
        override suspend fun removeFavoriteChannel(channelId: String): FavoriteToggleResponse = TODO()
        override suspend fun recordChannelWatch(channelId: String): WatchBeaconResponse = TODO()
        override suspend fun markUnplayed(itemId: String) = TODO()
        override suspend fun toggleItemFavorite(itemId: String): ItemFavoriteToggleResponse = TODO()
        override suspend fun searchItems(query: String, limit: Int): SearchResponse = TODO()
    }

    companion object {
        private const val TICKS_PER_SECOND = 10_000_000L
    }
}
