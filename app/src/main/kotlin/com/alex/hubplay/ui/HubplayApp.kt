package com.alex.hubplay.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.alex.hubplay.data.AppContainer
import com.alex.hubplay.data.LocalTrailerHost
import com.alex.hubplay.data.TrailerHost
import androidx.compose.foundation.background
import com.alex.hubplay.ui.components.CertTrustDialog
import com.alex.hubplay.ui.components.TrailerHostOverlay
import com.alex.hubplay.ui.theme.BgBase
import com.alex.hubplay.ui.home.components.LocalVisibleTabs
import com.alex.hubplay.ui.home.components.Tab
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
    val navController         = rememberNavController()
    val authState             by container.authStateFlow.collectAsState()
    val idleState             by container.idleController.state.collectAsState()
    val slides                by container.screensaverImageSource.slides.collectAsState()
    val pendingCertChallenge  by container.certChallengeBus.pending.collectAsState()
    val collectionsAvailable  by container.collectionsAvailability.hasAny.collectAsState()

    // Trigger the availability fetch on first paint of any TopNav-
    // bearing screen — cheap, idempotent thanks to ensureLoaded().
    androidx.compose.runtime.LaunchedEffect(authState.isAuthenticated) {
        if (authState.isAuthenticated) container.collectionsAvailability.ensureLoaded()
    }

    // Hide the Collections tab when the server has no sagas. Set
    // recomputes only when the boolean flips, so TopNav doesn't churn.
    val visibleTabs = androidx.compose.runtime.remember(collectionsAvailable) {
        if (collectionsAvailable) Tab.entries.toSet()
        else                       Tab.entries.toSet() - Tab.Collections
    }

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

    // TrailerHost singleton — un único WebView vive aquí mientras la app
    // está abierta. Las pantallas claman/liberan el trailer activo; al
    // navegar entre Home → Detail del mismo item, el vídeo no se interrumpe.
    val trailerScope = rememberCoroutineScope()
    val trailerHost  = remember { TrailerHost(trailerScope) }

    // Limpieza agresiva al entrar en pantallas que NO muestran trailer
    // (Movies grid, Series grid, Search, Settings, Player, etc). Si el
    // usuario venía de Home/Detail/Series con un trailer activo, lo cerramos
    // al instante — sin esto el debounce de 500ms del host dejaba el audio
    // sonando medio segundo y la WebView visible si la pantalla destino
    // tiene áreas transparentes. Sólo las 3 pantallas con trailer hero
    // (Home, Detail, Series) están exentas.
    val currentBackEntry by navController.currentBackStackEntryAsState()
    androidx.compose.runtime.LaunchedEffect(currentBackEntry?.destination?.route) {
        val route = currentBackEntry?.destination?.route ?: return@LaunchedEffect
        val isTrailerScreen = route == Route.Home.path ||
            route.startsWith("detail/") ||
            route.startsWith("series/")
        if (!isTrailerScreen) trailerHost.hideNow()
    }

    Box(modifier = Modifier.fillMaxSize().background(BgBase)) {
        // Capa 0: el WebView del trailer, detrás de todo el contenido. Las
        // pantallas tienen fondos transparentes (o backdrops con alpha
        // condicionada al estado revealed del host) para que se vea.
        CompositionLocalProvider(LocalTrailerHost provides trailerHost) {
            TrailerHostOverlay(modifier = Modifier.fillMaxSize())

            CompositionLocalProvider(LocalVisibleTabs provides visibleTabs) {
                HubplayNavGraph(
                    navController = navController,
                    startRoute    = startRoute,
                    container     = container,
                )
            }
        }

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
