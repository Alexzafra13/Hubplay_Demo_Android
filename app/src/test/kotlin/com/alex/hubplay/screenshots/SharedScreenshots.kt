package com.alex.hubplay.screenshots

import android.view.KeyEvent.KEYCODE_DPAD_CENTER
import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import com.alex.hubplay.data.FakePeersApi
import com.alex.hubplay.data.PeersRepository
import com.alex.hubplay.data.SharedLibrary
import com.alex.hubplay.data.TICKS_PER_SECOND
import com.alex.hubplay.data.api.dto.PeerContinueDto
import com.alex.hubplay.data.api.dto.PeerItemDto
import com.alex.hubplay.data.api.dto.PeerLibraryDto
import com.alex.hubplay.data.api.dto.PeerProgressDto
import com.alex.hubplay.ui.components.TvShell
import com.alex.hubplay.ui.home.HomeActions
import com.alex.hubplay.ui.home.HomeScreen
import com.alex.hubplay.ui.home.HomeViewModel
import com.alex.hubplay.ui.home.components.Tab
import com.alex.hubplay.ui.shared.SharedDetailScreen
import com.alex.hubplay.ui.shared.SharedDetailViewModel
import com.alex.hubplay.ui.shared.SharedScreen
import com.alex.hubplay.ui.shared.SharedTarget
import com.alex.hubplay.ui.shared.SharedViewModel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** "Compartido contigo": sección, ficha y rails de amigos en Inicio. */
class SharedScreenshots : ScreenshotTest() {

    private fun peerItem(id: String, title: String, year: Int, type: String = "movie") = PeerItemDto(
        id = id, type = type, title = title, year = year,
        overview = "Una película que tu amigo comparte contigo desde su HubPlay. " +
            "La sinopsis viaja con el item; el resto (reparto, tráiler) se queda en su servidor.",
        posterUrl = FakeArtwork.poster(id, title),
    )

    private val pedroMovies = listOf(
        peerItem("pm1", "El faro", 2019), peerItem("pm2", "Niebla", 2021), peerItem("pm3", "Océano", 2018),
        peerItem("pm4", "Fuego cruzado", 2022), peerItem("pm5", "Los otros", 2001), peerItem("pm6", "Marea", 2020),
        peerItem("pm7", "Duna roja", 2023), peerItem("pm8", "Invierno", 2017),
    )
    private val luciaShows = listOf(
        peerItem("ls1", "Oscuro", 2017, "series"), peerItem("ls2", "La casa", 2020, "series"),
        peerItem("ls3", "Frontera", 2022, "series"),
    )

    private val api = FakePeersApi(
        continueWatching = listOf(
            PeerContinueDto(
                id = "pm2", peerId = "p1", peerName = "Pedro", libraryId = "lib-p1", type = "movie",
                title = "Niebla", year = 2021, posterUrl = FakeArtwork.poster("pm2", "Niebla"),
                positionTicks = 2400 * TICKS_PER_SECOND, durationTicks = 6000 * TICKS_PER_SECOND, percentage = 40.0,
            ),
        ),
        recent = pedroMovies.take(5).map { it.copy(peerId = "p1", peerName = "Pedro", libraryId = "lib-p1") },
    ).apply {
        libraries = listOf(
            PeerLibraryDto("p1", "Pedro", "lib-p1", "Películas", "movies", canPlay = true),
            PeerLibraryDto("p2", "Lucía", "lib-p2", "Series", "shows", canPlay = true),
        )
        pages = mapOf("lib-p1" to pedroMovies, "lib-p2" to luciaShows)
    }

    private val repo = PeersRepository(api) { "https://hub.example" }

    @Test
    fun section() {
        val vm = SharedViewModel(repo)
        setTvContent {
            SharedScreen(viewModel = vm, onOpenItem = {}, onResume = {}, onTabSelected = {}, onSettings = {})
        }
        settle(2500)
        // En la TV el foco nace en la primera tarjeta; aquí se llega a mano.
        press(KEYCODE_DPAD_DOWN) // menú lateral
        press(KEYCODE_DPAD_RIGHT, settleMs = 1200)
        snap("shared_01_section")
        press(KEYCODE_DPAD_DOWN, settleMs = 1200)
        snap("shared_02_down")
        press(KEYCODE_DPAD_DOWN, settleMs = 1200)
        snap("shared_03_series")
    }

