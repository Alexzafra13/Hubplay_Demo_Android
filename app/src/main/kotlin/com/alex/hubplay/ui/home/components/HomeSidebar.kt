package com.alex.hubplay.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alex.hubplay.R
import com.alex.hubplay.ui.theme.BgBase
import com.alex.hubplay.ui.theme.TextMuted
import com.alex.hubplay.ui.theme.TextPrimary

/**
 * Ancho de la franja colapsada — sólo iconos. Coincide con el ancho
 * reservado en el layout padre (HomeScreen) para que cuando el sidebar
 * se expanda el contenido NO se desplace: el inner `requiredWidth` deja
 * que la columna se salga visualmente por encima del rail mientras
 * la celda del Row sigue valiendo [SIDEBAR_WIDTH] dp.
 */
val SIDEBAR_WIDTH = 52.dp

/** Ancho en estado expandido (icono + label + padding). Inspirado en
 *  LG Prime Video TV — el label "Configuración" cabe holgado a 220dp. */
private val SidebarExpandedWidth = 220.dp

/** Altura de cada fila (icono + posible label). */
private val SidebarRowHeight = 44.dp

/** Stops del horizontal gradient que da el "glass" look. */
private const val SidebarBgAlphaLeft   = 0.96f
private const val SidebarBgAlphaMid    = 0.92f
private const val SidebarBgStopMid     = 0.65f

/** Highlight de la fila enfocada — fondo blanco translúcido + borde. */
private const val FocusedRowBgAlpha    = 0.10f
private const val FocusedRowBorderAlpha = 0.6f
private val FocusedRowBorderWidth      = 1.5.dp

/**
 * Lateral nav rail estilo Prime Video TV: colapsado muestra iconos,
 * al recibir foco se expande mostrando labels con un fade glass en el
 * borde derecho. El layout padre reserva sólo [SIDEBAR_WIDTH] dp, así
 * que la expansión flota OVER el contenido (el caller debe darnos un
 * `zIndex` alto en HomeScreen para que dibujemos por encima de los
 * rails y el hero).
 *
 * Glass effect: en API 30 (Mi Box S) no podemos usar `Modifier.blur`
 * real, así que aplicamos un horizontal gradient que pasa de
 * `BgBase` opaco a transparente — visualmente similar y sin coste.
 */
@Composable
fun HomeSidebar(
    onNavigateToTab: (Tab) -> Unit,
    onOpenSearch:    () -> Unit,
    onOpenSettings:  () -> Unit,
    visibleTabs:     Set<Tab>,
    modifier:        Modifier = Modifier,
) {
    // `hasFocus` es true si CUALQUIER fila descendente tiene el foco —
    // basta para decidir colapsado vs expandido. Cuando el usuario pulsa
    // → desde el último icono el focus engine lleva el foco fuera del
    // grupo y el callback baja a false, colapsando con animación.
    var groupFocused by remember { mutableStateOf(false) }
    val animatedWidth by animateDpAsState(
        targetValue   = if (groupFocused) SidebarExpandedWidth else SIDEBAR_WIDTH,
        animationSpec = tween(durationMillis = 220),
        label         = "sidebar-width",
    )

    // El sidebar se ancla a la izquierda del Box padre (HomeScreen lo
    // posiciona con `align(CenterStart)` + `zIndex`). El Column toma su
    // `animatedWidth` natural, sin pelearse con un slot fijo del Row —
    // así la expansión a 220dp se ve completa sin clip alguno.
    Box(
        modifier = modifier
            .width(animatedWidth)
            .fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .width(animatedWidth)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        0f               to BgBase.copy(alpha = SidebarBgAlphaLeft),
                        SidebarBgStopMid to BgBase.copy(alpha = SidebarBgAlphaMid),
                        1f               to Color.Transparent,
                    ),
                )
                .focusGroup()
                .onFocusChanged { state -> groupFocused = state.hasFocus },
            verticalArrangement = Arrangement.Center,
        ) {
            SidebarRow(
                icon     = Icons.Default.Search,
                label    = stringResource(R.string.home_sidebar_search),
                onClick  = onOpenSearch,
                expanded = groupFocused,
            )
            SidebarRow(
                icon     = Icons.Default.Home,
                label    = stringResource(R.string.home_sidebar_home),
                onClick  = { onNavigateToTab(Tab.Home) },
                expanded = groupFocused,
            )
            if (Tab.Movies in visibleTabs) {
                SidebarRow(
                    icon     = Icons.Default.Movie,
                    label    = stringResource(R.string.home_sidebar_movies),
                    onClick  = { onNavigateToTab(Tab.Movies) },
                    expanded = groupFocused,
                )
            }
            if (Tab.Series in visibleTabs) {
                SidebarRow(
                    icon     = Icons.Default.VideoLibrary,
                    label    = stringResource(R.string.home_sidebar_series),
                    onClick  = { onNavigateToTab(Tab.Series) },
                    expanded = groupFocused,
                )
            }
            if (Tab.Collections in visibleTabs) {
                SidebarRow(
                    icon     = Icons.Outlined.CollectionsBookmark,
                    label    = stringResource(R.string.home_sidebar_collections),
                    onClick  = { onNavigateToTab(Tab.Collections) },
                    expanded = groupFocused,
                )
            }
            if (Tab.LiveTv in visibleTabs) {
                SidebarRow(
                    icon     = Icons.Default.LiveTv,
                    label    = stringResource(R.string.home_sidebar_livetv),
                    onClick  = { onNavigateToTab(Tab.LiveTv) },
                    expanded = groupFocused,
                )
            }
            SidebarRow(
                icon     = Icons.Default.Settings,
                label    = stringResource(R.string.home_sidebar_settings),
                onClick  = onOpenSettings,
                expanded = groupFocused,
            )
        }
    }
}

/**
 * Una fila del sidebar: icono fijo + label que aparece con
 * expandHorizontally + fadeIn al expandirse. Cuando la fila tiene
 * foco directo (no sólo el grupo), se resalta con border + leve scale
 * y el texto pasa a SemiBold.
 */
@Composable
private fun SidebarRow(
    icon:     ImageVector,
    label:    String,
    onClick:  () -> Unit,
    expanded: Boolean,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (focused) 1.05f else 1.0f,
        animationSpec = tween(150),
        label         = "sidebar-row-scale",
    )
    val tint = if (focused) TextPrimary else TextMuted
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier = Modifier
            // Le damos toda la anchura que el grupo padre tenga ahora
            // mismo — así el highlight focused cubre la fila entera y
            // arrastra el label dentro del mismo rectángulo.
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .height(SidebarRowHeight)
            .clip(shape)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick,
            )
            .then(
                if (focused) {
                    Modifier
                        .background(Color.White.copy(alpha = FocusedRowBgAlpha))
                        .border(
                            FocusedRowBorderWidth,
                            TextPrimary.copy(alpha = FocusedRowBorderAlpha),
                            shape,
                        )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = label,
            tint               = tint,
            modifier           = Modifier.size(20.dp),
        )
        AnimatedVisibility(
            visible = expanded,
            enter   = expandHorizontally(animationSpec = tween(180)) + fadeIn(tween(220)),
            exit    = shrinkHorizontally(animationSpec = tween(160)) + fadeOut(tween(140)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(14.dp))
                Text(
                    text       = label,
                    color      = tint,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
