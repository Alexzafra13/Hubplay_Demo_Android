package com.alex.hubplay.ui.components

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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alex.hubplay.R
import com.alex.hubplay.ui.home.components.Tab
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.BgBase
import com.alex.hubplay.ui.theme.TextMuted
import com.alex.hubplay.ui.theme.TextPrimary
import com.alex.hubplay.ui.theme.TextSecondary

/**
 * Ancho de la franja colapsada (solo iconos). Es lo que el contenido
 * reserva a la izquierda en todas las pantallas de primer nivel; la
 * expansión flota por encima del contenido sin desplazarlo.
 */
val SIDEBAR_WIDTH = 56.dp

/** Ancho expandido: icono + label + aire. */
private val SidebarExpandedWidth = 228.dp

private val SidebarRowHeight   = 44.dp
private val SidebarIconPill    = 36.dp
private val SidebarBrandHeight = 30.dp

private const val LABEL_FADE_IN_MS  = 180
private const val LABEL_FADE_OUT_MS = 120

/** Fondo del menú: opaco casi hasta el borde derecho y ahí se funde con
 *  el contenido (el "glass" sin blur). Con menos opacidad, los botones
 *  del hero se leían a través de las etiquetas del menú abierto. */
private const val SIDEBAR_BG_ALPHA   = 0.985f
private const val SIDEBAR_BG_FADE_AT = 0.86f
private const val ROW_FOCUS_SCALE    = 1.04f

/**
 * Menú lateral único de HubPlay. Colapsado enseña solo iconos con el
 * logo arriba; al recibir foco se expande con las etiquetas y el
 * wordmark, montado por encima del contenido (el caller decide el
 * scrim, ver [TvShell]).
 *
 * Tres estados por fila, claramente distintos a 3 metros:
 * - **Foco** (el cursor del mando): pill blanco tras el icono + label
 *   en primario y SemiBold.
 * - **Activo** (la pantalla en la que estás): barra de acento a la
 *   izquierda + icono/label en primario. Se ve también colapsado, así
 *   siempre sabes dónde estás.
 * - **Resto**: icono y label apagados.
 *
 * Sin blur real (API 30 en boxes baratas): el "glass" es un degradado
 * horizontal opaco→transparente en el borde derecho.
 */
@Composable
fun NavSidebar(
    selectedTab:      Tab?,
    onNavigateToTab:  (Tab) -> Unit,
    onOpenSettings:   () -> Unit,
    visibleTabs:      Set<Tab>,
    settingsSelected: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
    modifier:         Modifier = Modifier,
) {
    // Expandido = alguna fila con foco real. Se agrega por fila en vez
    // de fiarse del focusGroup: en Mi Box S el callback del grupo no
    // siempre baja a false al salir hacia el contenido y el menú se
    // quedaba "pegado" abierto.
    val rowsFocused = remember { mutableStateMapOf<Int, Boolean>() }
    val expanded by remember { derivedStateOf { rowsFocused.values.any { it } } }
    LaunchedEffect(expanded) { onExpandedChange(expanded) }

    val animatedWidth by animateDpAsState(
        targetValue   = if (expanded) SidebarExpandedWidth else SIDEBAR_WIDTH,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = Spring.StiffnessMediumLow,
        ),
        label         = "sidebar-width",
    )
    val sidebarBrush = remember {
        Brush.horizontalGradient(
            0f                 to BgBase.copy(alpha = SIDEBAR_BG_ALPHA),
            SIDEBAR_BG_FADE_AT to BgBase.copy(alpha = SIDEBAR_BG_ALPHA),
            1f                 to Color.Transparent,
        )
    }

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
                .focusGroup(),
        ) {
            SidebarBrand(expanded = expanded)

            Spacer(Modifier.weight(1f))

            // Índices estables por Tab (ordinal) — una fila oculta
            // simplemente no aparece y no descoloca al resto.
            Tab.entries.filter { it in visibleTabs }.forEach { tab ->
                SidebarRow(
                    icon     = tab.icon,
                    label    = stringResource(tab.labelRes),
                    active   = tab == selectedTab,
                    expanded = expanded,
                    onClick  = { onNavigateToTab(tab) },
                    onFocusedChange = { f -> rowsFocused[tab.ordinal] = f },
                )
            }

            Spacer(Modifier.weight(1f))

            SidebarRow(
                icon     = Icons.Default.Settings,
                label    = stringResource(R.string.home_sidebar_settings),
                active   = settingsSelected,
                expanded = expanded,
                onClick  = onOpenSettings,
                onFocusedChange = { f -> rowsFocused[Tab.entries.size] = f },
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Logo arriba del rail. Colapsado: solo la marca (el mando), alineada con
 * la columna de iconos. Expandido: el wordmark completo (que ya lleva la
 * marca dentro) en el mismo sitio — un crossfade, nunca los dos a la vez.
 */
@Composable
private fun SidebarBrand(expanded: Boolean) {
    Box(
        modifier = Modifier
            .padding(start = 10.dp, top = 18.dp, end = 12.dp)
            .height(SidebarBrandHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        AnimatedVisibility(
            visible = !expanded,
            enter   = fadeIn(animationSpec = tween(LABEL_FADE_IN_MS)),
            exit    = fadeOut(animationSpec = tween(LABEL_FADE_OUT_MS)),
        ) {
            Image(
                painter            = painterResource(R.drawable.brand_mark),
                contentDescription = stringResource(R.string.brand_hubplay),
                modifier           = Modifier.size(SidebarIconPill),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter   = fadeIn(animationSpec = tween(LABEL_FADE_IN_MS)),
            exit    = fadeOut(animationSpec = tween(LABEL_FADE_OUT_MS)),
        ) {
            Image(
                painter            = painterResource(R.drawable.brand_wordmark),
                contentDescription = stringResource(R.string.brand_hubplay),
                modifier           = Modifier
                    .padding(start = 2.dp)
                    .height(24.dp),
            )
        }
    }
}

@Composable
private fun SidebarRow(
    icon:            ImageVector,
    label:           String,
    active:          Boolean,
    expanded:        Boolean,
    onClick:         () -> Unit,
    onFocusedChange: (Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (focused) ROW_FOCUS_SCALE else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = Spring.StiffnessMediumLow,
        ),
        label         = "sidebar-row-scale",
    )
    val iconBg = if (focused) Color.White else Color.Transparent
    val iconTint = when {
        focused -> BgBase
        active  -> TextPrimary
        else    -> TextMuted
    }
    val labelColor = when {
        focused -> TextPrimary
        active  -> TextPrimary
        else    -> TextSecondary
    }
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
        // Barra de "estás aquí": 3dp de acento pegada al borde. Ocupa
        // sitio siempre (transparente si no es la activa) para que los
        // iconos no bailen entre filas.
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (active && !focused) Accent else Color.Transparent),
        )
        Spacer(Modifier.width(5.dp))
        Box(
            modifier = Modifier
                .size(SidebarIconPill)
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
        SidebarLabel(label = label, color = labelColor, bold = focused || active, visible = expanded)
    }
}

/** Etiqueta de fila: entra/sale con la expansión del menú. */
@Composable
private fun SidebarLabel(label: String, color: Color, bold: Boolean, visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
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
                color      = color,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
        }
    }
}
