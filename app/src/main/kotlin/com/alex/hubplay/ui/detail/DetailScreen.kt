@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)

package com.alex.hubplay.ui.detail

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.alex.hubplay.R
import com.alex.hubplay.data.CollectionDetail
import com.alex.hubplay.data.Content
import com.alex.hubplay.data.IdentifyCandidate
import com.alex.hubplay.data.LocalTrailerHost
import com.alex.hubplay.data.MediaKind
import com.alex.hubplay.data.Person
import com.alex.hubplay.ui.catalog.PortraitCatalogCard
import com.alex.hubplay.ui.components.BackPill
import com.alex.hubplay.ui.components.HeroCtaButton
import com.alex.hubplay.ui.components.HeroIconButton
import com.alex.hubplay.ui.components.trailerBackdropAlphaSpec
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.BgBase
import com.alex.hubplay.ui.theme.BgElevated
import com.alex.hubplay.ui.theme.Border
import kotlinx.coroutines.delay

/**
 * Movie detail surface — same cinematic Netflix-style hero pattern as
 * the SeriesScreen, but adapted for movies:
 *
 *   - Backdrop fullscreen as the ambient layer.
 *   - Optional YouTube trailer overlay (same HeroTrailerView the
 *     series uses) — fades the backdrop out when it reveals.
 *   - Top-left: back pill + "HubPlay PELÍCULAS" brand row.
 *   - Centre-left split into:
 *       * Poster (vertical, 160dp wide) — movies keep the cover art
 *         visible because the cover is itself a strong visual signal
 *         the user reads from the home rails. Series rely on the
 *         studio logo for the same job.
 *       * Info column: studio logo (if available) or title text, the
 *         tagline as subtitle, meta row, overview, and the Play / Mi
 *         lista CTAs stacked vertically.
 */
@Composable
fun DetailScreen(
    viewModel:          DetailViewModel,
    onPlay:             (itemId: String, resumePosSec: Long) -> Unit,
    onBack:             () -> Unit,
    onOpenCollection:   (collectionId: String) -> Unit = {},
    onOpenPerson:       (personId: String) -> Unit = {},
    onOpenItem:         (itemId: String, kind: MediaKind) -> Unit = { _, _ -> },
    onOpenStudio:       (studioSlug: String) -> Unit = {},
    trailerResumeSec:   Long = 0L,
) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current

    // Avisos de las acciones de metadatos: un Toast basta en TV (no hay
    // snackbar host en esta pantalla y el mensaje es de un segundo).
    LaunchedEffect(ui.notice) {
        val notice = ui.notice ?: return@LaunchedEffect
        Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
        viewModel.clearNotice()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        val item = ui.item
        when {
            ui.isLoading     -> CenteredSpinner()
            ui.error != null -> ErrorBanner(message = ui.error!!, onRetry = viewModel::load)
            item != null     -> {
                val actions = remember(viewModel, onPlay, onBack) {
                    DetailActions(
                        onPlay            = onPlay,
                        onBack            = onBack,
                        onToggleFavorite  = viewModel::toggleFavorite,
                        onToggleWatched   = viewModel::toggleWatched,
                        onRefreshMetadata = viewModel::refreshMetadata,
                        onIdentify        = viewModel::openIdentify,
                    )
                }
                val nav = remember(onOpenCollection, onOpenStudio, onOpenPerson, onOpenItem) {
                    DetailNav(
                        onOpenCollection = onOpenCollection,
                        onOpenStudio     = onOpenStudio,
                        onOpenPerson     = onOpenPerson,
                        onOpenItem       = onOpenItem,
                    )
                }
                DetailContent(
                    item             = item,
                    ui               = ui,
                    actions          = actions,
                    nav              = nav,
                    trailerResumeSec = trailerResumeSec,
                )
                ui.identify?.let { state ->
                    IdentifyDialog(
                        state     = state,
                        onSearch  = viewModel::searchCandidates,
                        onPick    = viewModel::applyIdentify,
                        onRefresh = {
                            viewModel.closeIdentify()
                            viewModel.refreshMetadata()
                        },
                        onDismiss = viewModel::closeIdentify,
                    )
                }
            }
        }
    }
}

