package com.alex.hubplay.ui.livetv

import com.alex.hubplay.data.EpgProgram
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class GuideWindowTest {

    private val now = Instant.parse("2026-11-28T18:42:10Z")

    private fun program(id: String, from: String, to: String) = EpgProgram(
        id          = id,
        title       = id,
        description = "",
        category    = "",
        startTime   = Instant.parse("2026-11-28T$from:00Z"),
        endTime     = Instant.parse("2026-11-28T$to:00Z"),
    )

    @Test
    fun `window starts half an hour before the current slot and spans six and a half hours`() {
        val w = GuideWindow.around(now)
        assertThat(w.start).isEqualTo(Instant.parse("2026-11-28T18:00:00Z"))
        assertThat(w.end).isEqualTo(Instant.parse("2026-11-29T00:30:00Z"))
        assertThat(w.halfHourMarks().first()).isEqualTo(w.start)
        assertThat(w.halfHourMarks()).hasSize(13)
    }

    @Test
    fun `programmes are clipped to the window and keep their order`() {
        val w = GuideWindow.around(now)
        val cells = guideCells(
            listOf(program("b", "19:00", "20:00"), program("a", "17:30", "19:00"), program("old", "16:00", "17:30")),
            w,
        )
        assertThat(cells.map { it.program?.id }).containsExactly("a", "b").inOrder()
        assertThat(cells[0].startMinute).isEqualTo(0f)
        assertThat(cells[0].widthMinute).isEqualTo(60f)
        assertThat(cells[1].startMinute).isEqualTo(60f)
        assertThat(cells[1].widthMinute).isEqualTo(60f)
    }

    @Test
    fun `a channel without guide data still gets one cell across the window`() {
        val w = GuideWindow.around(now)
        val cells = guideCells(emptyList(), w)
        assertThat(cells.single().program).isNull()
        assertThat(cells.single().widthMinute).isEqualTo(w.minutes)
    }
}
