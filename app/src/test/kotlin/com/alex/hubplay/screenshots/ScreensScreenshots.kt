package com.alex.hubplay.screenshots

import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.alex.hubplay.ui.catalog.CatalogScreen
import com.alex.hubplay.ui.catalog.CatalogViewModel
import com.alex.hubplay.ui.catalog.PortraitCatalogCard
import com.alex.hubplay.ui.components.TvShell
import com.alex.hubplay.ui.detail.DetailScreen
import com.alex.hubplay.ui.detail.DetailViewModel
import com.alex.hubplay.ui.home.components.Tab
import com.alex.hubplay.ui.search.SearchScreen
import com.alex.hubplay.ui.search.SearchViewModel
import com.alex.hubplay.ui.series.SeriesScreen
import com.alex.hubplay.ui.series.SeriesViewModel
import org.junit.Test

/** Ficha, serie, catálogo y buscar con la biblioteca de [SampleCatalog]. */
class ScreensScreenshots : ScreenshotTest() {

    private val repo = SampleHomeRepository()

    /** Como en HubplayNavGraph: las fichas van dentro de TvShell sin padding. */
    @Composable
    private fun inShell(tab: Tab, content: @Composable () -> Unit) {
        TvShell(selectedTab = tab, onNavigateToTab = {}, onOpenSettings = {}, padContent = false) { content() }
    }

    @Test
    fun detail() {
        val vm = DetailViewModel(repo, "m2")
        setTvContent { inShell(Tab.Movies) { DetailScreen(viewModel = vm, onPlay = { _, _ -> }, onBack = {}) } }
        settle(2500)
        snap("detail_01")
        // En la TV el foco nace en Reproducir (el foco inicial no se simula).
        focusText("Reproducir")
        snap("detail_02_play")
        press(KEYCODE_DPAD_DOWN, settleMs = 1500)
        snap("detail_03_down2")
        press(KEYCODE_DPAD_DOWN, settleMs = 1500)
        snap("detail_04_down3")
        press(KEYCODE_DPAD_DOWN, settleMs = 1500)
        snap("detail_05_down4")
    }

    @Test
    fun series() {
        val vm = SeriesViewModel(repo, "s1")
        setTvContent { inShell(Tab.Series) { SeriesScreen(viewModel = vm, onPlayEpisode = { _, _ -> }, onBack = {}) } }
        settle(2500)
        snap("series_01")
        focusText("Seguir T2·E4")
        snap("series_02_play")
        press(KEYCODE_DPAD_DOWN, settleMs = 1500)
        snap("series_03_down2")
        press(KEYCODE_DPAD_DOWN, settleMs = 1500)
        snap("series_04_down3")
        press(KEYCODE_DPAD_DOWN, settleMs = 1500)
        snap("series_05_down4")
    }

    @Test
    fun catalog() {
        val vm = CatalogViewModel(repo, CatalogViewModel.Source.Movies)
        setTvContent {
            val ui by vm.ui.collectAsState()
            CatalogScreen(
                selectedTab = Tab.Movies,
                title = "Películas",
                items = ui.items,
                isLoading = ui.isLoading,
                isLoadingMore = ui.isLoadingMore,
                canLoadMore = ui.canLoadMore,
                error = ui.error,
                onRetry = {},
                onTabSelected = {},
                cardContent = { item -> PortraitCatalogCard(item) { _, _ -> } },
            )
        }
        settle(2000)
        snap("catalog_01")
        press(KEYCODE_DPAD_DOWN)
        press(KEYCODE_DPAD_RIGHT)
        snap("catalog_02_focus")
    }

    @Test
    fun search() {
        val vm = SearchViewModel(repo)
        setTvContent { SearchScreen(viewModel = vm, onTabSelected = {}, onOpenItem = { _, _ -> }) }
        settle(1000)
        snap("search_01_empty")
        compose.runOnUiThread { vm.onQueryChange("ma") }
        settle(2000)
        snap("search_02_results")
    }
}
