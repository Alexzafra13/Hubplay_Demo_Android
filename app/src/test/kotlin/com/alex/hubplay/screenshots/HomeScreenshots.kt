package com.alex.hubplay.screenshots

import android.view.KeyEvent.KEYCODE_DPAD_CENTER
import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import androidx.activity.compose.setContent
import com.alex.hubplay.data.BecauseYouWatched
import com.alex.hubplay.data.CollectionSummary
import com.alex.hubplay.data.HomeRailConfig
import com.alex.hubplay.data.HomeRailType
import com.alex.hubplay.ui.home.HomeActions
import com.alex.hubplay.ui.home.HomeScreen
import com.alex.hubplay.ui.home.HomeViewModel
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Test

class HomeScreenshots : ScreenshotTest() {

    private fun showHome(repo: SampleHomeRepository = SampleHomeRepository()) {
        val vm = HomeViewModel(repo, emptyFlow())
        setTvContent {
            HomeScreen(
                viewModel = vm,
                actions = HomeActions(
                    onOpenItem = { _, _ -> },
                    onPlayItem = { _, _ -> },
                    onPlayChannel = {},
                    onOpenShared = {},
                    onPlayShared = { _, _ -> },
                    onNavigateToTab = {},
                ),
                profileName = "Alex",
            )
        }
        settle(2500)
    }

    /**
     * Regresión: un item de Continuar viendo que desaparece (SSE desde otro
     * dispositivo, o "Quitar") mientras el foco está en OTRO rail. El
     * focusRestorer del rail lo tenía fijado y al desmontar Inicio Compose
     * abortaba con "Release should only be called once".
     */
    @Test
    fun railItemRemovedWhileFocusElsewhere() {
        val vm = HomeViewModel(SampleHomeRepository(), emptyFlow())
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
        press(KEYCODE_DPAD_DOWN, settleMs = 1200) // Continuar viendo (foco en la 1.ª)
        press(KEYCODE_DPAD_DOWN, settleMs = 1200) // A continuación
        compose.runOnUiThread { vm.removeFromContinueWatching(vm.ui.value.data.continueWatching.first()) }
        settle(1500)
        snap("home_14_removed_elsewhere")
        // Desmontar Inicio (como al navegar a otra pantalla).
        compose.runOnUiThread { compose.activity.setContent { } }
        settle(500)
    }

    /** Mantener OK en Continuar viendo → "¿Quitar de Continuar viendo?" con el foco en Cancelar. */
    @Test
    fun removeFromContinue() {
        showHome()
        press(KEYCODE_DPAD_DOWN) // menú lateral
        press(KEYCODE_DPAD_RIGHT) // hero
        press(KEYCODE_DPAD_DOWN, settleMs = 1200) // Continuar viendo
        longPress(KEYCODE_DPAD_CENTER)
        snap("home_11_remove_continue")
        press(KEYCODE_DPAD_RIGHT) // a "Quitar"
        snap("home_12_remove_confirm_focus")
        press(KEYCODE_DPAD_CENTER, settleMs = 1500)
        snap("home_13_removed")
    }

    @Test
    fun home() {
        showHome()
        snap("home_01_landing")
        press(KEYCODE_DPAD_DOWN) // primer foco: menú lateral
        snap("home_02_sidebar")
        press(KEYCODE_DPAD_RIGHT) // al hero
        snap("home_03_hero_play")
        press(KEYCODE_DPAD_DOWN, settleMs = 1200)
        snap("home_04_rail_continue")
        press(KEYCODE_DPAD_RIGHT, settleMs = 2000)
        snap("home_05a_rail_right1")
        press(KEYCODE_DPAD_RIGHT, settleMs = 2000)
        snap("home_05_rail_moved")
        press(KEYCODE_DPAD_DOWN, settleMs = 1200)
        snap("home_06_next_up")
        press(KEYCODE_DPAD_DOWN, settleMs = 1200)
        snap("home_07_trending")
        press(KEYCODE_DPAD_DOWN, settleMs = 1200)
        snap("home_08_live")
        press(KEYCODE_DPAD_DOWN, settleMs = 1200)
        snap("home_09_latest")
        press(KEYCODE_DPAD_DOWN, settleMs = 1200)
        snap("home_10_last")
    }

    /** Rails que la app añade tras el layout, como la web: Porque viste X, Recomendados, Colecciones, Favoritos. */
    @Test
    fun discoveryRails() {
        showHome(
            object : SampleHomeRepository() {
                override suspend fun fetchHomeLayout() =
                    listOf(HomeRailConfig("cw", HomeRailType.ContinueWatching, null, "Continuar viendo"))
                override suspend fun fetchBecauseYouWatched(limit: Int) =
                    BecauseYouWatched("Arena Roja", SampleCatalog.movies.drop(2))
                override suspend fun fetchCollections() = SampleCatalog.movies.take(4).mapIndexed { i, m ->
                    CollectionSummary("col$i", "Saga ${m.title}", 3, m.posterUrl, m.backdropUrl)
                }
                override suspend fun fetchFavoriteChannels() = SampleCatalog.liveNow.take(2)
            },
        )
        press(KEYCODE_DPAD_DOWN) // menú lateral
        press(KEYCODE_DPAD_RIGHT) // hero
        press(KEYCODE_DPAD_DOWN, times = 2, settleMs = 1200) // Porque viste X
        snap("home_15_because_you_watched")
        press(KEYCODE_DPAD_DOWN, times = 2, settleMs = 1200) // Colecciones
        snap("home_16_collections")
        press(KEYCODE_DPAD_DOWN, settleMs = 1200) // Canales favoritos
        snap("home_17_favorite_channels")
    }
}