/** Acciones sobre el item, agrupadas para no arrastrar diez lambdas por cada composable. */
@androidx.compose.runtime.Immutable
private class DetailActions(
    val onPlay:            (String, Long) -> Unit,
    val onBack:            () -> Unit,
    val onToggleFavorite:  () -> Unit,
    val onToggleWatched:   () -> Unit,
    val onRefreshMetadata: () -> Unit,
    val onIdentify:        () -> Unit,
)

/** Navegación a otras pantallas desde la ficha (chips, reparto, relacionados). */
@androidx.compose.runtime.Immutable
private class DetailNav(
    val onOpenCollection: (String) -> Unit,
    val onOpenStudio:     (String) -> Unit,
    val onOpenPerson:     (String) -> Unit,
    val onOpenItem:       (String, MediaKind) -> Unit,
)

/**
 * Cuerpo de la ficha: backdrop + tráiler FIJOS detrás (capa 0) y, encima,
 * una columna que hace scroll con el hero (un viewport de alto) y los
 * rails de reparto / relacionados.
 *
 * Reglas de foco y scroll, en este orden de prioridad:
 *  - El foco que entra en el hero va a Reproducir (`enter`), y mientras
 *    cualquier cosa del hero tenga el foco la página está arriba del todo
 *    (`heroHasFocus` → `animateScrollTo(0)`): moverse entre Volver, Play y
 *    las acciones nunca desplaza la pantalla.
 *  - Bajar al reparto desplaza (bringIntoView normal); subir desde el
 *    primer rail vuelve a Reproducir (`up`), no a la flecha de volver.
 *  - Los rails se componen dos frames después del hero: foco inicial
 *    determinista y primer frame más barato con el tráiler debajo.
 */
@Composable
private fun DetailContent(
    item:             Content,
    ui:               DetailUiState,
    actions:          DetailActions,
    nav:              DetailNav,
    trailerResumeSec: Long,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val heroHeight  = maxHeight
        val scrollState = rememberScrollState()
        val playFocus   = remember { FocusRequester() }
        LaunchedEffect(item.id) {
            runCatching { playFocus.requestFocus() }
        }

        var railsReady by remember(item.id) { mutableStateOf(false) }
        LaunchedEffect(item.id) {
            withFrameNanos { }
            withFrameNanos { }
            railsReady = true
        }

        var heroHasFocus by remember { mutableStateOf(false) }
        LaunchedEffect(heroHasFocus) {
            if (heroHasFocus && scrollState.value != 0) scrollState.animateScrollTo(0)
        }

        val people   = peopleOf(item)
        val hasRails = people.isNotEmpty() || ui.related.isNotEmpty() || ui.collection != null

        DetailBackdrop(item = item, trailerResumeSec = trailerResumeSec)

        // El spec por defecto de Compose en Android TV pivota CADA foco al
        // 30 % del viewport: mover el foco entre Reproducir y las acciones
        // desplazaba la página ("se me baja"). Con DetailBringIntoViewSpec,
        // lo que ya se ve no mueve nada; solo lo que está fuera (bajar al
        // reparto) desplaza, y ahí sí pivotamos para que el rail suba entero.
        CompositionLocalProvider(LocalBringIntoViewSpec provides DetailBringIntoViewSpec) {
            Column(modifier = Modifier.verticalScroll(scrollState)) {
                Box(
                    modifier = Modifier
                        .height(heroHeight)
                        .focusProperties { enter = { playFocus } }
                        .onFocusChanged { heroHasFocus = it.hasFocus },
                ) {
                    HeroFull(
                        item            = item,
                        canEditMetadata = ui.canEditMetadata,
                        actions         = actions,
                        nav             = nav,
                        playFocus       = playFocus,
                    )
                }
                if (railsReady && hasRails) {
                    RailsSection(
                        people     = people,
                        collection = ui.collection,
                        related    = ui.related,
                        currentId  = item.id,
                        nav        = nav,
                        playFocus  = playFocus,
                    )
                }
            }
        }
    }
}

/**
 * Bring-into-view de la ficha: 0 si el hijo ya está entero en pantalla
 * (moverse por el hero no desplaza), y si está fuera lo trae al 30 % del
 * alto (mismo pivote que usa Android TV por defecto) sin pasarse del
 * recorrido disponible.
 */
private val DetailBringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        val visible = offset >= 0f && offset + size <= containerSize
        if (visible || size >= containerSize) return 0f
        return offset - containerSize * RAIL_PIVOT_FRACTION
    }
}

