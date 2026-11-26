package com.alex.hubplay.ui.shared

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alex.hubplay.R
import com.alex.hubplay.data.Content
import com.alex.hubplay.ui.components.TvShell
import com.alex.hubplay.ui.home.components.CardStyle
import com.alex.hubplay.ui.home.components.HomeRail
import com.alex.hubplay.ui.home.components.LocalSharedBadge
import com.alex.hubplay.ui.home.components.Tab

/**
 * "Compartido contigo": lo que comparten los servidores HubPlay de los
 * amigos, un rail por biblioteca, con "Seguir viendo" arriba. Elegir un
 * título abre su ficha; "Seguir viendo" reanuda directamente (como en
 * Inicio).
 */
@Composable
fun SharedScreen(
    viewModel:     SharedViewModel,
    onOpenItem:    (Content.Shared) -> Unit,
    onResume:      (Content.Shared) -> Unit,
    onTabSelected: (Tab) -> Unit,
    onSettings:    () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()

    // Al volver de la ficha o del reproductor cambia el progreso: se
    // refresca solo "Seguir viendo" (barato), no las bibliotecas.
    LaunchedEffect(Unit) { viewModel.refreshContinueWatching() }

    TvShell(
        selectedTab     = Tab.Shared,
        onNavigateToTab = onTabSelected,
        onOpenSettings  = onSettings,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header()
            when {
                ui.isLoading && ui.rails.isEmpty() -> CenteredSpinner()
                ui.error != null                   -> ErrorBanner(message = ui.error!!, onRetry = viewModel::load)
                ui.isEmpty                         -> EmptyBanner()
                else                               -> SharedRails(ui, onOpenItem, onResume)
            }
        }
    }
}

@Composable
private fun Header() {
    Column(modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 4.dp)) {
        Text(
            text       = stringResource(R.string.shared_title),
            style      = MaterialTheme.typography.headlineMedium,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text  = stringResource(R.string.shared_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SharedRails(
    ui:         SharedUiState,
    onOpenItem: (Content.Shared) -> Unit,
    onResume:   (Content.Shared) -> Unit,
) {
    // Foco inicial en la primera tarjeta: sin esto se queda en el menú
    // lateral y la pantalla parece muerta hasta pulsar →.
    val firstRail = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstRail.requestFocus() } }

    val rows = remember(ui.continueWatching, ui.rails) {
        buildList {
            if (ui.continueWatching.isNotEmpty()) add(SharedRow.Continue(ui.continueWatching))
            ui.rails.forEach { add(SharedRow.Library(it)) }
        }
    }
    val firstFocusable = rows.indexOfFirst { it.focusable }

    // Como en Inicio: el rail con foco se pega arriba (sin el rail
    // anterior cortado a medias bajo la cabecera). Se anula el
    // bring-into-view por defecto y se desplaza a mano al rail enfocado.
    val listState = rememberLazyListState()
    var focusedRow by remember { mutableIntStateOf(-1) }
    LaunchedEffect(focusedRow) {
        if (focusedRow >= 0) listState.animateScrollToItem(focusedRow)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Recorrido extra al final para que también el último rail llegue arriba.
        val bottomPad = (maxHeight - SharedRailHeight).coerceAtLeast(0.dp)
        CompositionLocalProvider(LocalBringIntoViewSpec provides NoVerticalBringIntoView) {
            LazyColumn(
                state               = listState,
                contentPadding      = PaddingValues(start = 8.dp, top = 4.dp, bottom = bottomPad),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier            = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                    Box(modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedRow = index }) {
                        val requester = if (index == firstFocusable) firstRail else null
                        when (row) {
                            is SharedRow.Continue -> HomeRail(
                                title              = stringResource(R.string.shared_continue),
                                items              = row.items,
                                style              = CardStyle.PosterCompact,
                                onFocused          = {},
                                onClick            = onResume,
                                railFocusRequester = requester,
                            )
                            is SharedRow.Library -> LibraryRail(
                                rail         = row.rail,
                                onOpenItem   = onOpenItem,
                                focusRequest = requester,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Filas de la sección: "Seguir viendo" y una por biblioteca compartida. */
private sealed interface SharedRow {
    val key: String
    val focusable: Boolean

    class Continue(val items: List<Content.Shared>) : SharedRow {
        override val key = "continue"
        override val focusable = true
    }

    class Library(val rail: SharedRail) : SharedRow {
        override val key = rail.library.key
        override val focusable = rail.items.isNotEmpty()
    }
}

/**
 * Por debajo del alto real de un rail (título + tarjeta de 180 dp + pie ≈
 * 250 dp): así el último rail siempre puede subir hasta arriba.
 */
private val SharedRailHeight = 200.dp

/** Sin desplazamiento automático: lo hace [SharedRails] al rail enfocado. */
@OptIn(ExperimentalFoundationApi::class)
private val NoVerticalBringIntoView = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

@Composable
private fun LibraryRail(
    rail:         SharedRail,
    onOpenItem:   (Content.Shared) -> Unit,
    focusRequest: FocusRequester?,
) {
    val title = stringResource(R.string.shared_library_title, rail.library.peerName, rail.library.libraryName)
    Column(modifier = Modifier.fillMaxWidth()) {
        when {
            rail.failed -> {
                RailNote(text = title, emphasis = true)
                RailNote(text = stringResource(R.string.shared_library_error), emphasis = false)
            }
            rail.items.isEmpty() -> Unit
            else -> {
                // El título ya dice de quién es: sin la píldora en cada tarjeta.
                CompositionLocalProvider(LocalSharedBadge provides false) {
                    HomeRail(
                        title              = title,
                        items              = rail.items,
                        style              = CardStyle.PosterCompact,
                        onFocused          = {},
                        onClick            = onOpenItem,
                        railFocusRequester = focusRequest,
                    )
                }
                if (rail.fromCache) {
                    RailNote(text = stringResource(R.string.shared_offline, rail.library.peerName), emphasis = false)
                }
            }
        }
    }
}

@Composable
private fun RailNote(text: String, emphasis: Boolean) {
    Text(
        text       = text,
        style      = if (emphasis) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodySmall,
        fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Normal,
        color      = if (emphasis) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier   = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

@Composable
private fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text      = message,
            style     = MaterialTheme.typography.bodyLarge,
            color     = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun EmptyBanner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text  = stringResource(R.string.shared_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
