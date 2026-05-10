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
        composable(Route.Login.path) {
            // Fresh ViewModel per nav; the device-code repo it depends on
            // is the singleton from AppContainer so an in-flight pairing
            // session survives recomposition.
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

        composable(Route.Home.path) {
            val viewModel = viewModel<HomeViewModel>(
                factory = HomeViewModel.factory(container.homeRepository),
            )
            HomeScreen(
                viewModel  = viewModel,
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
