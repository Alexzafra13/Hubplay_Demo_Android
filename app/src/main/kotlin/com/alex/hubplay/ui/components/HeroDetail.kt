@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)

package com.alex.hubplay.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.alex.hubplay.ui.metadata.IdentifyState
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.BgBase
import com.alex.hubplay.ui.theme.BgElevated
import com.alex.hubplay.ui.theme.Border
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════════════════════════════
//  Ficha cinematográfica compartida por Detalle (película) y Series.
//
//  Capa 0: backdrop + tráiler FIJOS ([HeroBackdrop]). Encima, una columna
//  con scroll: hero de un viewport ([HeroPanel]: póster + Reproducir +
//  iconos | logo, meta, sinopsis con "Ver más", chip de estudio) y los
//  rails ([RailsSection]: reparto, saga, "más como esto") sobre un velo.
//
//  Reglas de foco y scroll:
//   - El foco que entra en el hero va a Reproducir (`enter`), y mientras
//     algo del hero tenga el foco la página está arriba (`animateScrollTo(0)`).
//   - El spec de bring-into-view por defecto de Android TV pivota cada
//     foco al 30 %: aquí lo visible no desplaza ([HeroBringIntoViewSpec]).
//   - ↑ desde el primer rail vuelve a Reproducir (tecla interceptada).
//   - Rails diferidos dos frames: foco inicial determinista y primer frame
//     más barato con el tráiler pintando debajo.
//
//  Vista previa: con el tráiler sonando, arriba del todo y 5 s sin tocar
//  el mando, todo se desvanece y la carátula viaja a la esquina inferior
//  izquierda (una capa con escala + desplazamiento). La primera tecla solo
//  despierta la ficha. Ver [rememberPreviewMode].
// ═══════════════════════════════════════════════════════════════════════════

