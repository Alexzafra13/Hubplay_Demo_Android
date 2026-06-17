package com.alex.hubplay

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launch smoke test — runs on an Android TV emulator via the
 * `UI tests (emulator)` workflow (`.github/workflows/ui-tests.yml`).
 *
 * What it proves on a *fresh install* (no token, no paired server), with
 * NO backend reachable:
 *   - `HubplayApp.onCreate` + `AppContainer` wiring don't throw.
 *   - The theme, splash hand-off and `NavGraph` compose.
 *   - The Login screen reaches its first stage (server-URL entry) and
 *     renders without crashing.
 *
 * That single assertion exercises a surprising amount of the startup path,
 * so it's a cheap regression gate for "the app doesn't even boot".
 *
 * A richer Login → Home → Play flow is deliberately NOT here: it needs a
 * reachable HubPlay server (or DI test doubles, which the manual
 * AppContainer doesn't expose yet). Drive that against a real server with
 * `scripts/tv-smoke.sh` until we add an injectable test backend.
 *
 * NOTE: written without a local emulator to verify — confirm it passes on
 * the first `:app:connectedDebugAndroidTest` run and adjust the asserted
 * node if the start destination differs. Adding `Modifier.testTag(...)`
 * to key composables would make deeper assertions far less brittle.
 */
@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunches_andLoginScreenIsShown() {
        composeRule.waitForIdle()
        // Resource-based lookup so the test survives copy tweaks.
        val loginTitle = composeRule.activity.getString(R.string.login_title)
        composeRule.onNodeWithText(loginTitle).assertIsDisplayed()
    }
}
