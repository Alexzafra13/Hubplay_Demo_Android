package com.alex.hubplay.ui.detail

import com.alex.hubplay.data.CollectionDetail
import com.alex.hubplay.data.CollectionSummary
import com.alex.hubplay.data.Content
import com.alex.hubplay.data.HomeRailConfig
import com.alex.hubplay.data.HomeRepository
import com.alex.hubplay.data.PersonDetail
import com.alex.hubplay.data.StudioDetail
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test

/**
 * Unit tests for [DetailViewModel]. Drives a configurable
 * [FakeDetailRepository] (implementing the [HomeRepository] interface) so
 * we exercise the optimistic mark-watched / favourite logic and the
 * best-effort "Más como esto" load without any Android or network deps.
 *
 * Dispatcher pattern mirrors HomeViewModelTest: Main → an
 * UnconfinedTestDispatcher sharing runTest's scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load exposes the item and its recommendations`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repo = FakeDetailRepository(
            item    = movie("m1"),
            related = listOf(movie("r1"), movie("r2")),
        )
        val vm = DetailViewModel(repo, "m1")
        advanceUntilIdle()

        val ui = vm.ui.value
        assertThat(ui.isLoading).isFalse()
        assertThat(ui.item?.id).isEqualTo("m1")
        assertThat(ui.related).hasSize(2)
    }

    @Test
    fun `load surfaces an error message on failure`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = DetailViewModel(FakeDetailRepository(failDetail = true), "m1")
        advanceUntilIdle()

        val ui = vm.ui.value
        assertThat(ui.item).isNull()
        assertThat(ui.error).isNotNull()
    }

    @Test
    fun `toggleWatched flips optimistically and persists`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repo = FakeDetailRepository(item = movie("m1", watched = false))
        val vm = DetailViewModel(repo, "m1")
        advanceUntilIdle()

        vm.toggleWatched()
        advanceUntilIdle()

        assertThat((vm.ui.value.item as? Content.Movie)?.watched).isEqualTo(true)
        assertThat(repo.watchedCalls).containsExactly("m1" to true)
    }

    @Test
    fun `toggleWatched reverts when the server call fails`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repo = FakeDetailRepository(item = movie("m1", watched = false), failWatched = true)
        val vm = DetailViewModel(repo, "m1")
        advanceUntilIdle()

        vm.toggleWatched()
        advanceUntilIdle()

        // Optimistic flip was rolled back to the original (unwatched) state.
        assertThat((vm.ui.value.item as? Content.Movie)?.watched).isEqualTo(false)
    }

    @Test
    fun `marking watched clears the resume position`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repo = FakeDetailRepository(
            item = movie("m1", watched = false, resumePosSec = 120L, progressPct = 0.5f),
        )
        val vm = DetailViewModel(repo, "m1")
        advanceUntilIdle()

        vm.toggleWatched()
        advanceUntilIdle()

        val m = vm.ui.value.item as? Content.Movie
        assertThat(m?.watched).isEqualTo(true)
        assertThat(m?.resumePosSec).isEqualTo(0L)
        assertThat(m?.progressPct).isEqualTo(0f)
    }

    @Test
    fun `toggleFavorite flips the heart and applies the server value`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repo = FakeDetailRepository(item = movie("m1"), favoriteResult = true)
        val vm = DetailViewModel(repo, "m1")
        advanceUntilIdle()

        vm.toggleFavorite()
        advanceUntilIdle()

        assertThat((vm.ui.value.item as? Content.Movie)?.isFavorite).isEqualTo(true)
    }

    private fun movie(
        id:           String,
        watched:      Boolean = false,
        resumePosSec: Long    = 0L,
        progressPct:  Float   = 0f,
    ) = Content.Movie(
        id           = id,
        title        = "Movie $id",
        watched      = watched,
        resumePosSec = resumePosSec,
        progressPct  = progressPct,
    )

    /**
     * Minimal [HomeRepository] fake. Only the four methods DetailViewModel
     * touches are meaningful; the rest return empty / throw so an accidental
     * new dependency surfaces loudly in a test rather than silently passing.
     */
    private class FakeDetailRepository(
        private val item:           Content?       = null,
        private val related:        List<Content>  = emptyList(),
        private val favoriteResult: Boolean        = false,
        private val failDetail:     Boolean        = false,
        private val failWatched:    Boolean        = false,
    ) : HomeRepository {
        val watchedCalls = mutableListOf<Pair<String, Boolean>>()

        override suspend fun fetchItemDetail(itemId: String): Content {
            if (failDetail) error("boom")
            return item ?: error("no item configured")
        }

        override suspend fun fetchRecommendations(itemId: String): List<Content> = related

        override suspend fun toggleItemFavorite(itemId: String): Boolean = favoriteResult

        override suspend fun setItemWatched(itemId: String, watched: Boolean) {
            watchedCalls += itemId to watched
            if (failWatched) error("boom")
        }

        override suspend fun fetchPerson(personId: String): PersonDetail = error("unused")
        override suspend fun fetchStudio(slug: String): StudioDetail = error("unused")
        override suspend fun fetchContinueWatching(): List<Content.Resumable> = emptyList()
        override suspend fun fetchTrending(limit: Int): List<Content> = emptyList()
        override suspend fun fetchRecommended(limit: Int): List<Content> = emptyList()
        override suspend fun fetchLatest(libraryId: String?, type: String?, limit: Int): List<Content> = emptyList()
        override suspend fun fetchLibraries(): Map<String, String> = emptyMap()
        override suspend fun fetchLiveNow(limit: Int): List<Content.LiveChannel> = emptyList()
        override suspend fun fetchHomeLayout(): List<HomeRailConfig> = emptyList()
        override suspend fun fetchChildren(parentId: String): List<Content> = emptyList()
        override suspend fun fetchNextUp(): List<Content.Episode> = emptyList()
        override suspend fun fetchCatalogue(
            type: String,
            limit: Int,
            offset: Int,
            sortBy: String,
            sortOrder: String,
        ): List<Content> = emptyList()
        override suspend fun fetchCollections(): List<CollectionSummary> = emptyList()
        override suspend fun fetchCollectionDetail(id: String): CollectionDetail = error("unused")
        override suspend fun searchItems(query: String, limit: Int, offset: Int): List<Content> = emptyList()
    }
}
