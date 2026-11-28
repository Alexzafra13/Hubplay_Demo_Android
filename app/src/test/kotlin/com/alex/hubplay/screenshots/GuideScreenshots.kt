package com.alex.hubplay.screenshots

import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import com.alex.hubplay.data.EpgProgram
import com.alex.hubplay.data.LiveChannel
import com.alex.hubplay.ui.components.TvShell
import com.alex.hubplay.ui.home.components.Tab
import com.alex.hubplay.ui.livetv.GuideData
import com.alex.hubplay.ui.livetv.LiveGuideScreen
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Guía completa de TV en directo: rejilla alineada por horas. */
class GuideScreenshots : ScreenshotTest() {

    private val now = Instant.parse("2026-11-28T18:42:00Z")

    private val names = listOf(
        "La 1", "La 2", "Antena 3", "Cuatro", "Telecinco", "laSexta", "Teledeporte", "Canal 24H", "Clan", "Sin guía",
    )
    private val titles = listOf(
        "Telediario", "El tiempo", "Cine: Arena Roja", "Documental: Océanos", "Fútbol: Liga", "Informe semanal",
        "Concurso", "Noticias", "Dibujos", "Magacín", "Serie: Distrito Siete", "Cine de noche",
    )

    private fun channel(i: Int) = LiveChannel(
        id = "c$i", name = names[i], number = i + 1, groupName = "TDT", category = "general",
        logoUrl = null, logoInitials = null, logoBg = null, logoFg = null,
        libraryId = "lib", isActive = true, healthStatus = "ok",
    )

    /** Programas de duraciones variadas desde 2 h antes de ahora; el último canal no tiene EPG. */
    private fun schedule(i: Int): List<EpgProgram> {
        if (i == names.lastIndex) return emptyList()
        val lengths = listOf(30L, 45L, 60L, 90L, 25L, 120L)
        var t = now.minus(Duration.ofMinutes(120L + i * 7))
        return (0 until 12).map { n ->
            val len = lengths[(i + n) % lengths.size]
            val p = EpgProgram(
                id = "c$i-p$n", title = titles[(i + n) % titles.size],
                description = "Programa de ejemplo para la guía en rejilla.", category = "",
                startTime = t, endTime = t.plus(Duration.ofMinutes(len)),
            )
            t = p.endTime
            p
        }
    }

    @Test
    fun guide() {
        val data = GuideData(
            channels       = names.indices.map(::channel),
            schedule       = names.indices.associate { "c$it" to schedule(it) },
            now            = now,
            filterLabel    = "Todos",
            focusChannelId = "c0",
        )
        setTvContent {
            TvShell(selectedTab = Tab.LiveTv, onNavigateToTab = {}, onOpenSettings = {}) {
                LiveGuideScreen(data = data, onPlay = {})
            }
        }
        settle(2500)
        snap("guide_01_landing")
        press(KEYCODE_DPAD_RIGHT, times = 3, settleMs = 900)
        snap("guide_02_right")
        press(KEYCODE_DPAD_DOWN, times = 3, settleMs = 900)
        snap("guide_03_down")
        press(KEYCODE_DPAD_DOWN, times = 6, settleMs = 700)
        snap("guide_04_no_info")
    }
}
