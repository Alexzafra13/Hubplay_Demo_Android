package com.alex.hubplay.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.alex.hubplay.data.AppContainer
import com.alex.hubplay.ui.detail.DetailScreen
import com.alex.hubplay.ui.detail.DetailViewModel
import com.alex.hubplay.ui.home.HomeScreen
import com.alex.hubplay.ui.home.HomeViewModel
import com.alex.hubplay.ui.login.LoginScreen
import com.alex.hubplay.ui.login.LoginViewModel
import com.alex.hubplay.ui.player.PlayerScreen
import com.alex.hubplay.ui.player.PlayerViewModel

@Composable
fun HubplayNavGraph(
    navController: NavHostController,
    startRoute:    Route,
    container:     AppContainer,
) {
    val authState by container.authStateFlow.collectAsState()

    NavHost(
        navController     = navController,
        startDestination  = startRoute.path,
    ) {
        // ── Login ────────────────────────────────────────────────────
        composable(Route.Login.path) {
            val viewModel = viewModel<LoginViewModel>(
                factory = LoginViewModel.factory(container.deviceCodeRepository),
            )
            LoginScreen(
                viewModel = viewModel,
                onAuthenticated = {
                    navController.navigate(Route.Home.path) {
                        popUpTo(Route.Login.path) { inclusive = true }
                    }
                },
            )
        }

        // ── Home ─────────────────────────────────────────────────────
        composable(Route.Home.path) {
            val viewModel = viewModel<HomeViewModel>(
                factory = HomeViewModel.factory(container.homeRepository),
            )
            HomeScreen(
                viewModel  = viewModel,
                onOpenItem = { itemId ->
                    navController.navigate(Route.Detail.route(itemId))
                },
                onPlayItem = { itemId, resumePosSec ->
                    navController.navigate(Route.Player.route(itemId, resumePosSec))
                },
                onLogOut   = {
                    container.tokenStore.clearBlocking()
                    navController.navigate(Route.Login.path) {
                        popUpTo(Route.Home.path) { inclusive = true }
                    }
                },
            )
        }

        // ── Detail ───────────────────────────────────────────────────
        composable(
            route     = Route.Detail.path,
            arguments = listOf(
                navArgument(Route.Detail.ARG_ITEM_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            val itemId = entry.arguments?.getString(Route.Detail.ARG_ITEM_ID) ?: return@composable
            val viewModel = viewModel<DetailViewModel>(
                factory = DetailViewModel.factory(container.homeRepository, itemId),
            )
            DetailScreen(
                viewModel = viewModel,
                onPlay    = { id, resume ->
                    navController.navigate(Route.Player.route(id, resume))
                },
                onBack    = { navController.popBackStack() },
            )
        }

        // ── Player ───────────────────────────────────────────────────
        composable(
            route     = Route.Player.path,
            arguments = listOf(
                navArgument(Route.Player.ARG_ITEM_ID) { type = NavType.StringType },
                navArgument(Route.Player.ARG_RESUME)  {
                    type = NavType.LongType
                    defaultValue = 0L
                },
            ),
        ) { entry ->
            val itemId       = entry.arguments?.getString(Route.Player.ARG_ITEM_ID) ?: return@composable
            val resumePosSec = entry.arguments?.getLong(Route.Player.ARG_RESUME) ?: 0L
            val viewModel    = viewModel<PlayerViewModel>(
                factory = PlayerViewModel.factory(container.hubplayApi, itemId, resumePosSec),
            )
            PlayerScreen(
                viewModel = viewModel,
                authState = authState,
                onBack    = { navController.popBackStack() },
            )
        }
    }
}
