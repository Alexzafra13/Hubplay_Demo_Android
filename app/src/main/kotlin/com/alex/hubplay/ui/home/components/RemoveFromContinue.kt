package com.alex.hubplay.ui.home.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.alex.hubplay.R
import com.alex.hubplay.ui.theme.BgBase
import com.alex.hubplay.ui.theme.BgElevated
import com.alex.hubplay.ui.theme.Border

/**
 * Pulsación larga de OK (centro del mando o Enter) sobre cualquier tarjeta
 * de lo que envuelve. Se pone en el rail, no en cada tarjeta: los eventos
 * de tecla pasan por los ancestros del foco en la fase "preview". Se traga
 * la repetición y el KeyUp que sigue, para que no salte además el click.
 */
fun Modifier.onLongSelect(onLongSelect: () -> Unit): Modifier = composed {
    var consuming by remember { mutableStateOf(false) }
    onPreviewKeyEvent { event ->
        val isSelect = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        when {
            !isSelect -> false
            event.type == KeyEventType.KeyDown && event.nativeKeyEvent.isLongPress -> {
                consuming = true
                onLongSelect()
                true
            }
            consuming && event.type == KeyEventType.KeyDown -> true
            consuming && event.type == KeyEventType.KeyUp -> {
                consuming = false
                true
            }
            else -> false
        }
    }
}

/**
 * "¿Quitar de Continuar viendo?": no marca como visto ni borra el
 * historial, solo deja de enseñarlo ahí (`DELETE /me/continue-watching/{id}`).
 *
 * Overlay dentro del árbol de Inicio y no `Dialog`, por lo mismo que
 * Identificar: una ventana nueva costaba 0,5-0,9 s en el TV box y al
 * cerrarse dejaba la pantalla sin foco. El foco queda atrapado dentro,
 * nace en Cancelar (un OK de más no quita nada) y Atrás lo cierra.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RemoveFromContinueOverlay(
    title:     String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { cancelFocus.requestFocus() }
    }
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .zIndex(OVERLAY_Z_INDEX)
            .background(BgBase.copy(alpha = SCRIM_ALPHA))
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape    = RoundedCornerShape(16.dp),
            color    = BgElevated,
            modifier = Modifier
                .width(PANEL_WIDTH)
                .border(1.dp, Border, RoundedCornerShape(16.dp)),
        ) {
            Column(
                modifier            = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text       = stringResource(R.string.continue_remove_title),
                    style      = MaterialTheme.typography.titleLarge,
                    color      = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text  = stringResource(R.string.continue_remove_body, title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    OverlayAction(
                        text     = stringResource(R.string.action_cancel),
                        onClick  = onDismiss,
                        modifier = Modifier.focusRequester(cancelFocus),
                    )
                    OverlayAction(text = stringResource(R.string.continue_remove_confirm), onClick = onConfirm)
                }
            }
        }
    }
}

/**
 * Botón con un foco que se ve a 3 metros (relleno blanco, como Reproducir
 * en las fichas): el estado de foco de Material es un velo casi invisible
 * en la tele.
 */
@Composable
private fun OverlayAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    TextButton(
        onClick  = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        colors   = ButtonDefaults.textButtonColors(
            containerColor = if (focused) Color.White else Color.Transparent,
            contentColor   = if (focused) Color.Black else MaterialTheme.colorScheme.primary,
        ),
    ) {
        Text(text = text, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium)
    }
}

private const val OVERLAY_Z_INDEX = 10f
private const val SCRIM_ALPHA = 0.78f
private val PANEL_WIDTH = 560.dp
