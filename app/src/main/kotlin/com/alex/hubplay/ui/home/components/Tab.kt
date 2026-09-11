package com.alex.hubplay.ui.home.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.alex.hubplay.R

/**
 * Destinos de primer nivel de la app. El orden del enum es el orden
 * visual del menú lateral ([com.alex.hubplay.ui.components.NavSidebar]):
 * Buscar arriba (acceso rápido), luego el catálogo, y TV en vivo al
 * final. Ajustes no es un Tab — vive fuera del back-stack de tabs y el
 * sidebar lo pinta como fila extra al pie.
 */
enum class Tab(@StringRes val labelRes: Int, val icon: ImageVector) {
    Search(R.string.nav_tab_search, Icons.Default.Search),
    Home(R.string.nav_tab_home, Icons.Default.Home),
    Movies(R.string.nav_tab_movies, Icons.Default.Movie),
    Series(R.string.nav_tab_series, Icons.Default.VideoLibrary),
    Collections(R.string.nav_tab_collections, Icons.Outlined.CollectionsBookmark),
    LiveTv(R.string.nav_tab_livetv, Icons.Default.LiveTv),
}

/**
 * Tabs visibles en la sesión actual. Se provee en la raíz de la app
 * ([com.alex.hubplay.HubplayApp]) para que Colecciones desaparezca en
 * bibliotecas sin sagas sin que cada pantalla tenga que saberlo. El
 * fallback al enum completo mantiene previews y tests sin provider.
 */
val LocalVisibleTabs: ProvidableCompositionLocal<Set<Tab>> =
    staticCompositionLocalOf { Tab.entries.toSet() }
