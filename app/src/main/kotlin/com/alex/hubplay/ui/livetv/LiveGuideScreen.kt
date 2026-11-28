package com.alex.hubplay.ui.livetv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.hubplay.R
import com.alex.hubplay.data.EpgProgram
import com.alex.hubplay.data.LiveChannel
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.BgBase
import com.alex.hubplay.ui.theme.BgCard
import com.alex.hubplay.ui.theme.BgOverlay
import com.alex.hubplay.ui.theme.TextMuted
import com.alex.hubplay.ui.theme.TextPrimary
import com.alex.hubplay.ui.theme.TextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * La guía con los datos del ViewModel de TV en directo: los canales del
 * filtro activo y la EPG que ya descargó (ahora − 1 h … ahora + 6 h, que
 * se refresca sola cada 5 min).
 */
@Composable
fun LiveGuideRoute(viewModel: LiveTvViewModel, onPlayChannel: (String) -> Unit) {
    val ui by viewModel.ui.collectAsState()
    if (ui.channels.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(BgBase), contentAlignment = Alignment.Center) {
            if (ui.isLoading) {
                CircularProgressIndicator(color = Accent)
            } else {
                Text(text = stringResource(R.string.livetv_empty_title), color = TextSecondary)
            }
        }
        return
    }
    val label = filterLabel(ui.filter)
    val data = remember(ui.visibleChannels, ui.scheduleByChannel, ui.nowEpoch, label) {
        GuideData(
            channels       = ui.visibleChannels,
            schedule       = ui.scheduleByChannel,
            now            = Instant.ofEpochMilli(ui.nowEpoch),
            filterLabel    = label,
            focusChannelId = ui.focusedChannelId,
        )
    }
    LiveGuideScreen(
        data   = data,
        onPlay = { ch ->
            viewModel.recordWatch(ch.id)
            onPlayChannel(ch.id)
        },
    )
}

@Composable
private fun filterLabel(filter: ChannelFilter): String = when (filter) {
    ChannelFilter.All       -> stringResource(R.string.livetv_filter_all)
    ChannelFilter.Favorites -> stringResource(R.string.livetv_filter_favorites)
    ChannelFilter.Recent    -> stringResource(R.string.livetv_filter_recent)
    is ChannelFilter.Group  -> filter.name
}

/**
 * Guía clásica en rejilla: canales en filas y programas a lo ancho,
 * alineados por hora con una regla arriba y una línea en "ahora". Es una
 * pantalla aparte (Guía completa, en el menú de TV en directo); la
 * portada de TV en directo sigue igual.
 *
 * Rendimiento: filas perezosas y un único [ScrollState] horizontal
 * compartido por la regla y todas las filas, así que moverse en el tiempo
 * desplaza la rejilla entera sin recomponer. El título de un programa que
 * empezó antes del borde visible se queda pegado al borde (en la fase de
 * layout, sin recomponer).
 */
@Composable
fun LiveGuideScreen(data: GuideData, onPlay: (LiveChannel) -> Unit) {
    val window = remember(data.now.epochSecond / WINDOW_REFRESH_SECONDS) { GuideWindow.around(data.now) }
    val hScroll = rememberScrollState()
    val density = LocalDensity.current
    var selected by remember { mutableStateOf<GuideSelection?>(null) }
    val startIndex = remember(data.channels) {
        data.channels.indexOfFirst { it.id == data.focusChannelId }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val initialFocus = remember { FocusRequester() }

    LaunchedEffect(window) {
        // Arranca en la media hora actual: la anterior queda a la izquierda.
        hScroll.scrollTo(with(density) { minutesToDp(HALF_HOUR_MINUTES).roundToPx() })
        withFrameNanos { }
        runCatching { initialFocus.requestFocus() }
    }

    Column(modifier = Modifier.fillMaxSize().background(BgBase)) {
        GuideHeader(data = data, selected = selected)
        TimeRuler(window = window, now = data.now, scroll = hScroll)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(data.channels, key = { it.id }) { channel ->
                val cells = remember(channel.id, data.schedule[channel.id], window) {
                    guideCells(data.schedule[channel.id].orEmpty(), window)
                }
                GuideRow(
                    channel = channel,
                    row     = GuideRowState(cells, window, data.now, hScroll),
                    focus   = initialFocus.takeIf { channel.id == data.channels.getOrNull(startIndex)?.id },
                    onFocus = { program -> selected = GuideSelection(channel, program) },
                    onPlay  = onPlay,
                )
            }
        }
    }
}

