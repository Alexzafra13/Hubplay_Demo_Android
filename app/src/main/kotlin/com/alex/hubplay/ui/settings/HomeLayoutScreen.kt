package com.alex.hubplay.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.hubplay.R
import com.alex.hubplay.data.HomeRailType
import com.alex.hubplay.data.api.dto.HomeSectionDto
import com.alex.hubplay.data.homeRailTitle
import com.alex.hubplay.ui.components.BackPill
import com.alex.hubplay.ui.components.HeroIconButton
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.BgBase
import com.alex.hubplay.ui.theme.BgCard
import com.alex.hubplay.ui.theme.TextPrimary
import com.alex.hubplay.ui.theme.TextSecondary

/**
 * Ajustes → Inicio: lista de rails con su orden y si se muestran. OK
 * sobre la fila alterna mostrar/ocultar; las flechas de la derecha
 * cambian el orden. Los tipos que esta versión de la app no sabe pintar
 * se listan igualmente para no perderlos al guardar, pero sin título.
 */
@Composable
fun HomeLayoutScreen(
    viewModel: HomeLayoutViewModel,
    onBack:    () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()
    val firstRowFocus = remember { FocusRequester() }
    LaunchedEffect(ui.sections.isNotEmpty()) {
        if (ui.sections.isNotEmpty()) runCatching { firstRowFocus.requestFocus() }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = BgBase) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackPill(onBack = onBack)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text       = stringResource(R.string.home_layout_title),
                        style      = MaterialTheme.typography.headlineSmall,
                        color      = TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text  = stringResource(R.string.home_layout_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            ui.error?.let { err ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.action_dismiss)) }
                }
                Spacer(Modifier.height(8.dp))
            }
            when {
                ui.isLoading && ui.sections.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                ui.sections.isEmpty() && !ui.isLoading -> TextButton(onClick = viewModel::load) {
                    Text(stringResource(R.string.action_retry))
                }
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding      = PaddingValues(bottom = 24.dp),
                    modifier            = Modifier.widthIn(max = ROW_MAX_WIDTH),
                ) {
                    itemsIndexed(ui.sections, key = { _, s -> s.id }) { index, section ->
                        SectionRow(
                            index    = index,
                            section  = section,
                            isFirst  = index == 0,
                            isLast   = index == ui.sections.lastIndex,
                            actions  = SectionRowActions(
                                onToggle = { viewModel.toggleVisible(section.id) },
                                onUp     = { viewModel.moveUp(section.id) },
                                onDown   = { viewModel.moveDown(section.id) },
                            ),
                            modifier = if (index == 0) Modifier.focusRequester(firstRowFocus) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

/** Acciones de una fila: mostrar/ocultar, subir y bajar. */
private class SectionRowActions(
    val onToggle: () -> Unit,
    val onUp:     () -> Unit,
    val onDown:   () -> Unit,
)

@Composable
private fun SectionRow(
    index:    Int,
    section:  HomeSectionDto,
    isFirst:  Boolean,
    isLast:   Boolean,
    actions:  SectionRowActions,
    modifier: Modifier = Modifier,
) {
    val onToggle = actions.onToggle
    val onUp     = actions.onUp
    val onDown   = actions.onDown
    var focused by remember { mutableStateOf(false) }
    val title = HomeRailType.from(section.type)?.let { homeRailTitle(it, section.libraryName) } ?: section.type
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) BgCard else BgCard.copy(alpha = ROW_IDLE_ALPHA))
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val isSelect = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (event.type == KeyEventType.KeyDown && isSelect) {
                    onToggle()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text       = "${index + 1}",
            color      = if (focused) Accent else TextSecondary,
            fontSize   = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.width(36.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = title,
                color      = if (section.visible) TextPrimary else TextSecondary,
                fontSize   = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            Text(
                text     = stringResource(if (section.visible) R.string.home_layout_shown else R.string.home_layout_hidden),
                color    = if (section.visible) Accent else TextSecondary,
                fontSize = 12.sp,
            )
        }
        HeroIconButton(
            icon               = if (section.visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
            contentDescription = stringResource(if (section.visible) R.string.home_layout_hide else R.string.home_layout_show),
            onClick            = onToggle,
            size               = ACTION_SIZE,
        )
        Spacer(Modifier.width(8.dp))
        HeroIconButton(
            icon               = Icons.Filled.ArrowUpward,
            contentDescription = stringResource(R.string.home_layout_move_up),
            onClick            = onUp,
            enabled            = !isFirst,
            size               = ACTION_SIZE,
        )
        Spacer(Modifier.width(8.dp))
        HeroIconButton(
            icon               = Icons.Filled.ArrowDownward,
            contentDescription = stringResource(R.string.home_layout_move_down),
            onClick            = onDown,
            enabled            = !isLast,
            size               = ACTION_SIZE,
        )
    }
}

private val ROW_MAX_WIDTH = 760.dp
private val ACTION_SIZE = 36.dp
private const val ROW_IDLE_ALPHA = 0.55f
