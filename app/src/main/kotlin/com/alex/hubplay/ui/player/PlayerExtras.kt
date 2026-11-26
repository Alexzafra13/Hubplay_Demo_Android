package com.alex.hubplay.ui.player

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.media3.ui.AspectRatioFrameLayout
import com.alex.hubplay.R
import com.alex.hubplay.data.api.dto.ChapterDto
import com.alex.hubplay.data.api.dto.SegmentDto
import com.alex.hubplay.data.api.dto.TrickplayManifestDto

// Extras del reproductor de VOD que el servidor ya da en /items/{id} y
// la web ya usa: saltar intro/resumen/créditos y capítulos. Más las
// opciones locales (velocidad, encuadre). Lógica pura para testear sin UI.

/** Un capítulo del fichero: dónde empieza y su nombre (puede venir vacío). */
@Immutable
data class PlayerChapter(val startMs: Long, val title: String?)

/** Tramo que se puede saltar. */
@Immutable
data class SkipSegment(val kind: SkipKind, val startMs: Long, val endMs: Long)

enum class SkipKind(@StringRes val labelRes: Int) {
    Recap(R.string.player_skip_recap),
    Intro(R.string.player_skip_intro),
    Outro(R.string.player_skip_credits),
}

object PlayerExtras {

    /** Mismo suelo que la web (segmentLogic.ts): por debajo no se ofrece el botón. */
    private const val MIN_CONFIDENCE = 0.7

    /** El botón se va medio segundo antes del final para no parpadear. */
    private const val TAIL_TRIM_MS = 500L

    private const val MS_PER_SECOND = 1000.0
    private const val TICKS_PER_MS = 10_000L

    fun chapters(dtos: List<ChapterDto>): List<PlayerChapter> =
        dtos.map { PlayerChapter(startMs = it.startTicks / TICKS_PER_MS, title = it.title?.takeIf { t -> t.isNotBlank() }) }
            .sortedBy { it.startMs }

    fun segments(dtos: List<SegmentDto>): List<SkipSegment> = dtos.mapNotNull { dto ->
        val kind = when (dto.kind) {
            "recap" -> SkipKind.Recap
            "intro" -> SkipKind.Intro
            "outro" -> SkipKind.Outro
            else    -> return@mapNotNull null
        }
        if (dto.confidence < MIN_CONFIDENCE || dto.endSeconds <= dto.startSeconds) return@mapNotNull null
        SkipSegment(kind, (dto.startSeconds * MS_PER_SECOND).toLong(), (dto.endSeconds * MS_PER_SECOND).toLong())
    }

    /**
     * El tramo saltable que contiene [positionMs], o null. Los créditos solo
     * se ofrecen si hay episodio siguiente (saltarlos = ir al siguiente);
     * en una película no hay a dónde saltar. Si dos se solapan, gana el que
     * empieza más tarde (como la web).
     */
    fun activeSkip(segments: List<SkipSegment>, positionMs: Long, hasNext: Boolean): SkipSegment? =
        segments
            .filter { it.kind != SkipKind.Outro || hasNext }
            .filter { positionMs >= it.startMs && positionMs < it.endMs - TAIL_TRIM_MS }
            .maxByOrNull { it.startMs }

    /** Capítulo en curso (el último que ya empezó), para marcarlo en la lista. */
    fun currentChapterIndex(chapters: List<PlayerChapter>, positionMs: Long): Int =
        chapters.indexOfLast { it.startMs <= positionMs }

    /** Velocidades ofrecidas (Jellyfin ofrece las mismas). */
    val speeds: List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
}

/** Encuadre del vídeo en pantalla. */
enum class VideoFit(@StringRes val labelRes: Int, val resizeMode: Int) {
    Fit(R.string.player_fit_fit, AspectRatioFrameLayout.RESIZE_MODE_FIT),
    Zoom(R.string.player_fit_zoom, AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    Stretch(R.string.player_fit_stretch, AspectRatioFrameLayout.RESIZE_MODE_FILL),
}

/** Lo que el menú Opciones enseña además de lo que lee del propio ExoPlayer. */
@Immutable
data class PlayerOptionsState(
    val fit:      VideoFit,
    val chapters: List<PlayerChapter>,
    /** direct_play | direct_stream | transcode */
    val method:   String?,
)

/** Rectángulo (px) de una miniatura dentro de la hoja. */
@Immutable
data class SpriteTile(val index: Int, val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * Miniaturas de la barra (trickplay): una hoja PNG de `columns` de ancho con
 * una miniatura cada [intervalMs]. Misma cuenta que la web (useTrickplay.ts).
 */
@Immutable
data class TrickplayInfo(
    val itemId:      String,
    val intervalMs:  Long,
    val thumbWidth:  Int,
    val thumbHeight: Int,
    val columns:     Int,
    val total:       Int,
) {
    /** La miniatura que corresponde a [positionMs] (la última si se pasa del final). */
    fun tileAt(positionMs: Long): SpriteTile {
        val index = (positionMs.coerceAtLeast(0L) / intervalMs).toInt().coerceAtMost(total - 1)
        val col = index % columns
        val row = index / columns
        return SpriteTile(index, col * thumbWidth, row * thumbHeight, thumbWidth, thumbHeight)
    }

    val spritePath: String get() = "/api/v1/items/$itemId/trickplay.png"

    companion object {
        private const val MS_PER_SECOND = 1000L

        /** null si el manifiesto no sirve (campos a 0). */
        fun from(itemId: String, dto: TrickplayManifestDto): TrickplayInfo? {
            val m = dto.unwrap()
            val valid = m.intervalSec > 0 && m.thumbWidth > 0 && m.thumbHeight > 0 && m.columns > 0 && m.total > 0
            if (!valid) return null
            return TrickplayInfo(
                itemId      = itemId,
                intervalMs  = m.intervalSec * MS_PER_SECOND,
                thumbWidth  = m.thumbWidth,
                thumbHeight = m.thumbHeight,
                columns     = m.columns,
                total       = m.total,
            )
        }
    }
}