/**
 * Reparto + relacionados sobre fondo sólido. Arriba, una franja con
 * degradado transparente → BgBase: al bajar, el backdrop (o el tráiler)
 * que sigue fijo detrás se funde en el fondo en vez de cortarse a negro.
 * El tráiler NO se para al bajar: queda tapado por esta sección y sigue
 * visible por la parte superior mientras haya hero a la vista.
 */
@Composable
private fun RailsSection(
    people:     List<Person>,
    collection: CollectionDetail?,
    related:    List<Content>,
    currentId:  String,
    nav:        DetailNav,
    playFocus:  FocusRequester,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(RAILS_FADE_HEIGHT)
                .background(Brush.verticalGradient(0f to Color.Transparent, 1f to BgBase)),
        )
        Column(modifier = Modifier.fillMaxWidth().background(BgBase)) {
            // Solo el PRIMER rail rutea ↑ a Reproducir; el segundo sube al
            // primero de forma natural. Se intercepta la tecla (preview) en
            // vez de `focusProperties { up }`: el LazyRow de dentro es un
            // focus group y la propiedad heredada no llegaba a las cards,
            // así que el motor de foco elegía la píldora más cercana de
            // la fila de acciones.
            val upToPlay = Modifier.onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                    runCatching { playFocus.requestFocus() }.isSuccess
                } else {
                    false
                }
            }
            var first = true
            fun railModifier(): Modifier {
                if (!first) return Modifier
                first = false
                return upToPlay
            }
            if (people.isNotEmpty()) {
                CastCrewRail(people = people, onOpenPerson = nav.onOpenPerson, modifier = railModifier())
            }
            // Saga: sus otras películas + una tarjeta final que abre la
            // colección entera. La actual se omite (ya estás en ella).
            if (collection != null) {
                PosterRail(
                    title      = stringResource(R.string.detail_section_collection, collectionDisplayName(collection.name)),
                    items      = collection.items.filter { it.id != currentId },
                    onOpenItem = nav.onOpenItem,
                    modifier   = railModifier(),
                    trailing   = { OpenCollectionCard(onClick = { nav.onOpenCollection(collection.id) }) },
                )
            }
            if (related.isNotEmpty()) {
                PosterRail(
                    title      = stringResource(R.string.detail_section_related),
                    items      = related,
                    onOpenItem = nav.onOpenItem,
                    modifier   = railModifier(),
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * TMDb llama a las sagas "X - Colección" / "X Collection"; en el título del
 * rail ya decimos "Forma parte de la colección", así que se quita el sufijo.
 */
private fun collectionDisplayName(name: String): String =
    name.replace(COLLECTION_SUFFIX, "").trim().ifEmpty { name }

private val COLLECTION_SUFFIX = Regex("""\s*[-–:]?\s*(colecci[oó]n|collection)\s*$""", RegexOption.IGNORE_CASE)

/** Cast + crew of the variants that carry it; empty for everything else. */
private fun peopleOf(item: Content): List<Person> = when (item) {
    is Content.Movie   -> item.people
    is Content.Series  -> item.people
    is Content.Episode -> item.people
    else               -> emptyList()
}

/**
 * Capa fija detrás del scroll: backdrop (con el crossfade al tráiler) y los
 * degradados de legibilidad. El tráiler no se monta aquí — vive en
 * TrailerHostOverlay (root); aquí solo se registra el claim y se baja el
 * alpha del backdrop cuando el host lo revela para ESTE item.
 */
@Composable
private fun DetailBackdrop(item: Content, trailerResumeSec: Long) {
    // Only Movies and Series ever carry a trailer pair on /items/{id}.
    val trailerKey  = (item as? Content.Movie)?.trailerKey  ?: (item as? Content.Series)?.trailerKey
    val trailerSite = (item as? Content.Movie)?.trailerSite ?: (item as? Content.Series)?.trailerSite

    // Si llegamos desde Home con el tráiler sonando para este mismo item,
    // el host detecta misma key y NO recarga — el vídeo sigue sin corte.
    val trailerHost = LocalTrailerHost.current
    val trailerRevealed = trailerHost.revealed.value &&
        trailerHost.current.value?.itemId == item.id

    DisposableEffect(item.id, trailerKey, trailerSite, trailerResumeSec) {
        // trailerResumeSec solo se usa si la key es NUEVA (deep link o item
        // distinto al que sonaba); con la misma key el WebView ni se entera.
        val token = if (trailerKey != null && trailerSite != null) {
            trailerHost.activate(
                itemId     = item.id,
                videoKey   = trailerKey,
                site       = trailerSite,
                startAtSec = trailerResumeSec,
            )
        } else null
        onDispose { token?.let { trailerHost.deactivate(it) } }
    }

    val backdropAlpha by animateFloatAsState(
        targetValue   = if (trailerRevealed) 0f else 1f,
        animationSpec = trailerBackdropAlphaSpec(trailerRevealed, trailerHost.fadeOutOnHide.value),
        label         = "backdrop-fade",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model              = item.backdropUrl ?: item.posterUrl,
            contentDescription = item.title,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .fillMaxSize()
                .alpha(backdropAlpha),
        )
        // Left fade so info reads against the backdrop.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f    to BgBase.copy(alpha = 0.92f),
                        0.55f to BgBase.copy(alpha = 0.40f),
                        1f    to Color.Transparent,
                    ),
                ),
        )
        // Subtle bottom vertical fade.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to BgBase.copy(alpha = 0.55f),
                    ),
                ),
        )
    }
}

