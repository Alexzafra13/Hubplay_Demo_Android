package com.alex.hubplay

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.alex.hubplay.data.AppContainer
import com.alex.hubplay.ui.HubplayApp
import com.alex.hubplay.ui.theme.HubPlayTheme

/**
 * Single-Activity host. All navigation lives inside Compose
 * (see [com.alex.hubplay.ui.nav.HubplayNavGraph]) — there are no
 * fragments, no extra Activities. This keeps lifecycle reasoning simple
 * and matches the patterns in Now in Android / official Compose samples.
 *
 * Two activity-level concerns live here instead of inside Compose:
 *
 *   1. **Idle screensaver**: every key / touch event is funneled
 *      through `idleController.onInteraction()`. Doing this at the
 *      dispatch layer (not Compose's onPreviewKeyEvent) means we catch
 *      input regardless of which composable currently owns focus —
 *      including events the focused composable consumes.
 *
 *      When the screensaver is showing we CONSUME the dismissing event
 *      so the first wake-up press doesn't double-fire as a navigation
 *      action on the underlying screen (otherwise pressing D-pad ↓ to
 *      dismiss would also scroll the home rails).
 */
class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the splash screen BEFORE super.onCreate so the
        // SplashScreen library can hook into Activity lifecycle to
        // bridge the launch theme to the post-splash theme (declared
        // in styles.xml as `postSplashScreenTheme`). Skipping this
        // would leave the launch theme stuck through Compose's first
        // frame, flashing the splash bg.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        container = (application as HubplayApp).container

        setContent {
            HubPlayTheme {
                HubplayApp(container = container)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Defensive: framework can dispatch a stray event during very
        // early lifecycle (config change race, intent re-dispatch) before
        // onCreate has run. `lateinit` would throw — just fall through.
        if (!::container.isInitialized) return super.dispatchKeyEvent(event)
        val wasIdle = container.idleController.state.value.isIdle
        container.idleController.onInteraction()
        // Consume the wake-up press so the underlying screen doesn't
        // also act on it. Only the DOWN edge — letting UP through avoids
        // stuck-modifier states on hardware keyboards.
        if (wasIdle && event.action == KeyEvent.ACTION_DOWN) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!::container.isInitialized) return super.dispatchTouchEvent(event)
        val wasIdle = container.idleController.state.value.isIdle
        container.idleController.onInteraction()
        if (wasIdle && event.action == MotionEvent.ACTION_DOWN) return true
        return super.dispatchTouchEvent(event)
    }
}
