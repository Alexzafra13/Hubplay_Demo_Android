package com.alex.hubplay.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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

/** Altura de cada fila (icono + posible label). Compacta para que 7
 *  filas no ocupen toda la altura visible en pantallas 540dp como
 *  Mi Box S (1080p × 2.0 density) — antes a 44dp + padding ocupaban
 *  336dp = 62% pantalla y se veían "esparcidas". */
private val SidebarRowHeight = 38.dp

/** Tamaño del "pill" blanco que rodea al icono cuando la fila está
 *  enfocada — estilo Prime Video TV. El icono mismo (18dp) queda
 *  centrado con margen. */
private val SidebarIconPillSize = 30.dp

/** Padding superior antes del primer icono — deja aire para el brand
 *  wordmark de HomeScreen (que se renderiza a y=18..42dp) y alinea
 *  el cluster de iconos en el tercio superior estilo Prime Video. */
private val SidebarContentTopPadding = 72.dp

/** Duraciones de las animaciones de fade del label (in/out). */
private const val LABEL_FADE_IN_MS  = 180
private const val LABEL_FADE_OUT_MS = 120

/** Stops del horizontal gradient que da el "glass" look. */
private const val SidebarBgAlphaLeft   = 0.96f
private const val SidebarBgAlphaMid    = 0.92f
private const val SidebarBgStopMid     = 0.65f

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
    // Tracking per-row del foco en vez de `hasFocus` sobre el padre con
    // focusGroup() — en Mi Box S el callback del focusGroup no siempre
    // dispara con `hasFocus=false` cuando el foco sale del sidebar
    // hacia un rail, dejando el menú expandido "pegado". Agregando los
    // flags de cada fila garantiza que sólo está expandido si AL MENOS
    // una fila tiene foco real.
    val rowsFocused  = remember { mutableStateMapOf<Int, Boolean>() }
    val groupFocused by remember {
        derivedStateOf { rowsFocused.values.any { it } }
    }
    // Spring snappy pero sin rebote — apertura/cierre que se siente
    // rápido y orgánico. StiffnessMediumLow ≈ 400, da unos 280ms
    // efectivos al recorrer 52→220dp. Mucho más vivo que un tween
    // lineal del mismo tiempo y menos pesado en CPU porque no necesita
    // hacer animateFloatAsState para cada propiedad — width manda.
    val animatedWidth by animateDpAsState(
        targetValue   = if (groupFocused) SidebarExpandedWidth else SIDEBAR_WIDTH,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = Spring.StiffnessMediumLow,
        ),
        label         = "sidebar-width",
    )

    // Cachear el brush para que la animación no cree un objeto Brush
    // nuevo en cada frame — directamente notable en Mi Box S al
    // entrar/salir del sidebar.
    val sidebarBrush = remember {
        Brush.horizontalGradient(
            0f               to BgBase.copy(alpha = SidebarBgAlphaLeft),
            SidebarBgStopMid to BgBase.copy(alpha = SidebarBgAlphaMid),
            1f               to Color.Transparent,
        )
    }

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
                .background(sidebarBrush)
                .padding(top = SidebarContentTopPadding)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Construimos la lista de entradas en runtime para asignar
            // un índice estable a cada SidebarRow (clave del map de
            // foco). El índice es 0..6 y NO depende de qué tabs estén
            // visibles — un SidebarRow oculto simplemente no aparece.
            var rowIndex = 0
            SidebarRow(
                index    = rowIndex++,
                icon     = Icons.Default.Search,
                label    = stringResource(R.string.home_sidebar_search),
                onClick  = onOpenSearch,
                expanded = groupFocused,
                onFocusedChange = { f -> rowsFocused[0] = f },
            )
            SidebarRow(
                index    = rowIndex++,
                icon     = Icons.Default.Home,
                label    = stringResource(R.string.home_sidebar_home),
                onClick  = { onNavigateToTab(Tab.Home) },
                expanded = groupFocused,
                onFocusedChange = { f -> rowsFocused[1] = f },
            )
            if (Tab.Movies in visibleTabs) {
                val i = rowIndex++
                SidebarRow(
                    index    = i,
                    icon     = Icons.Default.Movie,
                    label    = stringResource(R.string.home_sidebar_movies),
                    onClick  = { onNavigateToTab(Tab.Movies) },
                    expanded = groupFocused,
                    onFocusedChange = { f -> rowsFocused[i] = f },
                )
            }
            if (Tab.Series in visibleTabs) {
                val i = rowIndex++
                SidebarRow(
                    index    = i,
                    icon     = Icons.Default.VideoLibrary,
                    label    = stringResource(R.string.home_sidebar_series),
                    onClick  = { onNavigateToTab(Tab.Series) },
                    expanded = groupFocused,
                    onFocusedChange = { f -> rowsFocused[i] = f },
                )
            }
            if (Tab.Collections in visibleTabs) {
                val i = rowIndex++
                SidebarRow(
                    index    = i,
                    icon     = Icons.Outlined.CollectionsBookmark,
                    label    = stringResource(R.string.home_sidebar_collections),
                    onClick  = { onNavigateToTab(Tab.Collections) },
                    expanded = groupFocused,
                    onFocusedChange = { f -> rowsFocused[i] = f },
                )
            }
            if (Tab.LiveTv in visibleTabs) {
                val i = rowIndex++
                SidebarRow(
                    index    = i,
                    icon     = Icons.Default.LiveTv,
                    label    = stringResource(R.string.home_sidebar_livetv),
                    onClick  = { onNavigateToTab(Tab.LiveTv) },
                    expanded = groupFocused,
                    onFocusedChange = { f -> rowsFocused[i] = f },
                )
            }
            val settingsIndex = rowIndex
            SidebarRow(
                index    = settingsIndex,
                icon     = Icons.Default.Settings,
                label    = stringResource(R.string.home_sidebar_settings),
                onClick  = onOpenSettings,
                expanded = groupFocused,
                onFocusedChange = { f -> rowsFocused[settingsIndex] = f },
            )
        }
    }
}