/**
 * Hero de un viewport de alto: fila de volver + marca arriba a la
 * izquierda y, centrado, el bloque de dos columnas (póster + Reproducir |
 * info + acciones). Las acciones secundarias (Mi lista, Visto, Información
 * y las de metadatos) viven en la columna de info, bajo la sinopsis — el
 * patrón Plex/Jellyfin — y no en la esquina superior: así el foco no se
 * va a la flecha de volver al subir desde el reparto.
 */
@Composable
private fun HeroFull(
    item:            Content,
    canEditMetadata: Boolean,
    actions:         DetailActions,
    nav:             DetailNav,
    playFocus:       FocusRequester,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // ── Back + brand row, top-left ─────────────────────────────────────
        Row(
            modifier          = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 20.dp)
                .zIndex(10f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackPill(onBack = actions.onBack)
            Spacer(Modifier.width(16.dp))
            Image(
                painter            = painterResource(R.drawable.brand_wordmark),
                contentDescription = stringResource(R.string.brand_hubplay),
                modifier           = Modifier.height(28.dp),
            )
        }

        // ── Two-column layout: poster + Play + iconos | info ───────────────
        Row(
            modifier              = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.8f)
                // top: reserva la fila de volver + marca; con póster, Play e
                // iconos la columna izquierda ya no cabe centrada sin pisarla.
                .padding(start = 48.dp, end = 24.dp, top = HERO_HEADER_HEIGHT),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            PosterAndPlayColumn(
                item            = item,
                actions         = actions,
                canEditMetadata = canEditMetadata,
                playFocus       = playFocus,
            )
            InfoColumn(item = item, nav = nav)
        }
    }
}

@Composable
private fun PosterAndPlayColumn(
    item:            Content,
    actions:         DetailActions,
    canEditMetadata: Boolean,
    playFocus:       FocusRequester,
) {
    val resumePosSec = (item as? Content.Resumable)?.resumePosSec ?: 0L
    val playLabel = if (resumePosSec > 0) {
        val mins = resumePosSec / 60
        val secs = resumePosSec % 60
        stringResource(R.string.detail_resume_format, mins, secs)
    } else stringResource(R.string.detail_play)

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Poster — drops gracefully when the item has no poster_url.
        if (item.posterUrl != null) {
            Box(
                modifier = Modifier
                    .width(POSTER_WIDTH)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model              = item.posterUrl,
                    contentDescription = item.title,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            }
        }
        // Play takes the full poster width — clean, unambiguous, and
        // the label never gets truncated on smaller viewports. Debajo,
        // las acciones secundarias como iconos redondos (Netflix/Plex TV):
        // sin texto salvo una línea pequeña con el nombre del enfocado.
        HeroCtaButton(
            label          = playLabel,
            icon           = Icons.Default.PlayArrow,
            primary        = true,
            focusRequester = playFocus,
            onClick        = { actions.onPlay(item.id, resumePosSec) },
            modifier       = Modifier.width(POSTER_WIDTH),
        )
        QuickActions(item = item, actions = actions, canEditMetadata = canEditMetadata)
    }
}