/** Botón de acción del hero (Reproducir, Episodios…). */
@androidx.compose.runtime.Immutable
class HeroCta(
    val label:   String,
    val icon:    ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

/** Reproducir + acción secundaria opcional bajo el póster. */
@androidx.compose.runtime.Immutable
class HeroCtas(
    val play:      HeroCta,
    val secondary: HeroCta? = null,
)

/** Etiqueta de sección junto a la marca ("SERIES") y la fila de metadatos. */
@androidx.compose.runtime.Immutable
class HeroHeader(
    val sectionLabel: String? = null,
    val meta:         @Composable () -> Unit,
)

/** Qué iconos rápidos se ofrecen además de favorito. */
@androidx.compose.runtime.Immutable
class HeroToggles(
    val showWatched:     Boolean,
    val canEditMetadata: Boolean,
)

/** Acciones sobre el item. `onToggleWatched` a null oculta el icono de visto. */
@androidx.compose.runtime.Immutable
class HeroActions(
    val onBack:           () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onToggleWatched:  (() -> Unit)?,
    val onIdentify:       () -> Unit,
)

/** Navegación a otras pantallas desde la ficha (chip, reparto, rails). */
@androidx.compose.runtime.Immutable
class HeroNav(
    val onOpenCollection: (String) -> Unit,
    val onOpenStudio:     (String) -> Unit,
    val onOpenPerson:     (String) -> Unit,
    val onOpenItem:       (String, MediaKind) -> Unit,
)

/** Rails bajo el hero. Los vacíos / nulos no se pintan. */
@androidx.compose.runtime.Immutable
class HeroRails(
    val people:     List<Person>     = emptyList(),
    val collection: CollectionDetail? = null,
    val related:    List<Content>    = emptyList(),
) {
    val isEmpty: Boolean get() = people.isEmpty() && collection == null && related.isEmpty()
}

/** Todo lo que la ficha necesita saber del item y de quién la aloja. */
@androidx.compose.runtime.Immutable
class HeroDetailConfig(
    val item:    Content,
    val header:  HeroHeader,
    val ctas:    HeroCtas,
    val toggles: HeroToggles,
    val actions: HeroActions,
    val nav:     HeroNav,
)

/**
 * Ficha completa: backdrop/tráiler fijos + hero + rails + vista previa.
 * [dialogOpen] desactiva la vista previa mientras hay un diálogo encima.
 */
@Composable
fun HeroDetailScaffold(
    config:           HeroDetailConfig,
    rails:            HeroRails,
    trailerResumeSec: Long = 0L,
    dialogOpen:       Boolean = false,
) {
    val item = config.item
    val trailerHost = LocalTrailerHost.current
    val trailerRevealed = trailerHost.revealed.value &&
        trailerHost.current.value?.itemId == item.id
    val scrollState = rememberScrollState()
    val preview = rememberPreviewMode(
        eligible = trailerRevealed && scrollState.value == 0 && !dialogOpen,
    )
    val previewProgress by animateFloatAsState(
        targetValue   = if (preview.active) 1f else 0f,
        animationSpec = tween(durationMillis = PREVIEW_ANIM_MS),
        label         = "hero-preview",
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize().onPreviewKeyEvent(preview.onKey)) {
        val heroHeight = maxHeight
        val playFocus  = remember { FocusRequester() }
        LaunchedEffect(item.id) {
            runCatching { playFocus.requestFocus() }
        }

        // Al cerrar Identificar el foco vuelve al lápiz que lo abrió. Sin
        // esto la ficha se quedaba sin foco y la primera tecla se perdía.
        val identifyFocus = remember { FocusRequester() }
        var wasDialogOpen by remember { mutableStateOf(false) }
        LaunchedEffect(dialogOpen) {
            if (!dialogOpen && wasDialogOpen) runCatching { identifyFocus.requestFocus() }
            wasDialogOpen = dialogOpen
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

        HeroBackdrop(item = item, trailerResumeSec = trailerResumeSec)

        CompositionLocalProvider(LocalBringIntoViewSpec provides HeroBringIntoViewSpec) {
            Column(modifier = Modifier.verticalScroll(scrollState)) {
                Box(
                    modifier = Modifier
                        .height(heroHeight)
                        .focusProperties { enter = { playFocus } }
                        .onFocusChanged { heroHasFocus = it.hasFocus },
                ) {
                    HeroPanel(
                        config          = config,
                        focus           = HeroFocus(play = playFocus, identify = identifyFocus),
                        previewProgress = { previewProgress },
                    )
                }
                if (railsReady && !rails.isEmpty) {
                    RailsSection(
                        rails     = rails,
                        currentId = item.id,
                        nav       = config.nav,
                        playFocus = playFocus,
                        minHeight = heroHeight,
                    )
                }
            }
        }
    }
}

/**
 * Bring-into-view de la ficha: 0 si el hijo ya está entero en pantalla
 * (moverse por el hero no desplaza), y si está fuera lo trae al 30 % del
 * alto (mismo pivote que usa Android TV por defecto).
 */
private val HeroBringIntoViewSpec = object : BringIntoViewSpec {
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

// ─── Vista previa ───────────────────────────────────────────────────────────

/** Modo vista previa: si está activo y qué hacer con cada tecla. */
private class PreviewMode(
    val active: Boolean,
    val onKey:  (KeyEvent) -> Boolean,
)

/**
 * Con [eligible] (tráiler revelado, página arriba, sin diálogo) y
 * [PREVIEW_IDLE_MS] sin pulsar nada, `active` pasa a true. Cualquier tecla
 * reinicia el contador; si estábamos en vista previa, esa primera tecla se
 * consume (solo "despierta" la ficha) salvo Back, que sigue saliendo.
 */
@Composable
private fun rememberPreviewMode(eligible: Boolean): PreviewMode {
    var inputTick by remember { mutableIntStateOf(0) }
    var idle      by remember { mutableStateOf(false) }
    LaunchedEffect(eligible, inputTick) {
        idle = false
        if (!eligible) return@LaunchedEffect
        delay(PREVIEW_IDLE_MS)
        idle = true
    }
    val active      = idle && eligible
    val activeState = rememberUpdatedState(active)
    val onKey: (KeyEvent) -> Boolean = remember {
        fun(event: KeyEvent): Boolean {
            if (event.type != KeyEventType.KeyDown) return false
            val wasActive = activeState.value
            inputTick++
            return wasActive && event.key != Key.Back
        }
    }
    return PreviewMode(active = active, onKey = onKey)
}

/**
 * Geometría para la animación de la carátula hacia la esquina, en
 * coordenadas de raíz. Se escribe desde `onGloballyPositioned` y se lee
 * dentro de `graphicsLayer`: la animación no recompone nada.
 */
private class PreviewGeometry {
    var heroOrigin   by mutableStateOf(Offset.Zero)
    var heroSize     by mutableStateOf(IntSize.Zero)
    var posterOrigin by mutableStateOf(Offset.Zero)
    var posterSize   by mutableStateOf(IntSize.Zero)
}

/** Geometría + progreso de la vista previa, para mover la carátula. */
private class PreviewMotion(
    val geometry: PreviewGeometry,
    val progress: () -> Float,
)

/**
 * Carátula: en vista previa se encoge a [PREVIEW_POSTER_SCALE] y viaja a la
 * esquina inferior izquierda del hero. Una sola capa con escala y
 * desplazamiento (origen arriba-izquierda); el layout no cambia.
 */
private fun Modifier.previewPosterMotion(preview: PreviewMotion): Modifier = this
    .onGloballyPositioned {
        preview.geometry.posterOrigin = it.positionInRoot()
        preview.geometry.posterSize   = it.size
    }
    .graphicsLayer {
        val p = preview.progress()
        val geo = preview.geometry
        val scale = 1f - (1f - PREVIEW_POSTER_SCALE) * p
        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = scale
        scaleY = scale
        val margin  = PREVIEW_MARGIN.toPx()
        val targetX = geo.heroOrigin.x + margin
        val targetY = geo.heroOrigin.y + geo.heroSize.height - margin - geo.posterSize.height * PREVIEW_POSTER_SCALE
        translationX = (targetX - geo.posterOrigin.x) * p
        translationY = (targetY - geo.posterOrigin.y) * p
    }

// ─── Backdrop ───────────────────────────────────────────────────────────────

/**
 * Capa fija detrás del scroll: backdrop (con el crossfade al tráiler) y los
 * degradados de legibilidad. El tráiler no se monta aquí — vive en
 * TrailerHostOverlay (root); aquí solo se registra el claim y se baja el
 * alpha del backdrop cuando el host lo revela para ESTE item.
 */
@Composable
private fun HeroBackdrop(item: Content, trailerResumeSec: Long) {
    val trailerKey  = (item as? Content.Movie)?.trailerKey  ?: (item as? Content.Series)?.trailerKey
    val trailerSite = (item as? Content.Movie)?.trailerSite ?: (item as? Content.Series)?.trailerSite

    val trailerHost = LocalTrailerHost.current
    val trailerRevealed = trailerHost.revealed.value &&
        trailerHost.current.value?.itemId == item.id

    DisposableEffect(item.id, trailerKey, trailerSite, trailerResumeSec) {
        // trailerResumeSec solo se usa si la key es NUEVA; con la misma key
        // (continuidad desde Inicio) el WebView ni se entera.
        val token = if (trailerKey != null && trailerSite != null) {
            trailerHost.activate(
                itemId     = item.id,
                videoKey   = trailerKey,
                site       = trailerSite,
                startAtSec = trailerResumeSec,
            )
        } else {
            null
        }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f                 to BgBase.copy(alpha = LEFT_FADE_START_ALPHA),
                        LEFT_FADE_MID_STOP to BgBase.copy(alpha = LEFT_FADE_MID_ALPHA),
                        1f                 to Color.Transparent,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to BgBase.copy(alpha = BOTTOM_FADE_ALPHA),
                    ),
                ),
        )
    }
}

