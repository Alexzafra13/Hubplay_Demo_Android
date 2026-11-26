package com.alex.hubplay.screenshots

import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import com.alex.hubplay.ui.home.HomeScreen
import com.alex.hubplay.ui.home.HomeViewModel
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Test

class HomeScreenshots : ScreenshotTest() {

    private fun showHome() {
        val vm = HomeViewModel(SampleHomeRepository(), emptyFlow())
        setTvContent {
            HomeScreen(
                viewModel = vm,
                onOpenItem = { _, _ -> },
                onPlayItem = { _, _ -> },
                onNavigateToTab = {},
                onLogOut = {},
                profileName = "Alex",
            )
        }
        settle(2500)
    }

    @Test
    fun home() {
        showHome()
        snap("home_01_landing")
        press(KEYCODE_DPAD_DOWN) // primer foco: menú lateral
        snap("home_02_sidebar")
        press(KEYCODE_DPAD_RIGHT) // al hero
        snap("home_03_hero_play")
        press(KEYCODE_DPAD_DOWN, settleMs = 1200)
        snap("home_04_rail_continue")
        press(KEYCODE_DPAD_RIGHT, settleMs = 2000)
        snap("home_05a_rail_right1")
        press(KEYCODE_DPAD_RIGHT, settleMs = 2000)
        snap("home_05_rail_moved")
        press(KEYCODE_DPAD_DOWN, settleMs = 1200)
        snap("home_06_next_up")
        press(KEYCODE_DPAD_DOWN, settleMs = 1200)
        snap("home_07_trending")
        press(KEYCODE_DPAD_DOWN, settleMs = 1200)
        snap("home_08_live")
        press(KEYCODE_DPAD_DOWN, settleMs = 1200)
        snap("home_09_latest")
        press(KEYCODE_DPAD_DOWN, settleMs = 1200)
        snap("home_10_last")
    }
}