@Composable
private fun InfoColumn(item: Content, nav: DetailNav) {
    Column(
        modifier            = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Center,
    ) {
        // Studio logo preferred over title text when the server has one
        // for the movie. Same fallback chain as SeriesScreen.
        if (!item.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model              = item.logoUrl,
                contentDescription = item.title,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier
                    .heightIn(min = 64.dp, max = 120.dp)
                    .widthIn(max = 480.dp),
            )
        } else {
            Text(
                text       = item.title,
                style      = MaterialTheme.typography.displayMedium,
                color      = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
            )
        }

        // Smart cast doesn't apply to interface properties (open getter),
        // so we read the nullable once into a local and let Kotlin narrow
        // it inside the let block.
        item.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
            Spacer(Modifier.height(10.dp))
            Text(
                text     = sub,
                style    = MaterialTheme.typography.titleMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(14.dp))
        MetaRow(item)

        item.overview?.takeIf { it.isNotBlank() }?.let { ov ->
            Spacer(Modifier.height(14.dp))
            ExpandableOverview(text = ov)
        }

        MetaChips(item = item, onOpenStudio = nav.onOpenStudio)
    }
}

/**
 * Sinopsis recortada a [OVERVIEW_MAX_LINES] con un "Ver más" enfocable
 * que la despliega ahí mismo (sustituye al diálogo de Información). El
 * enlace solo aparece si el texto realmente se recorta.
 */
@Composable
private fun ExpandableOverview(text: String) {
    var expanded  by remember(text) { mutableStateOf(false) }
    var truncated by remember(text) { mutableStateOf(false) }
    var focused   by remember { mutableStateOf(false) }
    Text(
        text             = text,
        style            = MaterialTheme.typography.bodyMedium,
        color            = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines         = if (expanded) OVERVIEW_MAX_LINES_EXPANDED else OVERVIEW_MAX_LINES,
        overflow         = TextOverflow.Ellipsis,
        onTextLayout     = { if (!expanded) truncated = it.hasVisualOverflow },
    )
    if (truncated || expanded) {
        Spacer(Modifier.height(4.dp))
        Text(
            text       = stringResource(if (expanded) R.string.detail_overview_less else R.string.detail_overview_more),
            style      = MaterialTheme.typography.bodyMedium,
            color      = if (focused) Accent else MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier
                .clip(RoundedCornerShape(6.dp))
                .onFocusChanged { focused = it.isFocused }
                .then(if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(6.dp)) else Modifier)
                .clickable { expanded = !expanded }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * Fila de iconos bajo Reproducir: favorito, visto y — solo con permiso de
 * metadatos en películas/series — el lápiz que abre Identificar (que
 * también ofrece "Actualizar metadatos"). Sin etiquetas fijas: una línea
 * pequeña bajo los iconos dice qué hace el que tiene el foco.
 */
@Composable
private fun QuickActions(
    item:            Content,
    actions:         DetailActions,
    canEditMetadata: Boolean,
) {
    val isFavorite = (item as? Content.Movie)?.isFavorite
        ?: (item as? Content.Series)?.isFavorite
        ?: (item as? Content.Episode)?.isFavorite
        ?: false
    // Only Movies / Series / Episodes carry a watched flag (and mark-played).
    val watched = (item as? Content.Movie)?.watched
        ?: (item as? Content.Series)?.watched
        ?: (item as? Content.Episode)?.watched
    val canIdentify = item is Content.Movie || item is Content.Series

    var focusedLabel by remember { mutableStateOf<Int?>(null) }
    val favoriteLabel = if (isFavorite) R.string.detail_action_favorite_remove else R.string.detail_action_favorite_add
    val watchedLabel  = if (watched == true) R.string.detail_action_mark_unwatched else R.string.detail_action_mark_watched

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionIcon(
                icon    = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label   = favoriteLabel,
                onClick = actions.onToggleFavorite,
                onFocus = { focusedLabel = if (it) favoriteLabel else null },
            )
            if (watched != null) {
                QuickActionIcon(
                    icon    = if (watched) Icons.Default.Check else Icons.Outlined.VisibilityOff,
                    label   = watchedLabel,
                    onClick = actions.onToggleWatched,
                    onFocus = { focusedLabel = if (it) watchedLabel else null },
                )
            }
            if (canEditMetadata && canIdentify) {
                QuickActionIcon(
                    icon    = Icons.Outlined.Edit,
                    label   = R.string.detail_action_metadata,
                    onClick = actions.onIdentify,
                    onFocus = { focusedLabel = if (it) R.string.detail_action_metadata else null },
                )
            }
        }
        Text(
            text     = focusedLabel?.let { stringResource(it) }.orEmpty(),
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(POSTER_WIDTH).height(QUICK_ACTION_CAPTION_HEIGHT),
        )
    }
}