/**
 * Una fila del sidebar — estilo Prime Video TV:
 *
 * - Sólo el **icono** se resalta cuando la fila tiene foco: pill blanco
 *   detrás del icono, con el icono mismo en color oscuro (BgBase) para
 *   alto contraste. El label, si está visible, NO tiene highlight de
 *   fondo — sólo cambia a color claro + SemiBold.
 * - Scale ligero (1.04) en TODA la fila para que el highlight respire.
 * - Label aparece con `expandHorizontally + fadeIn` cuando el sidebar
 *   está expandido, con springs ligeros para que entre/salga suave sin
 *   sentirse pesado.
 */
@Composable
private fun SidebarRow(
    index:           Int,
    icon:            ImageVector,
    label:           String,
    onClick:         () -> Unit,
    expanded:        Boolean,
    onFocusedChange: (Boolean) -> Unit,
) {
    var focused by remember(index) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (focused) 1.04f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = Spring.StiffnessMediumLow,
        ),
        label         = "sidebar-row-scale",
    )
    val iconBg     = if (focused) Color.White       else Color.Transparent
    val iconTint   = if (focused) BgBase             else TextMuted
    val labelColor = if (focused) TextPrimary        else TextMuted
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .height(SidebarRowHeight)
            .scale(scale)
            .onFocusChanged {
                focused = it.isFocused
                onFocusedChange(it.isFocused)
            }
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Icono con su pill blanco (sólo visible focused) ───────
        Box(
            modifier = Modifier
                .size(SidebarIconPillSize)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = label,
                tint               = iconTint,
                modifier           = Modifier.size(20.dp),
            )
        }

        // ── Label: aparece/desaparece con la expansión ────────────
        AnimatedVisibility(
            visible = expanded,
            enter   = expandHorizontally(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ) + fadeIn(animationSpec = tween(LABEL_FADE_IN_MS)),
            exit    = shrinkHorizontally(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ) + fadeOut(animationSpec = tween(LABEL_FADE_OUT_MS)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(14.dp))
                Text(
                    text       = label,
                    color      = labelColor,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
