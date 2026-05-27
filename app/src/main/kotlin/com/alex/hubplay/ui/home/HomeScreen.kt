package com.alex.hubplay.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alex.hubplay.R
import com.alex.hubplay.data.AuthState
import com.alex.hubplay.data.HomeData
import com.alex.hubplay.data.HomeRailConfig
import com.alex.hubplay.data.HomeRailType
import com.alex.hubplay.data.IdleController
import com.alex.hubplay.data.LiveChannel
import com.alex.hubplay.data.Content
import com.alex.hubplay.data.MediaKind
import com.alex.hubplay.ui.home.components.CardStyle
import com.alex.hubplay.ui.home.components.HeroInfo
import com.alex.hubplay.ui.home.components.HomeSidebar
import com.alex.hubplay.ui.livetv.ChannelPreviewPlayer
import com.alex.hubplay.ui.home.components.HomeRail
import com.alex.hubplay.ui.home.components.LiveNowRail
import com.alex.hubplay.ui.home.components.LocalVisibleTabs
import com.alex.hubplay.ui.home.components.Tab
import com.alex.hubplay.data.LocalTrailerHost
import com.alex.hubplay.ui.theme.BgBase

@OptIn(ExperimentalFoundationApi::class)
private val SuppressVerticalBringIntoView = object : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float = 0f
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    viewModel:       HomeViewModel,
    onOpenItem:      (itemId: String, kind: MediaKind) -> Unit,
    onPlayItem:      (itemId: String, resumePosSec: Long) -> Unit,
    onNavigateToTab: (Tab) -> Unit,
    onLogOut:        () -> Unit,
    onOpenSettings:  () -> Unit = {},
    profileName:     String?   = null,
    authState:       AuthState? = null,
    okHttpClient:    okhttp3.OkHttpClient? = null,
    idleController:  IdleController? = null,
) {
    val ui by viewModel.ui.collectAsState()
    // focusedItemForUi (150ms debounce) drive heroItem + backdrop —
    // se siente snappy. focusedItem (500ms debounce) sigue siendo el
    // que activa el trailer fetch, evitando spam durante navegación rápida.
    val focusedItem by viewModel.focusedItemForUi.collectAsState()
    val trailerInfo by viewModel.trailerInfo.collectAsState()

    val isLanding by remember {
        derivedStateOf { focusedItem == null }
    }

    val heroItem by remember {
        derivedStateOf {
            focusedItem ?: ui.data.hero.firstOrNull()
        }
    }

    // El trailer ya no es local: el WebView vive en TrailerHostOverlay (root).
    // Activamos un claim para el item del hero focused; el host decide si
    // recargar (key distinta) o continuar (misma key, viene de otra pantalla).
    val trailerHost = LocalTrailerHost.current
    val activeTrailer = trailerInfo?.takeIf { it.itemId == heroItem?.id }
    val currentHostItemId = trailerHost.current.value?.itemId
    val trailerRevealed = trailerHost.revealed.value && currentHostItemId == heroItem?.id

    DisposableEffect(activeTrailer?.itemId, activeTrailer?.key, activeTrailer?.site) {
        val token = activeTrailer?.let {
            trailerHost.activate(it.itemId, it.key, it.site)
        }
        onDispose { token?.let { trailerHost.deactivate(it) } }
    }

    // Cuando el foco cae en una card SIN trailer (in-screen), forzamos
    // hideNow para que el WebView del trailer anterior desaparezca al
    // instante (audio incluido). Sin esto, el debounce de 500ms del host
    // dejaba la WebView activa medio segundo más, mostrando el end-screen
    // de YouTube con el botón Play sobre la nueva card.
    LaunchedEffect(activeTrailer) {
        if (activeTrailer == null) trailerHost.hideNow()
    }

    // Backdrop alpha asimétrica:
    //  - Trailer REVELANDO (false→true): fade out suave 700ms (al usuario
    //    le gusta ver cómo desaparece el backdrop dejando paso al trailer).
    //  - Trailer OCULTÁNDOSE (true→false): snap inmediato a 1. El backdrop
    //    de la nueva card aparece YA, cubriendo cualquier resto visual
    //    del WebView anterior. Si animáramos los 700ms aquí también, la
    //    WebView del trailer viejo (end-screen gris con play) se vería
    //    a través de la transparencia durante todo ese tiempo.
    val backdropAlpha by animateFloatAsState(
        targetValue = if (trailerRevealed) 0f else 1f,
        animationSpec = if (trailerRevealed) tween(durationMillis = 700) else snap(),
        label = "backdrop-fade",
    )

    // Reportamos la posición que va devolviendo el host al ViewModel
    // — lo usa el NavGraph para pasar &trailerResume= al navegar a Detail.
    LaunchedEffect(Unit) {
        snapshotFlow { trailerHost.currentTimeSec.value }
            .collect { viewModel.onTrailerTimeUpdate(it) }
    }

    DisposableEffect(trailerRevealed) {
        if (trailerRevealed) idleController?.setSuspended(true)
        onDispose { if (trailerRevealed) idleController?.setSuspended(false) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        // Transparente para que se vea el TrailerHostOverlay del root cuando
        // el backdrop se desvanezca (alpha=0). Color sólido cuando hay
        // backdrop opaco (alpha=1): el AsyncImage ya cubre todo.
        color = Color.Transparent,
    ) {
        when {
            ui.isLoading && ui.data.continueWatching.isEmpty()
                && ui.data.trending.isEmpty() -> CenteredSpinner()
            ui.error != null -> ErrorBanner(
                message = ui.error!!,
                onRetry = viewModel::refresh,
            )
            else -> {
                val rails = ui.data.rails
                val scrollSnapshot by viewModel.scrollSnapshot.collectAsState()

                // Inicializamos el LazyListState con la posición vertical
                // guardada por el VM en la sesión anterior. rememberSaveable
                // con el Saver oficial de LazyListState mantiene el scroll
                // dentro de la misma sesión de NavHost; el VM lo cubre
                // contra disposición/recreación de la pantalla entera.
                val listState = rememberSaveable(saver = LazyListState.Saver) {
                    LazyListState(
                        firstVisibleItemIndex       = scrollSnapshot.railIndex,
                        firstVisibleItemScrollOffset = 0,
                    )
                }
                var activeRailIndex by remember { mutableIntStateOf(scrollSnapshot.railIndex) }

                val perRailFocused = remember { mutableMapOf<String, String>() }
                // Un FocusRequester por rail — los crea HomeScreen y se los
                // pasamos a cada `RenderRail`. Al re-entrar la pantalla, el
                // LaunchedEffect de abajo pide foco al requester del rail
                // que tenía el foco antes — fuerza al sistema a meter el
                // foco en los rails (no en el sidebar) y deja que el chain
                // de `focusRestorer`s elija el item correcto dentro.
                val railFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }

                DisposableEffect(Unit) {
                    viewModel.resetFirstFocusGate()
                    perRailFocused.putAll(scrollSnapshot.focusedItemIdByRail)
                    onDispose {
                        // Usamos `activeRailIndex` (que se setea síncronamente
                        // en `onCardFocused`) en vez de
                        // `listState.firstVisibleItemIndex` — éste último
                        // depende de que `LaunchedEffect(activeRailIndex) {
                        // scrollToItem(...) }` se haya ejecutado, lo cual NO
                        // ocurre si el usuario hace foco-en-rail + click muy
                        // rápido (la coroutine se cancela al disponer antes
                        // de completar el scroll). Resultado anterior: railIndex
                        // guardado = 0 (stale), restore a otra rail al volver.
                        viewModel.saveScrollSnapshot(
                            railIndex           = activeRailIndex,
                            focusedItemIdByRail = perRailFocused.toMap(),
                        )
                    }
                }

                // Restauración del foco al re-entrar tras back. Esperamos al
                // primer frame en el que el rail-target esté en la lista de
                // items visibles del LazyColumn — más fiable que un
                // `delay(N)` arbitrario en TVs lentas, y sin race con el
                // initial focus pass del sistema. El sidebar iría por
                // defecto a la lupa (primer focusable del Row) sin esto.
                LaunchedEffect(rails, scrollSnapshot) {
                    val targetIdx = scrollSnapshot.railIndex
                    val rail = rails.getOrNull(targetIdx) ?: return@LaunchedEffect
                    if (!scrollSnapshot.focusedItemIdByRail.containsKey(rail.id)) return@LaunchedEffect
                    snapshotFlow {
                        listState.layoutInfo.visibleItemsInfo.any { it.index == targetIdx }
                    }.first { it }
                    railFocusRequesters[rail.id]?.let { runCatching { it.requestFocus() } }
                }

                LaunchedEffect(activeRailIndex) {
                    // animateScrollToItem en lugar de scrollToItem para que
                    // el movimiento vertical entre rails sea suave estilo
                    // Prime/Netflix — el rail anterior se desliza fuera y
                    // el nuevo entra. Duración por defecto del spring de
                    // LazyList (~350-500ms según distancia).
                    listState.animateScrollToItem(activeRailIndex)
                }

                Box(modifier = Modifier.fillMaxSize()) {

                    // ── Layer 0: Full-screen backdrop ──────────────────
                    Crossfade(
                        targetState = heroItem?.backdropUrl ?: heroItem?.posterUrl,
                        animationSpec = tween(durationMillis = 300),
                        label = "home-backdrop",
                        modifier = Modifier
                            .fillMaxSize()
                            // ORDEN CRÍTICO: alpha PRIMERO, background DESPUÉS.
                            // alpha crea un graphics layer que envuelve a TODO
                            // lo que viene después (incluyendo el background).
                            // Si fuese .background().alpha() el BgBase quedaría
                            // FUERA del layer y se dibujaría siempre a full
                            // opacity, cubriendo la WebView del trailer aunque
                            // alpha=0. Audio sonaría pero video no se vería.
                            .alpha(backdropAlpha)
                            .background(BgBase),
                    ) { url ->
                        if (url != null) {
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    val liveChannelItem = heroItem as? Content.LiveChannel
                    if (liveChannelItem != null && authState != null && okHttpClient != null) {
                        val liveChannel = remember(liveChannelItem.id) {
                            LiveChannel(
                                id = liveChannelItem.id, name = liveChannelItem.title, number = 0,
                                groupName = "", category = "",
                                logoUrl = liveChannelItem.logoUrl,
                                logoInitials = liveChannelItem.logoInitials,
                                logoBg = liveChannelItem.logoBg, logoFg = liveChannelItem.logoFg,
                                libraryId = "", isActive = true,
                                healthStatus = "ok",
                            )
                        }
                        ChannelPreviewPlayer(
                            channel      = liveChannel,
                            authState    = authState,
                            okHttpClient = okHttpClient,
                            modifier     = Modifier.fillMaxSize(),
                            fallback     = {},
                        )
                    }
                    // El trailer YouTube ya NO se monta aquí — lo dibuja el
                    // TrailerHostOverlay en el root (detrás del Surface
                    // transparente). Aquí solo activamos el claim (más arriba)
                    // y bajamos el alpha del backdrop cuando se revela para
                    // este item.

                    // ── Layer 1: Gradient overlays ──────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.55f)
                            .background(
                                Brush.horizontalGradient(
                                    0f to BgBase.copy(alpha = 0.92f),
                                    0.55f to BgBase.copy(alpha = 0.70f),
                                    1f to Color.Transparent,
                                ),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(0.58f)
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.20f to BgBase.copy(alpha = 0.50f),
                                    0.50f to BgBase.copy(alpha = 0.88f),
                                    1f to BgBase,
                                ),
                            ),
                    )

                    // ── Layer 2: Content ────────────────────────────────
                    Row(modifier = Modifier.fillMaxSize()) {

                        val visibleTabs = LocalVisibleTabs.current
                        HomeSidebar(
                            onNavigateToTab = onNavigateToTab,
                            onOpenSearch = { onNavigateToTab(Tab.Search) },
                            onOpenSettings = onOpenSettings,
                            visibleTabs = visibleTabs,
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            // ── Hero info — fixed, top half ───────────
                            HeroInfo(
                                item = heroItem,
                                onPlay = { it?.let { item ->
                                    val resumeSec = (item as? Content.Resumable)?.resumePosSec ?: 0L
                                    onPlayItem(item.id, resumeSec)
                                } },
                                onDetails = { it?.let { item -> onOpenItem(item.id, item.kind) } },
                                showControls = isLanding,
                                // Si volvemos de Detail con un rail con foco
                                // guardado, dejamos que ese rail gane la
                                // carrera del foco inicial; si no, HeroInfo
                                // pide foco al Play como antes.
                                requestInitialFocus = scrollSnapshot.focusedItemIdByRail.isEmpty(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.50f),
                            )

                            // ── Rails — LazyColumn, bottom half ───────
                            @OptIn(ExperimentalFoundationApi::class)
                            CompositionLocalProvider(
                                LocalBringIntoViewSpec provides SuppressVerticalBringIntoView,
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(0.50f),
                                ) {
                                    itemsIndexed(
                                        items = rails,
                                        key = { _, config -> config.id },
                                    ) { index, config ->
                                        val railRequester = railFocusRequesters
                                            .getOrPut(config.id) { FocusRequester() }
                                        Box(
                                            modifier = Modifier
                                                .fillParentMaxHeight(0.88f)
                                                .fillMaxWidth()
                                                .focusGroup()
                                                // Quitamos `focusRestorer()` exterior: tener dos
                                                // capas de focusRestorer (Box + LazyRow dentro)
                                                // causaba el "amago" — la outer asignaba foco
                                                // a Default tras la inner asignar al item-target
                                                // correcto. Resultado: parpadeo y pérdida.
                                                // Usamos `focusProperties.enter` para rutear el
                                                // foco entrante al railRequester de forma
                                                // declarativa (síncrono al layout, sin race).
                                                .focusProperties { enter = { railRequester } },
                                            contentAlignment = Alignment.TopStart,
                                        ) {
                                            RenderRail(
                                                config = config,
                                                data = ui.data,
                                                initialFocusedItemId = scrollSnapshot
                                                    .focusedItemIdByRail[config.id],
                                                railFocusRequester = railRequester,
                                                onCardFocused = { item ->
                                                    viewModel.onCardFocused(item)
                                                    activeRailIndex = index
                                                    perRailFocused[config.id] = item.id
                                                },
                                                onOpenItem = onOpenItem,
                                                onPlayItem = onPlayItem,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderRail(
    config:               HomeRailConfig,
    data:                 HomeData,
    onCardFocused:        (Content) -> Unit,
    onOpenItem:           (String, MediaKind) -> Unit,
    onPlayItem:           (String, Long) -> Unit,
    initialFocusedItemId: String? = null,
    railFocusRequester:   FocusRequester? = null,
) {
    when (config.type) {
        HomeRailType.ContinueWatching -> HomeRail(
            title = config.title,
            items = data.continueWatching,
            style = CardStyle.Landscape,
            onFocused = onCardFocused,
            onClick = { onPlayItem(it.id, it.resumePosSec) },
            initialFocusedItemId = initialFocusedItemId,
            railFocusRequester = railFocusRequester,
        )
        HomeRailType.NextUp -> HomeRail(
            title = config.title,
            items = data.nextUp,
            style = CardStyle.Landscape,
            onFocused = onCardFocused,
            onClick = { onPlayItem(it.id, 0L) },
            initialFocusedItemId = initialFocusedItemId,
            railFocusRequester = railFocusRequester,
        )
        HomeRailType.Trending -> HomeRail(
            title = config.title,
            items = data.trending,
            style = CardStyle.Landscape,
            onFocused = onCardFocused,
            onClick = { onOpenItem(it.id, it.kind) },
            initialFocusedItemId = initialFocusedItemId,
            railFocusRequester = railFocusRequester,
        )
        HomeRailType.LatestInLibrary -> HomeRail(
            title = config.title,
            items = data.latestByRailId[config.id].orEmpty(),
            style = CardStyle.Landscape,
            onFocused = onCardFocused,
            onClick = { onOpenItem(it.id, it.kind) },
            initialFocusedItemId = initialFocusedItemId,
            railFocusRequester = railFocusRequester,
        )
        HomeRailType.LiveNow -> LiveNowRail(
            title = config.title,
            items = data.liveNow,
            onFocused = onCardFocused,
            onClick = { onPlayItem(it.id, 0L) },
            initialFocusedItemId = initialFocusedItemId,
            railFocusRequester = railFocusRequester,
        )
    }
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
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}