@Composable
private fun QuickActionIcon(
    icon:    ImageVector,
    label:   Int,
    onClick: () -> Unit,
    onFocus: (Boolean) -> Unit,
) {
    HeroIconButton(
        icon               = icon,
        contentDescription = stringResource(label),
        onClick            = onClick,
        modifier           = Modifier.onFocusChanged { onFocus(it.isFocused) },
    )
}

/**
 * The "Parte de [Saga]" + "Estudio: X" chips under the overview. Emitted
 * straight into the info [Column] (ColumnScope receiver) so the spacers
 * flow with the rest. Each hides itself when its data is absent.
 */
@Composable
private fun ColumnScope.MetaChips(
    item:         Content,
    onOpenStudio: (String) -> Unit,
) {
    val studioName = (item as? Content.Movie)?.studioName ?: (item as? Content.Series)?.studioName
    val studioSlug = (item as? Content.Movie)?.studioSlug ?: (item as? Content.Series)?.studioSlug
    if (!studioSlug.isNullOrBlank() && !studioName.isNullOrBlank()) {
        Spacer(Modifier.height(10.dp))
        StudioChip(name = studioName, onClick = { onOpenStudio(studioSlug) })
    }
}

@Composable
private fun MetaRow(item: Content) {
    val durationSec = (item as? Content.Resumable)?.durationSec ?: 0L
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item.rating?.let {
            Text(
                text       = "★ ${"%.1f".format(it)}",
                style      = MaterialTheme.typography.bodyMedium,
                color      = Accent,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
            )
        }
        item.year?.let {
            Text(
                text     = it.toString(),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
        }
        if (durationSec > 0) {
            // Separador solo si hay algo delante (sin año ni nota, la
            // duración es el primer dato y un "· 140 min" suelto queda mal).
            if (item.year != null || item.rating != null) {
                Text("·", style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text     = stringResource(R.string.detail_duration_minutes, durationSec / 60),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
        }
        item.genres.take(3).forEach { genre ->
            Text("·", style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text     = genre,
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "Estudio: X" pill — jumps to the studio's catalogue. Sibling of [PartOfChip]. */
@Composable
private fun StudioChip(name: String, onClick: () -> Unit) {
    Surface(
        color    = MaterialTheme.colorScheme.surface,
        shape    = RoundedCornerShape(999.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier              = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector        = Icons.Outlined.Business,
                contentDescription = null,
                tint               = Accent,
                modifier           = Modifier.size(16.dp),
            )
            Text(
                text       = stringResource(R.string.detail_studio_chip, name),
                style      = MaterialTheme.typography.labelLarge,
                color      = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** Focus "pop" of a cast avatar — matches the MediaCard feel on TV. */
private const val CAST_FOCUS_SCALE = 1.08f

/** Franja de fundido backdrop → fondo sólido al bajar a los rails. */
private val RAILS_FADE_HEIGHT = 140.dp

/** Líneas de sinopsis en el hero; "Ver más" la despliega hasta [OVERVIEW_MAX_LINES_EXPANDED]. */
private const val OVERVIEW_MAX_LINES = 4
private const val OVERVIEW_MAX_LINES_EXPANDED = 12

/** Alto reservado arriba del hero para la píldora de volver y la marca. */
private val HERO_HEADER_HEIGHT = 72.dp

/** Ancho de la tarjeta "Ver colección" (igual que un póster del catálogo). */
private val OPEN_COLLECTION_CARD_WIDTH = 120.dp

/** Ancho del póster y de la columna de Reproducir + iconos. */
private val POSTER_WIDTH = 190.dp

/** Línea reservada bajo los iconos para el nombre de la acción enfocada. */
private val QUICK_ACTION_CAPTION_HEIGHT = 18.dp

/** Alto máximo de la lista de candidatos del diálogo Identificar. */
private val IDENTIFY_LIST_MAX_HEIGHT = 330.dp

/** Ancho del diálogo Identificar (el Dialog no usa el ancho por defecto de la plataforma). */
private val IDENTIFY_DIALOG_WIDTH = 760.dp

private const val YEAR_DIGITS = 4

/** Reintento del cierre del teclado del TV al abrir Identificar. */
private const val KEYBOARD_HIDE_RETRY_MS = 150L

/** Al traer un rail a la vista, su borde superior queda a este alto del viewport. */
private const val RAIL_PIVOT_FRACTION = 0.3f

/**
 * Rail de pósters con título ("Forma parte de la colección X", "Más como
 * esto"). Reutiliza la card del catálogo, así que abrir una lleva al
 * Detalle real. [trailing] permite cerrar el rail con una tarjeta extra.
 */
@Composable
private fun PosterRail(
    title:      String,
    items:      List<Content>,
    onOpenItem: (String, MediaKind) -> Unit,
    modifier:   Modifier = Modifier,
    trailing:   (@Composable () -> Unit)? = null,
) {
    Column(
        modifier            = modifier
            .fillMaxWidth()
            .padding(start = 48.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text       = title,
            style      = MaterialTheme.typography.titleMedium,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.id }) { entry ->
                PortraitCatalogCard(entry, onOpenItem)
            }
            if (trailing != null) {
                item(key = "trailing") { trailing() }
            }
        }
    }
}

/** Última tarjeta del rail de saga: abre la pantalla de la colección. */
@Composable
private fun OpenCollectionCard(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier            = Modifier
            .width(OPEN_COLLECTION_CARD_WIDTH)
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (focused) 0.9f else 0.5f))
            .then(if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(10.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector        = Icons.Outlined.Collections,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onBackground,
            modifier           = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text      = stringResource(R.string.detail_collection_open),
            style     = MaterialTheme.typography.titleSmall,
            color     = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * "Reparto y equipo" — a horizontal strip of circular cast/crew avatars
 * pinned to the bottom of the hero (the Plex / Prime detail pattern).
 * Each card taps through to the PersonDetail screen.
 */
@Composable
private fun CastCrewRail(
    people:       List<Person>,
    onOpenPerson: (String) -> Unit,
    modifier:     Modifier = Modifier,
) {
    Column(
        modifier            = modifier
            .fillMaxWidth()
            .padding(start = 48.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text       = stringResource(R.string.detail_section_cast),
            style      = MaterialTheme.typography.titleMedium,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(people, key = { it.id }) { person ->
                CastCard(person = person, onClick = { onOpenPerson(person.id) })
            }
        }
    }
}

@Composable
private fun CastCard(person: Person, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (focused) CAST_FOCUS_SCALE else 1f,
        animationSpec = tween(durationMillis = 180),
        label         = "cast-scale",
    )
    Column(
        modifier            = Modifier
            .width(96.dp)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier         = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(BgElevated)
                .then(if (focused) Modifier.border(2.dp, Accent, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (person.imageUrl != null) {
                AsyncImage(
                    model              = person.imageUrl,
                    contentDescription = person.name,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text  = person.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text      = person.name,
            style     = MaterialTheme.typography.labelMedium,
            color     = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        val sub = person.character?.takeIf { it.isNotBlank() }
            ?: when (person.role?.lowercase()) {
                "director" -> stringResource(R.string.person_role_director)
                "writer"   -> stringResource(R.string.person_role_writer)
                "actor"    -> stringResource(R.string.person_role_actor)
                else       -> null
            }
        sub?.let {
            Text(
                text      = it,
                style     = MaterialTheme.typography.labelSmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The "⋮" overflow affordance and its dropdown. Two honest actions:
 *
 *  - Mark watched / unwatched — only when the variant carries a played
 *    flag (movies, series, episodes). Backed by /me/progress/{id}/played
 *    and its unplayed sibling.
 *  - Información — raises the Plex-style full-info dialog.
 *
 * No "delete": the server exposes no item-deletion route, and wiping
 * library media from a lean-back TV remote is the wrong place for a
 * destructive action anyway.
 */
/**
 * "Identificar": buscar el item en TMDb y aplicar el match correcto
 * (sobrescribe título, sinopsis, reparto e imágenes). Mismo flujo que el
 * modal de la web, adaptado a D-pad: el foco arranca en el primer
 * resultado (no en el campo de texto, para que el teclado del TV no se
 * abra solo); el usuario sube al campo si quiere afinar la búsqueda.
 */
@Composable
private fun IdentifyDialog(
    state:     IdentifyState,
    onSearch:  (String, Int?) -> Unit,
    onPick:    (String) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val firstResultFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.candidates) {
        if (state.candidates.isEmpty()) return@LaunchedEffect
        runCatching { firstResultFocus.requestFocus() }
        // El foco inicial del Dialog cae un instante en el campo de texto y
        // el TV abre su teclado; al mover el foco a la lista no se cierra
        // solo. Cerrar con reintento, como hace Login (el IME a veces
        // ignora la primera petición mientras aún se está mostrando).
        keyboard?.hide()
        delay(KEYBOARD_HIDE_RETRY_MS)
        keyboard?.hide()
        delay(KEYBOARD_HIDE_RETRY_MS * 2)
        keyboard?.hide()
    }
    // usePlatformDefaultWidth = false: el ancho por defecto del Dialog en
    // TV (~440 dp) dejaba el campo de título en 120 dp.
    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape    = RoundedCornerShape(16.dp),
            color    = BgElevated,
            modifier = Modifier
                .width(IDENTIFY_DIALOG_WIDTH)
                .border(1.dp, Border, RoundedCornerShape(16.dp)),
        ) {
            Column(
                modifier            = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text       = stringResource(R.string.identify_title),
                    style      = MaterialTheme.typography.titleLarge,
                    color      = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text  = stringResource(R.string.identify_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IdentifySearchRow(state = state, onSearch = onSearch)
                IdentifyStatus(state = state)
                LazyColumn(
                    modifier            = Modifier.heightIn(max = IDENTIFY_LIST_MAX_HEIGHT),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(state.candidates, key = { _, c -> c.externalId }) { index, candidate ->
                        CandidateRow(
                            candidate = candidate,
                            enabled   = !state.applying,
                            modifier  = if (index == 0) Modifier.focusRequester(firstResultFocus) else Modifier,
                            onClick   = { onPick(candidate.externalId) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroCtaButton(
                        label   = stringResource(R.string.detail_action_refresh_metadata),
                        icon    = Icons.Default.Refresh,
                        primary = false,
                        enabled = !state.applying,
                        onClick = onRefresh,
                    )
                    HeroCtaButton(
                        label   = stringResource(R.string.identify_close),
                        icon    = Icons.Default.Close,
                        primary = false,
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun IdentifySearchRow(state: IdentifyState, onSearch: (String, Int?) -> Unit) {
    var query    by remember(state.query) { mutableStateOf(state.query) }
    var yearText by remember(state.year) { mutableStateOf(state.year?.toString().orEmpty()) }
    val search = { onSearch(query, yearText.toIntOrNull()) }
    // Mientras se busca, los campos no son enfocables: el foco inicial del
    // Dialog caía en Título y el TV abría el teclado antes de que la lista
    // existiera para llevárselo. Al llegar resultados, el foco va al primero.
    val fieldsEnabled = !state.loading && !state.applying
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value           = query,
            onValueChange   = { query = it },
            label           = { Text(stringResource(R.string.identify_query_label)) },
            singleLine      = true,
            enabled         = fieldsEnabled,
            modifier        = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
        )
        OutlinedTextField(
            value           = yearText,
            onValueChange   = { yearText = it.filter(Char::isDigit).take(YEAR_DIGITS) },
            label           = { Text(stringResource(R.string.identify_year_label)) },
            singleLine      = true,
            enabled         = fieldsEnabled,
            modifier        = Modifier.width(120.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
        )
        HeroCtaButton(
            label   = stringResource(R.string.identify_search),
            icon    = Icons.Default.Search,
            primary = true,
            onClick = search,
        )
    }
}

@Composable
private fun IdentifyStatus(state: IdentifyState) {
    when {
        state.applying -> Text(
            text  = stringResource(R.string.identify_applying),
            style = MaterialTheme.typography.bodyMedium,
            color = Accent,
        )
        state.loading -> CircularProgressIndicator(
            color    = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        state.error != null -> Text(
            text  = state.error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        state.candidates.isEmpty() -> Text(
            text  = stringResource(R.string.identify_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CandidateRow(
    candidate: IdentifyCandidate,
    enabled:   Boolean,
    onClick:   () -> Unit,
    modifier:  Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .then(if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(10.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model              = candidate.posterUrl,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .width(44.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val year = candidate.year?.let { " ($it)" }.orEmpty()
            Text(
                text       = candidate.title + year,
                style      = MaterialTheme.typography.titleSmall,
                color      = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            if (candidate.overview.isNotBlank()) {
                Text(
                    text     = candidate.overview,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
        modifier              = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally,
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
