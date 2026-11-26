package com.alex.hubplay.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import com.alex.hubplay.ui.home.components.LocalVisibleTabs
import com.alex.hubplay.ui.home.components.Tab
import com.alex.hubplay.ui.theme.BgBase

/**
 * Lo que una pantalla dentro de [TvShell] puede pedirle al armazón. Hoy:
 * ocultar el menú lateral mientras la ficha está en vista previa del
 * tráiler (5 s sin tocar el mando), para que el vídeo quede limpio.
 */
@Stable
class ShellChrome {
    var hideSidebar by mutableStateOf(false)
}

/** null fuera de [TvShell] (tests, pantallas sin armazón). */
val LocalShellChrome = staticCompositionLocalOf<ShellChrome?> { null }

/** Mismo ritmo que el fundido de la vista previa de la ficha. */
private const val SIDEBAR_HIDE_MS = 600

/** Capa del sidebar: por encima de cualquier contenido de la pantalla. */
const val SIDEBAR_Z_INDEX = 5f

/** Scrim al expandir el menú: fuerte junto al menú, suave a la derecha. */
private const val SCRIM_ALPHA_LEFT  = 0.92f
private const val SCRIM_ALPHA_MID   = 0.60f
private const val SCRIM_ALPHA_RIGHT = 0.35f
private const val SCRIM_MID_STOP    = 0.30f
private const val SCRIM_IN_MS       = 200
private const val SCRIM_OUT_MS      = 160

/**
 * Armazón común de las pantallas de primer nivel: contenido + scrim +
 * [NavSidebar] a la izquierda. Es lo que hace que Inicio, Películas,
 * Series, Colecciones, TV en vivo y Buscar se sientan la misma app.
 *
 * - El contenido reserva [SIDEBAR_WIDTH] a la izquierda (`padContent`);
 *   Inicio pasa `false` porque su backdrop debe llegar hasta el borde y
 *   ya reserva el hueco en su propia columna.
 * - Al expandirse el menú, un scrim degradado atenúa el contenido de
 *   izquierda a derecha: el menú se lee, el contenido sigue ahí. Es un
 *   overlay sin foco ni clicks, no interfiere con el D-pad.
 */
@Composable
fun TvShell(
    selectedTab:      Tab?,
    onNavigateToTab:  (Tab) -> Unit,
    onOpenSettings:   () -> Unit,
    padContent:       Boolean = true,
    settingsSelected: Boolean = false,
    content:          @Composable BoxScope.() -> Unit,
) {
    val visibleTabs = LocalVisibleTabs.current
    var expanded by remember { mutableStateOf(false) }
    val chrome = remember { ShellChrome() }
    // Con el menú desplegado (el foco está en él) nunca se oculta.
    val sidebarAlpha by animateFloatAsState(
        targetValue   = if (chrome.hideSidebar && !expanded) 0f else 1f,
        animationSpec = tween(SIDEBAR_HIDE_MS),
        label         = "sidebar-hide",
    )
    val scrimAlpha by animateFloatAsState(
        targetValue   = if (expanded) 1f else 0f,
        animationSpec = tween(if (expanded) SCRIM_IN_MS else SCRIM_OUT_MS),
        label         = "sidebar-scrim",
    )
    val scrimBrush = remember {
        Brush.horizontalGradient(
            0f             to BgBase.copy(alpha = SCRIM_ALPHA_LEFT),
            SCRIM_MID_STOP to BgBase.copy(alpha = SCRIM_ALPHA_MID),
            1f             to BgBase.copy(alpha = SCRIM_ALPHA_RIGHT),
        )
    }

    // Fondo opaco SOLO cuando el contenido va con padding (catálogo, TV,
    // buscar…). Inicio pasa padContent=false y necesita el armazón
    // transparente: el WebView del tráiler vive DEBAJO del NavHost y con
    // un fondo aquí el hero se quedaba en negro al reproducirlo.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (padContent) Modifier.background(BgBase) else Modifier),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (padContent) Modifier.padding(start = SIDEBAR_WIDTH) else Modifier),
        ) {
            CompositionLocalProvider(LocalShellChrome provides chrome) { content() }
        }

        if (scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(SIDEBAR_Z_INDEX - 1f)
                    .graphicsLayer { alpha = scrimAlpha }
                    .background(scrimBrush),
            )
        }

        NavSidebar(
            selectedTab      = selectedTab,
            onNavigateToTab  = onNavigateToTab,
            onOpenSettings   = onOpenSettings,
            visibleTabs      = visibleTabs,
            settingsSelected = settingsSelected,
            onExpandedChange = { expanded = it },
            modifier         = Modifier
                .align(Alignment.CenterStart)
                .zIndex(SIDEBAR_Z_INDEX)
                .graphicsLayer { alpha = sidebarAlpha },
        )
    }
}
