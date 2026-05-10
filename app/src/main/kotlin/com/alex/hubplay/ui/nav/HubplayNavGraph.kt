package com.alex.hubplay.ui.nav

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.alex.hubplay.data.AppContainer
import com.alex.hubplay.ui.home.HomeScreen
import com.alex.hubplay.ui.login.LoginScreen
import com.alex.hubplay.ui.login.LoginViewModel

@Composable
fun HubplayNavGraph(
    navController: NavHostController,
    startRoute:    Route,
    container:     AppContainer,
) {
    NavHost(
        navController     = navController,
        startDestination  = startRoute.path,
    ) {
        composable(Route.Login.path) {
            // ViewModel is created fresh per navigation; the repository
            // it depends on is the singleton from AppContainer so any
            // in-flight device-code session survives recomposition.
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
            HomeScreen(
                onLogOut = {
                    container.tokenStore.clearBlocking()
                    navController.navigate(Route.Login.path) {
                        popUpTo(Route.Home.path) { inclusive = true }
                    }
                },
            )
        }
    }
}
