package com.alex.hubplay.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import com.alex.hubplay.ui.home.components.LocalVisibleTabs
import com.alex.hubplay.ui.home.components.Tab
import com.alex.hubplay.ui.theme.BgBase

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

    Box(modifier = Modifier.fillMaxSize().background(BgBase)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (padContent) Modifier.padding(start = SIDEBAR_WIDTH) else Modifier),
        ) {
            content()
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
                .zIndex(SIDEBAR_Z_INDEX),
        )
    }
}