/** El programa enfocado, para la cabecera. */
@Immutable
private class GuideSelection(val channel: LiveChannel, val program: EpgProgram?)

/** Lo que una fila necesita además de su canal. */
@Immutable
private class GuideRowState(
    val cells:  List<GuideCell>,
    val window: GuideWindow,
    val now:    Instant,
    val scroll: ScrollState,
)

@Composable
private fun GuideHeader(data: GuideData, selected: GuideSelection?) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT)
            .padding(start = 24.dp, end = 32.dp, top = 18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.width(CHANNEL_COLUMN_WIDTH)) {
            Text(
                text       = stringResource(R.string.livetv_guide_title),
                color      = TextPrimary,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(text = data.filterLabel, color = TextSecondary, fontSize = 13.sp, maxLines = 1)
            Text(text = clock(data.now), color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(24.dp))
        selected?.let { SelectionDetail(it) }
    }
}

@Composable
private fun SelectionDetail(selection: GuideSelection) {
    val program = selection.program
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text       = program?.title ?: stringResource(R.string.livetv_guide_no_info),
            color      = TextPrimary,
            fontSize   = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
        )
        val time = program?.let { "${clock(it.startTime)} – ${clock(it.endTime)} · " }.orEmpty()
        Text(text = time + selection.channel.name, color = TextSecondary, fontSize = 13.sp, maxLines = 1)
        program?.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                text     = it,
                color    = TextMuted,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun TimeRuler(window: GuideWindow, now: Instant, scroll: ScrollState) {
    val marks = remember(window) { window.halfHourMarks() }
    Row(modifier = Modifier.fillMaxWidth().height(RULER_HEIGHT)) {
        Spacer(Modifier.width(CHANNEL_COLUMN_WIDTH + CHANNEL_GAP))
        Box(modifier = Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
            Box(modifier = Modifier.width(minutesToDp(window.minutes)).fillMaxHeight()) {
                NowMarker(window = window, now = now)
                marks.forEach { mark ->
                    Text(
                        text     = clock(mark),
                        color    = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .offset(x = minutesToDp(window.minuteOf(mark)))
                            .padding(start = 6.dp, top = 6.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GuideRow(
    channel: LiveChannel,
    row:     GuideRowState,
    focus:   FocusRequester?,
    onFocus: (EpgProgram?) -> Unit,
    onPlay:  (LiveChannel) -> Unit,
) {
    val liveIndex = remember(row.cells, row.now) {
        row.cells.indexOfFirst { it.program?.isLiveAt(row.now) == true }.coerceAtLeast(0)
    }
    Row(modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT).padding(vertical = CELL_GAP / 2)) {
        GuideChannelCell(channel)
        Spacer(Modifier.width(CHANNEL_GAP))
        // Solo las filas usan el bring-into-view "quieto": la lista vertical
        // sigue con el de siempre.
        CompositionLocalProvider(LocalBringIntoViewSpec provides GuideBringIntoViewSpec) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().horizontalScroll(row.scroll)) {
                Box(modifier = Modifier.width(minutesToDp(row.window.minutes)).fillMaxHeight()) {
                    row.cells.forEachIndexed { i, cell ->
                        ProgramCell(
                            cell     = cell,
                            row      = row,
                            onFocus  = { onFocus(cell.program) },
                            onClick  = { onPlay(channel) },
                            modifier = if (i == liveIndex && focus != null) Modifier.focusRequester(focus) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideChannelCell(channel: LiveChannel) {
    Row(
        modifier          = Modifier
            .padding(start = 24.dp)
            .width(CHANNEL_COLUMN_WIDTH - 24.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(BgCard)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (channel.number > 0) {
            Text(
                text       = channel.number.toString().padStart(2, '0'),
                color      = TextMuted,
                fontSize   = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.padding(end = 8.dp),
            )
        }
        Text(
            text       = channel.name,
            color      = TextPrimary,
            fontSize   = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProgramCell(
    cell:     GuideCell,
    row:      GuideRowState,
    onFocus:  () -> Unit,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val program = cell.program
    val isLive = program?.isLiveAt(row.now) == true
    val isPast = program != null && !program.endTime.isAfter(row.now)
    val shape = RoundedCornerShape(6.dp)
    val startDp = minutesToDp(cell.startMinute)
    val startPx = with(LocalDensity.current) { startDp.roundToPx() }
    Box(
        modifier = modifier
            .offset(x = startDp)
            .width(minutesToDp(cell.widthMinute) - CELL_GAP)
            .fillMaxHeight()
            .clip(shape)
            .background(if (focused) BgOverlay else BgCard)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> Accent
                    isLive  -> Accent.copy(alpha = LIVE_BORDER_ALPHA)
                    else    -> BgCard
                },
                shape = shape,
            )
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) onFocus()
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        Column(modifier = Modifier.stickToVisibleStart(row.scroll, startPx).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text       = program?.title ?: stringResource(R.string.livetv_guide_no_info),
                color      = if (isPast) TextMuted else TextPrimary,
                fontSize   = 13.sp,
                fontWeight = if (focused || isLive) FontWeight.SemiBold else FontWeight.Medium,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            program?.let {
                Text(
                    text     = "${clock(it.startTime)} – ${clock(it.endTime)}",
                    color    = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
        if (isLive && program != null) LiveProgress(program.progressAt(row.now))
    }
}

@Composable
private fun LiveProgress(fraction: Float) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(3.dp)
                .background(Accent),
        )
    }
}

/**
 * Marca de "ahora" en la regla. En las filas no hay línea (tachaba los
 * títulos): la barra de progreso de cada celda en directo acaba en "ahora".
 */
@Composable
private fun NowMarker(window: GuideWindow, now: Instant) {
    if (now.isBefore(window.start) || !now.isBefore(window.end)) return
    Box(
        modifier = Modifier
            .offset(x = minutesToDp(window.minuteOf(now)) - NOW_LINE_WIDTH / 2)
            .padding(top = RULER_HEIGHT / 2)
            .size(width = NOW_LINE_WIDTH, height = RULER_HEIGHT / 2)
            .background(Accent),
    )
}

/**
 * El texto de una celda que empezó antes del borde visible se corre hasta
 * ese borde (y estrecha lo que le queda), como en las guías de las teles.
 * Lee el scroll en la fase de layout: desplazar no recompone.
 */
private fun Modifier.stickToVisibleStart(scroll: ScrollState, cellStartPx: Int): Modifier = layout { measurable, constraints ->
    val room = (constraints.maxWidth - STICKY_MIN_WIDTH.roundToPx()).coerceAtLeast(0)
    val shift = (scroll.value - cellStartPx).coerceIn(0, room)
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth - shift))
    layout(constraints.maxWidth, placeable.height) { placeable.place(shift, 0) }
}

/**
 * Moverse con el mando no desplaza la rejilla si la celda ya se ve en
 * buena parte (un programa largo que empezó hace rato no obliga a saltar
 * atrás); si no se ve, se trae lo justo, empezando por su principio.
 */
@OptIn(ExperimentalFoundationApi::class)
private object GuideBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val visible = minOf(offset + size, containerSize) - maxOf(offset, 0f)
        return when {
            visible >= minOf(size, containerSize) * VISIBLE_ENOUGH -> 0f
            offset < 0f                                         -> offset
            else                                                -> minOf(offset, offset + size - containerSize)
        }
    }
}

private val clockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun clock(t: Instant): String = clockFormat.format(t.atZone(ZoneId.systemDefault()))

private fun minutesToDp(minutes: Float): Dp = DP_PER_MINUTE * minutes

/** Una hora ocupa 360 dp: en 1080p caben unas dos horas a la vez. */
private val DP_PER_MINUTE = 6.dp
private val CHANNEL_COLUMN_WIDTH = 220.dp
private val CHANNEL_GAP = 8.dp
private val CELL_GAP = 4.dp
private val ROW_HEIGHT = 64.dp
private val RULER_HEIGHT = 30.dp
private val HEADER_HEIGHT = 118.dp
private val STICKY_MIN_WIDTH = 72.dp
private const val HALF_HOUR_MINUTES = 30f
private const val LIVE_BORDER_ALPHA = 0.45f
private const val VISIBLE_ENOUGH = 0.5f
private val NOW_LINE_WIDTH = 2.dp

/** La ventana se recalcula cada media hora (el tic de "ahora" va cada 30 s). */
private const val WINDOW_REFRESH_SECONDS = 1800L