// ─── Hero ───────────────────────────────────────────────────────────────────

/**
 * Hero de un viewport de alto: fila de volver + marca (+ etiqueta de
 * sección) arriba a la izquierda y, centrado, el bloque de dos columnas
 * (póster + Reproducir + iconos | info).
 */
/** Requesters del hero: Reproducir (foco inicial) y el lápiz de Identificar. */
private class HeroFocus(
    val play:     FocusRequester,
    val identify: FocusRequester,
)

@Composable
private fun HeroPanel(
    config:          HeroDetailConfig,
    focus:           HeroFocus,
    previewProgress: () -> Float,
) {
    val geometry = remember { PreviewGeometry() }
    // Todo lo que no es la carátula se desvanece con la vista previa. El
    // alpha se lee en la fase de dibujo: los 600 ms no recomponen.
    val fadeOut = Modifier.graphicsLayer { alpha = 1f - previewProgress() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                geometry.heroOrigin = it.positionInRoot()
                geometry.heroSize   = it.size
            },
    ) {
        Row(
            modifier          = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 20.dp)
                .zIndex(HEADER_Z_INDEX)
                .then(fadeOut),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackPill(onBack = config.actions.onBack)
            Spacer(Modifier.width(16.dp))
            Image(
                painter            = painterResource(R.drawable.brand_wordmark),
                contentDescription = stringResource(R.string.brand_hubplay),
                modifier           = Modifier.height(28.dp),
            )
            config.header.sectionLabel?.let { label ->
                Spacer(Modifier.width(10.dp))
                Text(
                    text          = label,
                    style         = MaterialTheme.typography.labelMedium,
                    color         = MaterialTheme.colorScheme.onBackground.copy(alpha = SECTION_LABEL_ALPHA),
                    fontWeight    = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                )
            }
        }

        Row(
            modifier              = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(HERO_CONTENT_WIDTH_FRACTION)
                // top: reserva la fila de volver + marca; con póster, Play e
                // iconos la columna izquierda no cabe centrada sin pisarla.
                .padding(start = 48.dp, end = 24.dp, top = HERO_HEADER_HEIGHT),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            PosterColumn(
                config  = config,
                focus   = focus,
                preview = PreviewMotion(geometry, previewProgress),
            )
            Box(modifier = fadeOut) {
                InfoColumn(config = config)
            }
        }
    }
}

