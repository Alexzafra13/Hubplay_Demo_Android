package com.alex.hubplay.ui.home

import com.alex.hubplay.data.CollectionDetail
import com.alex.hubplay.data.CollectionSummary
import com.alex.hubplay.data.Content
import com.alex.hubplay.data.HomeRailConfig
import com.alex.hubplay.data.HomeRepository
import com.alex.hubplay.data.MeEvent
import com.alex.hubplay.data.PersonDetail
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test

/**
 * Unit tests for [HomeViewModel]. Uses a [FakeHomeRepository] so we can
 * control what each fetch returns (or throws) without hitting the network.
 *
 * Dispatcher: [Dispatchers.Main] is swapped to an [UnconfinedTestDispatcher]
 * that **shares the scheduler** with [runTest]'s TestScope. This means
 * `advanceUntilIdle()` advances the same virtual clock that drives
 * `viewModelScope` — debounce, delay, and other time-based operators
 * work correctly without separate time sources fighting each other.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh populates ui with data from repository`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = viewModel(FakeHomeRepository(
            trending = listOf(movie("t1"), movie("t2")),
            continueWatching = listOf(movie("cw1")),
        ))
        advanceUntilIdle()

        val ui = vm.ui.value
        assertThat(ui.isLoading).isFalse()
        assertThat(ui.error).isNull()
        assertThat(ui.data.trending).hasSize(2)
        assertThat(ui.data.continueWatching).hasSize(1)
    }

    @Test
    fun `refresh shows error when all fetches fail`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = viewModel(FakeHomeRepository(throwOnAll = true))
        advanceUntilIdle()

        val ui = vm.ui.value
        assertThat(ui.isLoading).isFalse()
        assertThat(ui.error).isNotNull()
        assertThat(ui.error).contains("conexión")
    }

    @Test
    fun `refresh shows partial data when some fetches fail`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = viewModel(FakeHomeRepository(
            trending = listOf(movie("t1")),
            throwOnContinueWatching = true,
        ))
        advanceUntilIdle()

        val ui = vm.ui.value
        assertThat(ui.isLoading).isFalse()
        assertThat(ui.error).isNull()
        assertThat(ui.data.trending).hasSize(1)
        assertThat(ui.data.continueWatching).isEmpty()
    }

    @Test
    fun `hero is first 5 trending items`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val trending = (1..8).map { movie("t$it") }
        val vm = viewModel(FakeHomeRepository(trending = trending))
        advanceUntilIdle()

        assertThat(vm.ui.value.data.hero).hasSize(5)
        assertThat(vm.ui.value.data.hero.map { it.id })
            .containsExactly("t1", "t2", "t3", "t4", "t5").inOrder()
    }

    @Test
    fun `first onCardFocused is consumed by the gate`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = viewModel(FakeHomeRepository(trending = listOf(movie("t1"))))
        advanceUntilIdle()

        vm.onCardFocused(movie("t1"))
        advanceUntilIdle()

        assertThat(vm.focusedItemForUi.value).isNull()
    }

    @Test
    fun `resetFirstFocusGate re-arms the gate`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = viewModel(FakeHomeRepository(trending = listOf(movie("t1"))))
        advanceUntilIdle()

        vm.onCardFocused(movie("gate"))
        advanceUntilIdle()
        vm.resetFirstFocusGate()
        vm.onCardFocused(movie("after-reset"))
        advanceUntilIdle()

        assertThat(vm.focusedItemForUi.value).isNull()
    }

    @Test
    fun `saveScrollSnapshot persists and exposes snapshot`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = viewModel(FakeHomeRepository())
        advanceUntilIdle()

        val focused = mapOf("rail-1" to "item-a", "rail-2" to "item-b")
        vm.saveScrollSnapshot(railIndex = 2, focusedItemIdByRail = focused)

        val snap = vm.scrollSnapshot.value
        assertThat(snap.railIndex).isEqualTo(2)
        assertThat(snap.focusedItemIdByRail).isEqualTo(focused)
    }

    @Test
    fun `progress event refreshes only the continue-watching rail`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val events = MutableSharedFlow<MeEvent>(extraBufferCapacity = 8)
        val repo = FakeHomeRepository(
            trending = listOf(movie("t1"), movie("t2")),
            continueWatching = listOf(movie("cw1")),
        )
        val vm = HomeViewModel(repo, events)
        advanceUntilIdle()
        assertThat(vm.ui.value.data.continueWatching).hasSize(1)
        val trendingBefore = vm.ui.value.data.trending

        // El servidor pasa a tener 2 items en Continuar viendo; un
        // ProgressUpdated debe refrescar SOLO ese rail tras el debounce,
        // dejando trending intacto (sin recargar el Home entero).
        repo.continueWatchingOverride = listOf(movie("cw1"), movie("cw2"))
        events.tryEmit(MeEvent.ProgressUpdated(itemId = "cw1", positionTicks = 1L, completed = false))
        advanceUntilIdle()

        assertThat(vm.ui.value.data.continueWatching).hasSize(2)
        assertThat(vm.ui.value.data.trending).isEqualTo(trendingBefore)
    }

    @Test
    fun `onTrailerTimeUpdate updates trailerCurrentTimeSec`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = viewModel(FakeHomeRepository())
        advanceUntilIdle()

        vm.onTrailerTimeUpdate(42)
        assertThat(vm.trailerCurrentTimeSec.value).isEqualTo(42L)
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private fun viewModel(repo: HomeRepository) =
        HomeViewModel(repo, emptyFlow<MeEvent>())

    private fun movie(id: String): Content.Movie =
        Content.Movie(id = id, title = "Title $id")

    private class FakeHomeRepository(
        private val trending: List<Content> = emptyList(),
        private val continueWatching: List<Content.Resumable> = emptyList(),
        private val nextUp: List<Content.Episode> = emptyList(),
        private val liveNow: List<Content.LiveChannel> = emptyList(),
        private val layout: List<HomeRailConfig> = emptyList(),
        private val libraries: Map<String, String> = emptyMap(),
        private val throwOnAll: Boolean = false,
        private val throwOnContinueWatching: Boolean = false,
    ) : HomeRepository {

        /** Permite cambiar el rail de Continuar viendo tras construir el
         *  fake, para verificar el refresco selectivo por SSE. */
        var continueWatchingOverride: List<Content.Resumable>? = null

        override suspend fun fetchTrending(limit: Int): List<Content> {
            if (throwOnAll) throw RuntimeException("network")
            return trending
        }
        override suspend fun fetchRecommended(limit: Int): List<Content> {
            if (throwOnAll) throw RuntimeException("network")
            return emptyList()
        }
        override suspend fun fetchContinueWatching(): List<Content.Resumable> {
            if (throwOnAll || throwOnContinueWatching) throw RuntimeException("network")
            return continueWatchingOverride ?: continueWatching
        }
        override suspend fun fetchNextUp(): List<Content.Episode> {
            if (throwOnAll) throw RuntimeException("network")
            return nextUp
        }
        override suspend fun fetchLiveNow(limit: Int): List<Content.LiveChannel> {
            if (throwOnAll) throw RuntimeException("network")
            return liveNow
        }
        override suspend fun fetchHomeLayout(): List<HomeRailConfig> {
            if (throwOnAll) throw RuntimeException("network")
            return layout
        }
        override suspend fun fetchLibraries(): Map<String, String> {
            if (throwOnAll) throw RuntimeException("network")
            return libraries
        }
        override suspend fun fetchLatest(libraryId: String?, type: String?, limit: Int): List<Content> {
            if (throwOnAll) throw RuntimeException("network")
            return emptyList()
        }
        override suspend fun fetchItemDetail(itemId: String): Content {
            if (throwOnAll) throw RuntimeException("network")
            return trending.firstOrNull { it.id == itemId }
                ?: throw RuntimeException("not found")
        }
        override suspend fun fetchChildren(parentId: String) = emptyList<Content>()
        override suspend fun fetchCatalogue(type: String, limit: Int, offset: Int, sortBy: String, sortOrder: String) = emptyList<Content>()
        override suspend fun fetchCollections() = emptyList<CollectionSummary>()
        override suspend fun fetchCollectionDetail(id: String): CollectionDetail = throw RuntimeException("unused")
        override suspend fun toggleItemFavorite(itemId: String) = false
        override suspend fun setItemWatched(itemId: String, watched: Boolean) {}
        override suspend fun fetchPerson(personId: String): PersonDetail = throw RuntimeException("unused")
        override suspend fun fetchRecommendations(itemId: String) = emptyList<Content>()
        override suspend fun searchItems(query: String, limit: Int) = emptyList<Content>()
    }
}
