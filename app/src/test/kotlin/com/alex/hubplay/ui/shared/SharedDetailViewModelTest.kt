package com.alex.hubplay.ui.shared

import com.alex.hubplay.data.FakePeersApi
import com.alex.hubplay.data.MediaKind
import com.alex.hubplay.data.PeersRepository
import com.alex.hubplay.data.TICKS_PER_SECOND
import com.alex.hubplay.data.api.dto.PeerItemDto
import com.alex.hubplay.data.api.dto.PeerProgressDto
import com.alex.hubplay.ui.series.SeriesResumeMode
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

/** Ficha de una serie de un amigo: temporadas, episodios y qué pone Reproducir. */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedDetailViewModelTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val series = PeerItemDto(id = "sr", type = "series", title = "Oscuro", year = 2017)

    private fun season(id: String, n: Int) = PeerItemDto(id = id, type = "season", title = "Temporada $n", seasonNumber = n)

    private fun episode(id: String, s: Int, e: Int) = PeerItemDto(
        id = id, type = "episode", title = "Episodio $e", seasonNumber = s, episodeNumber = e,
        durationTicks = 3000 * TICKS_PER_SECOND,
    )

    private fun api(children: Map<String, List<PeerItemDto>>?) = FakePeersApi(page = listOf(series)).apply {
        this.children = children
    }

    private fun viewModel(api: FakePeersApi) = SharedDetailViewModel(
        PeersRepository(api) { "https://hub.example" },
        SharedTarget(peerId = "p1", itemId = "sr", libraryId = "lib"),
    )

    private fun progress(positionSec: Long, completed: Boolean = false) = PeerProgressDto(
        positionTicks = positionSec * TICKS_PER_SECOND,
        durationTicks = 3000 * TICKS_PER_SECOND,
        completed     = completed,
    )

    @Test
    fun `opens season 1 skipping specials and resumes the episode in progress`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val api = api(
            mapOf(
                "sr" to listOf(season("s0", 0), season("s1", 1), season("s2", 2)),
                "s1" to listOf(episode("e1", 1, 1), episode("e2", 1, 2), episode("e3", 1, 3)),
            ),
        ).apply { progress = mapOf("e1" to progress(3000, completed = true), "e2" to progress(600)) }
        val vm = viewModel(api)
        advanceUntilIdle()

        val state = vm.ui.value.series!!
        assertThat(state.isLoading).isFalse()
        assertThat(state.seasons.map { it.id }).containsExactly("s0", "s1", "s2").inOrder()
        assertThat(state.selectedSeasonId).isEqualTo("s1")
        assertThat(state.selectedEpisodes.map { it.remoteKind }).containsExactly(
            MediaKind.Episode, MediaKind.Episode, MediaKind.Episode,
        )
        val target = state.resumeTarget("sr")
        assertThat(target.mode).isEqualTo(SeriesResumeMode.RESUME)
        assertThat(target.episodeId).isEqualTo("e2")
        assertThat(target.resumeSec).isEqualTo(600L)
        // El episodio queda en la caché del repo: el reproductor saca de ahí el título.
        assertThat(state.episode("e2")?.isPlayable).isTrue()
    }

    @Test
    fun `plays the one after the last watched and loads other seasons on demand`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val api = api(
            mapOf(
                "sr" to listOf(season("s1", 1), season("s2", 2)),
                "s1" to listOf(episode("e1", 1, 1), episode("e2", 1, 2)),
                "s2" to listOf(episode("e21", 2, 1)),
            ),
        ).apply { progress = mapOf("e1" to progress(2900)) } // pasado del 90 %: visto
        val vm = viewModel(api)
        advanceUntilIdle()

        val target = vm.ui.value.series!!.resumeTarget("sr")
        assertThat(target.mode).isEqualTo(SeriesResumeMode.NEXT_UP)
        assertThat(target.episodeId).isEqualTo("e2")

        vm.selectSeason("s2")
        advanceUntilIdle()
        assertThat(vm.ui.value.series!!.selectedEpisodes.map { it.id }).containsExactly("e21")
    }

    @Test
    fun `a series without season folders is its own single season`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = viewModel(api(mapOf("sr" to listOf(episode("e1", 1, 1), episode("e2", 1, 2)))))
        advanceUntilIdle()

        val state = vm.ui.value.series!!
        assertThat(state.seasons.single().id).isEqualTo("sr")
        assertThat(state.selectedEpisodes.map { it.id }).containsExactly("e1", "e2").inOrder()
        assertThat(state.resumeTarget("sr").mode).isEqualTo(SeriesResumeMode.START)
    }

    @Test
    fun `a friend that cannot list episodes leaves the series marked unavailable`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = viewModel(api(children = null))
        advanceUntilIdle()

        val state = vm.ui.value.series!!
        assertThat(state.unavailable).isTrue()
        assertThat(state.isLoading).isFalse()
        assertThat(state.resumeTarget("sr").episodeId).isNull()
    }
}