    @Test
    fun detail() {
        // La ficha lee el item de la caché del repositorio (se acaba de ver en un listado).
        val library = SharedLibrary("p1", "Pedro", "lib-p1", "Películas", "movies", canPlay = true)
        runBlocking { repo.libraryItems(library) }
        val vm = SharedDetailViewModel(repo, SharedTarget(peerId = "p1", itemId = "pm1", libraryId = "lib-p1"))
        setTvContent {
            TvShell(selectedTab = Tab.Shared, onNavigateToTab = {}, onOpenSettings = {}, padContent = false) {
                SharedDetailScreen(viewModel = vm, onPlay = { _, _ -> }, onBack = {})
            }
        }
        settle(2500)
        focusText("Reproducir")
        snap("shared_04_detail")
    }

    /** Serie de un amigo: Reproducir el episodio que toca y el panel de temporadas. */
    @Test
    fun seriesDetail() {
        val seasons = (1..3).map {
            PeerItemDto(id = "ls1-s$it", type = "season", title = "Temporada $it", seasonNumber = it)
        }
        val titles = listOf("Secretos", "Mentiras", "Pasado y presente", "Doble vida", "Verdades", "Sic mundus")
        val episodes = titles.mapIndexed { i, title ->
            PeerItemDto(
                id = "ls1-e${i + 1}", type = "episode", title = title,
                seasonNumber = 1, episodeNumber = i + 1, durationTicks = 3000 * TICKS_PER_SECOND,
                posterUrl = FakeArtwork.still("ls1-e${i + 1}"),
            )
        }
        api.children = mapOf("ls1" to seasons, "ls1-s1" to episodes)
        api.progress = mapOf(
            "ls1-e1" to PeerProgressDto(3000 * TICKS_PER_SECOND, 3000 * TICKS_PER_SECOND, completed = true),
            "ls1-e2" to PeerProgressDto(1250 * TICKS_PER_SECOND, 3000 * TICKS_PER_SECOND),
        )
        val library = SharedLibrary("p2", "Lucía", "lib-p2", "Series", "shows", canPlay = true)
        runBlocking { repo.libraryItems(library) }
        val vm = SharedDetailViewModel(repo, SharedTarget(peerId = "p2", itemId = "ls1", libraryId = "lib-p2"))
        setTvContent {
            TvShell(selectedTab = Tab.Shared, onNavigateToTab = {}, onOpenSettings = {}, padContent = false) {
                SharedDetailScreen(viewModel = vm, onPlay = { _, _ -> }, onBack = {})
            }
        }
        settle(2500)
        focusText("Seguir T1·E2")
        snap("shared_07_series_hero")
        focusText("Episodios")
        press(KEYCODE_DPAD_CENTER, settleMs = 1500)
        snap("shared_08_series_episodes")
    }

    @Test
    fun homeRails() {
        val vm = HomeViewModel(SampleHomeRepository(), emptyFlow(), repo)
        setTvContent {
            HomeScreen(
                viewModel = vm,
                actions = HomeActions(
                    onOpenItem = { _, _ -> }, onPlayItem = { _, _ -> }, onPlayChannel = {},
                    onOpenShared = {}, onPlayShared = { _, _ -> }, onNavigateToTab = {},
                ),
                profileName = "Alex",
            )
        }
        settle(2500)
        press(KEYCODE_DPAD_DOWN) // menú lateral
        press(KEYCODE_DPAD_RIGHT) // hero
        // Bajar hasta los rails de amigos (al final de Inicio, tras Recomendados).
        press(KEYCODE_DPAD_DOWN, times = 8, settleMs = 900)
        snap("shared_05_home_continue")
        press(KEYCODE_DPAD_DOWN, settleMs = 1200)
        snap("shared_06_home_recent")
    }
}