@Composable
private fun PosterColumn(
    config:  HeroDetailConfig,
    focus:   HeroFocus,
    preview: PreviewMotion,
) {
    val item = config.item
    // Con acción secundaria (Episodios) la columna no cabe en el hero con
    // el póster grande: se usa el compacto para que los iconos no se corten.
    val posterWidth = if (config.ctas.secondary != null) POSTER_WIDTH_COMPACT else POSTER_WIDTH
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (item.posterUrl != null) {
            Box(
                modifier = Modifier
                    .width(posterWidth)
                    .aspectRatio(POSTER_ASPECT_RATIO)
                    .previewPosterMotion(preview)
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
        // Reproducir (ancho del póster), la acción secundaria si la hay y
        // los iconos rápidos. Todo se desvanece en la vista previa.
        Column(
            modifier            = Modifier.graphicsLayer { alpha = 1f - preview.progress() },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val play = config.ctas.play
            HeroCtaButton(
                label          = play.label,
                icon           = play.icon,
                primary        = true,
                enabled        = play.enabled,
                focusRequester = focus.play,
                onClick        = play.onClick,
                modifier       = Modifier.width(posterWidth),
            )
            config.ctas.secondary?.let { cta ->
                HeroCtaButton(
                    label    = cta.label,
                    icon     = cta.icon,
                    primary  = false,
                    enabled  = cta.enabled,
                    onClick  = cta.onClick,
                    modifier = Modifier.width(posterWidth),
                )
            }
            QuickActions(config = config, width = posterWidth, identifyFocus = focus.identify)
        }
    }
}

@Composable
private fun InfoColumn(config: HeroDetailConfig) {
    val item = config.item
    Column(
        modifier            = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Center,
    ) {
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
        config.header.meta()

        item.overview?.takeIf { it.isNotBlank() }?.let { ov ->
            Spacer(Modifier.height(14.dp))
            ExpandableOverview(text = ov)
        }

        StudioChipRow(item = item, onOpenStudio = config.nav.onOpenStudio)
    }
}

/**
 * Sinopsis recortada a [OVERVIEW_MAX_LINES] con un "Ver más" enfocable
 * que la despliega ahí mismo. El enlace solo aparece si el texto se recorta.
 */
@Composable
private fun ExpandableOverview(text: String) {
    var expanded  by remember(text) { mutableStateOf(false) }
    var truncated by remember(text) { mutableStateOf(false) }
    var focused   by remember { mutableStateOf(false) }
    Text(
        text         = text,
        style        = MaterialTheme.typography.bodyMedium,
        color        = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines     = if (expanded) OVERVIEW_MAX_LINES_EXPANDED else OVERVIEW_MAX_LINES,
        overflow     = TextOverflow.Ellipsis,
        onTextLayout = { if (!expanded) truncated = it.hasVisualOverflow },
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
 * Iconos bajo Reproducir: favorito, visto (si procede) y — con permiso de
 * metadatos en películas/series — el lápiz que abre Identificar. Sin
 * etiquetas fijas: una línea pequeña dice qué hace el que tiene el foco.
 */
@Composable
private fun QuickActions(config: HeroDetailConfig, width: Dp, identifyFocus: FocusRequester) {
    val item = config.item
    val isFavorite = (item as? Content.Movie)?.isFavorite
        ?: (item as? Content.Series)?.isFavorite
        ?: (item as? Content.Episode)?.isFavorite
        ?: false
    val watched = (item as? Content.Movie)?.watched
        ?: (item as? Content.Series)?.watched
        ?: (item as? Content.Episode)?.watched
    val onToggleWatched = config.actions.onToggleWatched
        ?.takeIf { config.toggles.showWatched && watched != null }
    val canIdentify = item is Content.Movie || item is Content.Series

    // Se guarda QUÉ icono tiene el foco, no su texto: así la línea cambia
    // sola al alternar favorito/visto.
    val focus = remember { QuickActionFocus() }
    val favoriteLabel = if (isFavorite) R.string.detail_action_favorite_remove else R.string.detail_action_favorite_add
    val watchedLabel  = if (watched == true) R.string.detail_action_mark_unwatched else R.string.detail_action_mark_watched
    val focusedLabel = focus.label(favoriteLabel = favoriteLabel, watchedLabel = watchedLabel)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionIcon(
                icon    = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label   = favoriteLabel,
                onClick = config.actions.onToggleFavorite,
                onFocus = focus.handler(QuickAction.Favorite),
            )
            if (onToggleWatched != null) {
                QuickActionIcon(
                    icon    = if (watched == true) Icons.Default.Check else Icons.Outlined.VisibilityOff,
                    label   = watchedLabel,
                    onClick = onToggleWatched,
                    onFocus = focus.handler(QuickAction.Watched),
                )
            }
            if (config.toggles.canEditMetadata && canIdentify) {
                QuickActionIcon(
                    icon     = Icons.Outlined.Edit,
                    label    = R.string.detail_action_metadata,
                    onClick  = config.actions.onIdentify,
                    onFocus  = focus.handler(QuickAction.Metadata),
                    modifier = Modifier.focusRequester(identifyFocus),
                )
            }
        }
        Text(
            text     = focusedLabel?.let { stringResource(it) }.orEmpty(),
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(width).height(QUICK_ACTION_CAPTION_HEIGHT),
        )
    }
}

private enum class QuickAction { Favorite, Watched, Metadata }

/** Qué icono rápido tiene el foco y qué texto le corresponde ahora mismo. */
private class QuickActionFocus {
    var focused by mutableStateOf<QuickAction?>(null)

    fun handler(action: QuickAction): (Boolean) -> Unit = { hasFocus ->
        focused = if (hasFocus) action else focused.takeUnless { it == action }
    }

    fun label(favoriteLabel: Int, watchedLabel: Int): Int? = when (focused) {
        QuickAction.Favorite -> favoriteLabel
        QuickAction.Watched  -> watchedLabel
        QuickAction.Metadata -> R.string.detail_action_metadata
        null                 -> null
    }
}

@Composable
private fun QuickActionIcon(
    icon:     ImageVector,
    label:    Int,
    onClick:  () -> Unit,
    onFocus:  (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    HeroIconButton(
        icon               = icon,
        contentDescription = stringResource(label),
        onClick            = onClick,
        modifier           = modifier.onFocusChanged { onFocus(it.isFocused) },
    )
}

/**
 * Línea de metadatos del hero ("★ 8,4 · 2019 · 99 min · Terror") en UN solo
 * `Text`: cada dato como composable propio eran hasta nueve nodos de texto
 * que medir y dibujar en el primer frame de la ficha en el TV box.
 * [HeroMeta.accent] pinta el dato en acento y seminegrita (la nota).
 */
class HeroMeta(val text: String, val accent: Boolean = false)

@Composable
fun HeroMetaRow(parts: List<HeroMeta>, modifier: Modifier = Modifier) {
    if (parts.isEmpty()) return
    val accent    = SpanStyle(color = Accent, fontWeight = FontWeight.SemiBold)
    val separator = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val line = buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (index > 0) withStyle(separator) { append(META_SEPARATOR) }
            if (part.accent) withStyle(accent) { append(part.text) } else append(part.text)
        }
    }
    Text(
        text     = line,
        style    = MaterialTheme.typography.bodyMedium,
        color    = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** "Estudio: X" bajo la sinopsis; se oculta sin datos. */
@Composable
private fun ColumnScope.StudioChipRow(item: Content, onOpenStudio: (String) -> Unit) {
    val studioName = (item as? Content.Movie)?.studioName ?: (item as? Content.Series)?.studioName
    val studioSlug = (item as? Content.Movie)?.studioSlug ?: (item as? Content.Series)?.studioSlug
    if (!studioSlug.isNullOrBlank() && !studioName.isNullOrBlank()) {
        Spacer(Modifier.height(10.dp))
        StudioChip(name = studioName, onClick = { onOpenStudio(studioSlug) })
    }
}

@Composable
private fun StudioChip(name: String, onClick: () -> Unit) {
    Surface(
        color          = MaterialTheme.colorScheme.surface,
        shape          = RoundedCornerShape(999.dp),
        tonalElevation = 2.dp,
        modifier       = Modifier.clickable(onClick = onClick),
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

// ─── Rails ──────────────────────────────────────────────────────────────────

/**
 * Reparto, saga y relacionados sobre un velo. Arriba, una franja con
 * degradado transparente → velo: al bajar, el backdrop (o el tráiler) que
 * sigue fijo detrás se funde en vez de cortarse. El tráiler NO se para al
 * bajar: se ve atenuado detrás de los rails.
 */
@Composable
private fun RailsSection(
    rails:     HeroRails,
    currentId: String,
    nav:       HeroNav,
    playFocus: FocusRequester,
    minHeight: Dp,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(RAILS_FADE_HEIGHT)
                .background(Brush.verticalGradient(0f to Color.Transparent, 1f to BgBase.copy(alpha = RAILS_SCRIM_ALPHA))),
        )
        // Mínimo un viewport de rails: con un solo rail la página no podía
        // desplazarse hasta el pivote y el rail quedaba al 65 % de alto con
        // media ficha (botones, iconos, chip) asomando por arriba.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight - RAILS_FADE_HEIGHT)
                .background(BgBase.copy(alpha = RAILS_SCRIM_ALPHA)),
        ) {
            // Solo el PRIMER rail rutea ↑ a Reproducir. Se intercepta la tecla
            // en vez de `focusProperties { up }`: el LazyRow es un focus
            // group y la propiedad heredada no llegaba a las cards.
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
            if (rails.people.isNotEmpty()) {
                CastCrewRail(people = rails.people, onOpenPerson = nav.onOpenPerson, modifier = railModifier())
            }
            // Saga: sus otras películas + tarjeta final que abre la colección.
            rails.collection?.let { collection ->
                PosterRail(
                    title      = stringResource(R.string.detail_section_collection, collectionDisplayName(collection.name)),
                    items      = collection.items.filter { it.id != currentId },
                    onOpenItem = nav.onOpenItem,
                    modifier   = railModifier(),
                    trailing   = { OpenCollectionCard(onClick = { nav.onOpenCollection(collection.id) }) },
                )
            }
            if (rails.related.isNotEmpty()) {
                PosterRail(
                    title      = stringResource(R.string.detail_section_related),
                    items      = rails.related,
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

/**
 * Rail de pósters con título. Reutiliza la card del catálogo, así que abrir
 * una lleva al Detalle real. [trailing] cierra el rail con una tarjeta extra.
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
            .padding(top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            modifier   = Modifier.padding(start = RAIL_START_PADDING),
            text       = title,
            style      = MaterialTheme.typography.titleMedium,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        // El margen va DENTRO del LazyRow (contentPadding): la card enfocada
        // crece un 8 % y con el padding fuera se recortaba la primera.
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding        = PaddingValues(start = RAIL_START_PADDING, end = RAIL_END_PADDING),
        ) {
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
            .aspectRatio(POSTER_ASPECT_RATIO)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (focused) OPEN_CARD_ALPHA_FOCUSED else OPEN_CARD_ALPHA))
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

/** "Reparto y equipo": avatares circulares; cada uno abre la ficha de la persona. */
@Composable
private fun CastCrewRail(
    people:       List<Person>,
    onOpenPerson: (String) -> Unit,
    modifier:     Modifier = Modifier,
) {
    Column(
        modifier            = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            modifier   = Modifier.padding(start = RAIL_START_PADDING),
            text       = stringResource(R.string.detail_section_cast),
            style      = MaterialTheme.typography.titleMedium,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding        = PaddingValues(start = RAIL_START_PADDING, end = RAIL_END_PADDING),
        ) {
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
            text       = person.name,
            style      = MaterialTheme.typography.labelMedium,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            textAlign  = TextAlign.Center,
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

// ─── Identificar ────────────────────────────────────────────────────────────

/**
 * "Identificar": buscar el item en TMDb y aplicar el match correcto
 * (sobrescribe título, sinopsis, reparto e imágenes), con "Actualizar
 * metadatos" como acción secundaria.
 *
 * Es un overlay dentro del árbol de la ficha, NO un `Dialog`: abrir una
 * ventana nueva con su propia composición costaba 0,5-0,9 s de hilo
 * principal en el TV box (primer frame de 500 ms), y al cerrarla la ficha
 * se quedaba sin foco. Aquí solo se compone el panel. El foco queda
 * atrapado dentro (`exit = Cancel`) y Back lo cierra.
 *
 * Adaptado a D-pad: el foco arranca en Buscar y salta al primer resultado
 * cuando llega la lista, nunca al campo de texto (el teclado del TV se
 * abriría solo).
 */
@Composable
fun IdentifyOverlay(
    state:     IdentifyState,
    onSearch:  (String, Int?) -> Unit,
    onPick:    (String) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val firstResultFocus = remember { FocusRequester() }
    val searchFocus      = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(onBack = onDismiss)
    LaunchedEffect(Unit) {
        runCatching { searchFocus.requestFocus() }
    }
    LaunchedEffect(state.candidates) {
        if (state.candidates.isEmpty()) return@LaunchedEffect
        runCatching { firstResultFocus.requestFocus() }
        // Cerrar el IME con reintento, como hace Login: a veces ignora la
        // primera petición mientras aún se está mostrando.
        keyboard?.hide()
        delay(KEYBOARD_HIDE_RETRY_MS)
        keyboard?.hide()
        delay(KEYBOARD_HIDE_RETRY_MS * 2)
        keyboard?.hide()
    }
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .zIndex(IDENTIFY_Z_INDEX)
            .background(BgBase.copy(alpha = IDENTIFY_SCRIM_ALPHA))
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup(),
        contentAlignment = Alignment.Center,
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
                IdentifySearchRow(state = state, onSearch = onSearch, searchFocus = searchFocus)
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
                IdentifyFooter(state = state, onRefresh = onRefresh, onDismiss = onDismiss)
            }
        }
    }
}

@Composable
private fun IdentifyFooter(state: IdentifyState, onRefresh: () -> Unit, onDismiss: () -> Unit) {
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

@Composable
private fun IdentifySearchRow(
    state:       IdentifyState,
    onSearch:    (String, Int?) -> Unit,
    searchFocus: FocusRequester,
) {
    var query    by remember(state.query) { mutableStateOf(state.query) }
    var yearText by remember(state.year) { mutableStateOf(state.year?.toString().orEmpty()) }
    val search = { onSearch(query, yearText.toIntOrNull()) }
    // Mientras se busca, los campos no son enfocables: si el foco inicial
    // cayera en Título el TV abriría el teclado antes de que la lista
    // existiera para llevárselo. Al llegar resultados, el foco va al primero.
    val fieldsEnabled = !state.loading && !state.applying
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IdentifyField(
            value         = query,
            onValueChange = { query = it },
            label         = stringResource(R.string.identify_query_label),
            enabled       = fieldsEnabled,
            keyboardType  = KeyboardType.Text,
            onSearch      = search,
            modifier      = Modifier.weight(1f),
        )
        IdentifyField(
            value         = yearText,
            onValueChange = { yearText = it.filter(Char::isDigit).take(YEAR_DIGITS) },
            label         = stringResource(R.string.identify_year_label),
            enabled       = fieldsEnabled,
            keyboardType  = KeyboardType.Number,
            onSearch      = search,
            modifier      = Modifier.width(120.dp),
        )
        HeroCtaButton(
            label          = stringResource(R.string.identify_search),
            icon           = Icons.Default.Search,
            primary        = true,
            focusRequester = searchFocus,
            onClick        = search,
        )
    }
}

/**
 * Campo del panel Identificar. Mientras no se edita es un Box enfocable con
 * borde y el mismo aspecto que un campo outlined: componer el
 * `OutlinedTextField` de Material costaba ~300 ms por campo en frío en la
 * Mi TV y era el 90 % del coste de abrir el panel (perfil 2026-09-15). Al
 * pulsarlo se compone el campo real, recibe el foco y abre el teclado; al
 * buscar o perder el foco vuelve al modo ligero.
 */
@Composable
private fun IdentifyField(
    value:         String,
    onValueChange: (String) -> Unit,
    label:         String,
    enabled:       Boolean,
    keyboardType:  KeyboardType,
    onSearch:      () -> Unit,
    modifier:      Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        val fieldFocus = remember { FocusRequester() }
        var hadFocus by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
        OutlinedTextField(
            value           = value,
            onValueChange   = onValueChange,
            label           = { Text(label) },
            singleLine      = true,
            modifier        = modifier
                .focusRequester(fieldFocus)
                // El primer callback llega sin foco (nodo recién montado):
                // solo se sale del modo edición si el foco llegó y se fue.
                .onFocusChanged { if (it.isFocused) hadFocus = true else if (hadFocus) editing = false },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    editing = false
                    onSearch()
                },
            ),
        )
    } else {
        var focused by remember { mutableStateOf(false) }
        val shape = RoundedCornerShape(4.dp)
        Column(
            modifier            = modifier
                .height(IDENTIFY_FIELD_HEIGHT)
                .clip(shape)
                .border(if (focused) 2.dp else 1.dp, if (focused) Accent else Border, shape)
                .onFocusChanged { focused = it.isFocused }
                .clickable(enabled = enabled) { editing = true }
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text  = label,
                style = MaterialTheme.typography.bodySmall,
                color = if (focused) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text     = value.ifEmpty { " " },
                style    = MaterialTheme.typography.bodyLarge,
                color    = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
                .aspectRatio(POSTER_ASPECT_RATIO)
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

// ─── Constantes ─────────────────────────────────────────────────────────────

/** Focus "pop" de un avatar del reparto — como MediaCard en TV. */
private const val CAST_FOCUS_SCALE = 1.08f

/** Degradado izquierdo de legibilidad y fundido inferior del backdrop. */
private const val LEFT_FADE_START_ALPHA = 0.92f
private const val LEFT_FADE_MID_STOP = 0.55f
private const val LEFT_FADE_MID_ALPHA = 0.40f
private const val BOTTOM_FADE_ALPHA = 0.55f

/** Fila de volver + marca por encima del contenido del hero. */
private const val HEADER_Z_INDEX = 10f

/** El bloque póster | info ocupa este ancho del hero. */
private const val HERO_CONTENT_WIDTH_FRACTION = 0.8f

/** Relación de aspecto de póster (2:3). */
private const val POSTER_ASPECT_RATIO = 2f / 3f

/** Etiqueta "SERIES" junto a la marca. */
private const val SECTION_LABEL_ALPHA = 0.85f

/** Fondo de la tarjeta "Ver colección" con y sin foco. */
private const val OPEN_CARD_ALPHA_FOCUSED = 0.9f
private const val OPEN_CARD_ALPHA = 0.5f

/** Franja de fundido backdrop → velo al bajar a los rails. */
private val RAILS_FADE_HEIGHT = 140.dp

/** Líneas de sinopsis en el hero; "Ver más" la despliega hasta [OVERVIEW_MAX_LINES_EXPANDED]. */
private const val OVERVIEW_MAX_LINES = 4
private const val OVERVIEW_MAX_LINES_EXPANDED = 12

/** Alto reservado arriba del hero para la píldora de volver y la marca. */
private val HERO_HEADER_HEIGHT = 72.dp

/** Ancho de la tarjeta "Ver colección" (igual que un póster del catálogo). */
private val OPEN_COLLECTION_CARD_WIDTH = 120.dp

/** Ancho del póster y de la columna de Reproducir + iconos; compacto con acción secundaria. */
private val POSTER_WIDTH = 190.dp
private val POSTER_WIDTH_COMPACT = 170.dp

/** Línea reservada bajo los iconos para el nombre de la acción enfocada. */
private val QUICK_ACTION_CAPTION_HEIGHT = 18.dp

/** Alto máximo de la lista de candidatos del panel Identificar. */
private val IDENTIFY_LIST_MAX_HEIGHT = 330.dp

/** Ancho del panel Identificar. */
private val IDENTIFY_DIALOG_WIDTH = 760.dp

/** Alto del campo ligero de Identificar (igual que un OutlinedTextField). */
private val IDENTIFY_FIELD_HEIGHT = 56.dp

/** El panel Identificar va por encima del hero y los rails, con un velo detrás. */
private const val IDENTIFY_Z_INDEX = 50f
private const val IDENTIFY_SCRIM_ALPHA = 0.72f

/** Separador entre datos de [HeroMetaRow]. */
private const val META_SEPARATOR = "  ·  "

private const val YEAR_DIGITS = 4

/** Reintento del cierre del teclado del TV al abrir Identificar. */
private const val KEYBOARD_HIDE_RETRY_MS = 150L

/** Al traer un rail a la vista, su borde superior queda a este alto del viewport. */
private const val RAIL_PIVOT_FRACTION = 0.3f

/** Márgenes de los rails, dentro del LazyRow (ver PosterRail). */
private val RAIL_START_PADDING = 48.dp
private val RAIL_END_PADDING = 24.dp

/** Opacidad del velo de los rails sobre el tráiler / backdrop fijo. */
private const val RAILS_SCRIM_ALPHA = 0.72f

/** Vista previa: espera sin tocar el mando, duración de la animación, tamaño y margen de la carátula. */
private const val PREVIEW_IDLE_MS = 5_000L
private const val PREVIEW_ANIM_MS = 600
private const val PREVIEW_POSTER_SCALE = 0.55f
private val PREVIEW_MARGIN = 28.dp
