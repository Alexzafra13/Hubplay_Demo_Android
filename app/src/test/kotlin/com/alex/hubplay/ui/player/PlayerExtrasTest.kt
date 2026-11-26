package com.alex.hubplay.ui.player

import com.alex.hubplay.data.api.dto.ChapterDto
import com.alex.hubplay.data.api.dto.SegmentDto
import com.alex.hubplay.data.api.dto.TrickplayManifestDto
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerExtrasTest {

    private val intro = SkipSegment(SkipKind.Intro, startMs = 60_000, endMs = 120_000)
    private val outro = SkipSegment(SkipKind.Outro, startMs = 2_500_000, endMs = 2_600_000)

    @Test
    fun `intro activa dentro del tramo y no fuera`() {
        val segs = listOf(intro)
        assertThat(PlayerExtras.activeSkip(segs, 59_999, hasNext = false)).isNull()
        assertThat(PlayerExtras.activeSkip(segs, 60_000, hasNext = false)).isEqualTo(intro)
        assertThat(PlayerExtras.activeSkip(segs, 119_000, hasNext = false)).isEqualTo(intro)
    }

    @Test
    fun `el boton se va medio segundo antes del final`() {
        assertThat(PlayerExtras.activeSkip(listOf(intro), 119_600, hasNext = false)).isNull()
    }

    @Test
    fun `creditos solo con episodio siguiente`() {
        assertThat(PlayerExtras.activeSkip(listOf(outro), 2_550_000, hasNext = false)).isNull()
        assertThat(PlayerExtras.activeSkip(listOf(outro), 2_550_000, hasNext = true)).isEqualTo(outro)
    }

    @Test
    fun `segmentos de poca confianza o desconocidos se descartan`() {
        val dtos = listOf(
            SegmentDto(kind = "intro", startSeconds = 10.0, endSeconds = 70.0, confidence = 0.95),
            SegmentDto(kind = "intro", startSeconds = 10.0, endSeconds = 70.0, confidence = 0.5),
            SegmentDto(kind = "preview", startSeconds = 0.0, endSeconds = 5.0, confidence = 1.0),
            SegmentDto(kind = "outro", startSeconds = 90.0, endSeconds = 80.0, confidence = 1.0),
        )
        assertThat(PlayerExtras.segments(dtos))
            .containsExactly(SkipSegment(SkipKind.Intro, 10_000, 70_000))
    }

    @Test
    fun `capitulos en ms ordenados y capitulo en curso`() {
        val chapters = PlayerExtras.chapters(
            listOf(ChapterDto(startTicks = 3_000_000_000, title = "B"), ChapterDto(startTicks = 0, title = " ")),
        )
        assertThat(chapters).containsExactly(PlayerChapter(0, null), PlayerChapter(300_000, "B")).inOrder()
        assertThat(PlayerExtras.currentChapterIndex(chapters, 299_999)).isEqualTo(0)
        assertThat(PlayerExtras.currentChapterIndex(chapters, 300_000)).isEqualTo(1)
    }

    @Test
    fun `miniatura en la hoja igual que la web`() {
        val info = TrickplayInfo.from(
            "x",
            TrickplayManifestDto(intervalSec = 10, thumbWidth = 320, thumbHeight = 180, columns = 10, rows = 3, total = 25),
        )!!
        assertThat(info.tileAt(0)).isEqualTo(SpriteTile(0, 0, 0, 320, 180))
        // 125 s → miniatura 12 → columna 2, fila 1
        assertThat(info.tileAt(125_000)).isEqualTo(SpriteTile(12, 640, 180, 320, 180))
        // Pasado el final: la última (24 → columna 4, fila 2)
        assertThat(info.tileAt(9_999_000)).isEqualTo(SpriteTile(24, 1280, 360, 320, 180))
    }

    @Test
    fun `manifiesto con sobre data o vacio`() {
        val wrapped = TrickplayManifestDto(
            data = TrickplayManifestDto(intervalSec = 5, thumbWidth = 320, thumbHeight = 180, columns = 2, total = 4),
        )
        assertThat(TrickplayInfo.from("x", wrapped)?.intervalMs).isEqualTo(5_000)
        assertThat(TrickplayInfo.from("x", TrickplayManifestDto())).isNull()
    }
}
