package com.alex.hubplay.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.alex.hubplay.data.AppContainer
import com.alex.hubplay.ui.nav.HubplayNavGraph
import com.alex.hubplay.ui.nav.Route

/**
 * Root composable. Owns the NavController and decides the start
 * destination based on whether we already have a valid session.
 *
 * The session check is reactive — if the AuthInterceptor wipes tokens
 * mid-session (refresh chain revoked) the app pops back to Login
 * automatically without an explicit "log out" call.
 */
@Composable
fun HubplayApp(container: AppContainer) {
    val navController = rememberNavController()
    val authState by container.authStateFlow.collectAsState()

    val startRoute = if (authState.isAuthenticated) Route.Home else Route.Login

    HubplayNavGraph(
        navController = navController,
        startRoute    = startRoute,
        container     = container,
    )
}
