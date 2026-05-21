package com.alex.hubplay.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.navigation.compose.rememberNavController
import com.alex.hubplay.data.AppContainer
import com.alex.hubplay.ui.components.CertTrustDialog
import com.alex.hubplay.ui.nav.HubplayNavGraph
import com.alex.hubplay.ui.nav.Route
import com.alex.hubplay.ui.screensaver.ScreensaverOverlay

/**
 * Root composable. Owns the NavController and decides the start
 * destination based on whether we already have a valid session.
 *
 * The session check is reactive — if the AuthInterceptor wipes tokens
 * mid-session (refresh chain revoked) the app pops back to Login
 * automatically without an explicit "log out" call.
 *
 * Also hosts the global [ScreensaverOverlay]. It sits on top of the
 * NavHost via z-index + AnimatedVisibility so its fade-in/out doesn't
 * unmount the underlying screens (the user returns exactly where they
 * left). Suppressed unless authenticated — there's nothing meaningful
 * to show before pairing.
 */
@Composable
fun HubplayApp(container: AppContainer) {
    val navController     = rememberNavController()
    val authState         by container.authStateFlow.collectAsState()
    val idleState         by container.idleController.state.collectAsState()
    val slides            by container.screensaverImageSource.slides.collectAsState()
    val pendingCertChallenge by container.certChallengeBus.pending.collectAsState()

    // Three-state startup gate:
    //   - no token        → Login
    //   - token, no pick  → WhoIsWatching (auto-skips to Home if ≤ 1 profile)
    //   - token + picked  → Home directly
    // We only evaluate this on first composition; subsequent transitions
    // (login, switch-profile) are handled by explicit navigate() calls
    // in HubplayNavGraph so the back stack stays clean.
    val startRoute = when {
        !authState.isAuthenticated          -> Route.Login
        authState.activeProfileId.isNullOrBlank() -> Route.WhoIsWatching
        else                                -> Route.Home
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HubplayNavGraph(
            navController = navController,
            startRoute    = startRoute,
            container     = container,
        )

        // The screensaver only ever shows when:
        //   1. The user is logged in (no pool to show otherwise).
        //   2. Idle timer has fired (idleState.isIdle).
        //   3. The player isn't suspending the controller (idleState.suspended).
        // AnimatedVisibility keeps the overlay mounted just long enough
        // to fade out cleanly, so the next swipe / button press feels
        // responsive instead of cutting hard.
        AnimatedVisibility(
            visible  = idleState.isIdle && !idleState.suspended && authState.isAuthenticated,
            enter    = fadeIn(animationSpec = tween(800)),
            exit     = fadeOut(animationSpec = tween(400)),
            modifier = Modifier.fillMaxSize().zIndex(100f),
        ) {
            ScreensaverOverlay(slides = slides)
        }

        // Cert-trust dialog promoted from LoginScreen to the app root.
        // Why: certs rotate every ~90 days with Let's Encrypt; if the
        // user is already paired and the rotation happens, the API
        // call from Home / Player / Search would have hard-failed with
        // a generic network error and forced a re-pair through Login.
        // Now the dialog catches the rotation in place — accept once,
        // ExoPlayer + Retrofit + Coil all share the same OkHttp with
        // the same TrustManager, so everything resumes.
        pendingCertChallenge?.let { challenge ->
            CertTrustDialog(
                challenge = challenge,
                onTrust   = { container.certChallengeBus.accept(it) },
                onCancel  = { container.certChallengeBus.dismiss() },
            )
        }
    }
}
