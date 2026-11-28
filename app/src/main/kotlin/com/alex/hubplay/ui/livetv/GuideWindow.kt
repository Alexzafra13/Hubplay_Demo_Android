package com.alex.hubplay.ui.livetv

import androidx.compose.runtime.Immutable
import com.alex.hubplay.data.EpgProgram
import com.alex.hubplay.data.LiveChannel
import java.time.Duration
import java.time.Instant

/**
 * Tramo de tiempo que pinta la guía en rejilla: desde la media hora
 * anterior a la actual (para ver qué acaba de empezar) hasta donde llega
 * la EPG que ya tiene TV en directo (ahora + 6 h). Todo se mide en
 * minutos desde [start]: la pantalla los pasa a dp con una sola escala,
 * así que todas las filas quedan alineadas.
 */
internal data class GuideWindow(val start: Instant, val end: Instant) {

    val minutes: Float get() = minutesBetween(start, end)

    /** Minuto de [t] dentro de la ventana, recortado a sus bordes. */
    fun minuteOf(t: Instant): Float = minutesBetween(start, t).coerceIn(0f, minutes)

    /** Marcas de media hora para la regla (incluye [start]). */
    fun halfHourMarks(): List<Instant> =
        generateSequence(start) { it.plus(HALF_HOUR) }.takeWhile { it.isBefore(end) }.toList()

    companion object {
        private val HALF_HOUR: Duration = Duration.ofMinutes(30)
        private const val HOURS_AHEAD = 6L
        private const val SECONDS_PER_HALF_HOUR = 1800L

        fun around(now: Instant): GuideWindow {
            val slot = now.epochSecond - Math.floorMod(now.epochSecond, SECONDS_PER_HALF_HOUR)
            val start = Instant.ofEpochSecond(slot).minus(HALF_HOUR)
            return GuideWindow(start, start.plus(HALF_HOUR).plus(Duration.ofHours(HOURS_AHEAD)))
        }

        private fun minutesBetween(a: Instant, b: Instant): Float =
            Duration.between(a, b).seconds / SECONDS_PER_MINUTE

        private const val SECONDS_PER_MINUTE = 60f
    }
}

/**
 * Una celda de la fila de un canal: un programa recortado a la ventana, o
 * (sin [program]) el hueco "sin guía" que ocupa la fila entera para que el
 * canal siga siendo alcanzable con el mando.
 */
internal data class GuideCell(
    val program:     EpgProgram?,
    val startMinute: Float,
    val widthMinute: Float,
)

/** Celdas de un canal en [window], en orden; nunca vacía. */
internal fun guideCells(programs: List<EpgProgram>, window: GuideWindow): List<GuideCell> {
    val cells = programs
        .asSequence()
        .filter { it.endTime.isAfter(window.start) && it.startTime.isBefore(window.end) }
        .sortedBy { it.startTime }
        .map { p ->
            val from = window.minuteOf(p.startTime)
            GuideCell(program = p, startMinute = from, widthMinute = window.minuteOf(p.endTime) - from)
        }
        .filter { it.widthMinute >= MIN_CELL_MINUTES }
        .toList()
    return cells.ifEmpty { listOf(GuideCell(program = null, startMinute = 0f, widthMinute = window.minutes)) }
}

/** Un programa de menos de un minuto en la ventana no se puede ni enfocar. */
private const val MIN_CELL_MINUTES = 1f

/** Lo que pinta la guía: los canales del filtro de TV en directo y su EPG. */
@Immutable
class GuideData(
    val channels:       List<LiveChannel>,
    val schedule:       Map<String, List<EpgProgram>>,
    val now:            Instant,
    /** Filtro activo en TV en directo ("Todos", "Deportes"…), de subtítulo. */
    val filterLabel:    String,
    /** Canal en el que nace el foco (el último enfocado en TV en directo). */
    val focusChannelId: String? = null,
)
