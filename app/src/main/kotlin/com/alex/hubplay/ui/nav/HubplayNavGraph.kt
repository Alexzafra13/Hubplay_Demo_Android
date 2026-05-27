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
import com.alex.hubplay.data.MediaKind
import com.alex.hubplay.ui.catalog.CatalogScreen
import com.alex.hubplay.ui.catalog.CatalogViewModel
import com.alex.hubplay.ui.catalog.PortraitCatalogCard
import com.alex.hubplay.ui.collections.CollectionDetailScreen
import com.alex.hubplay.ui.collections.CollectionDetailViewModel
import com.alex.hubplay.ui.collections.CollectionsScreen
import com.alex.hubplay.ui.collections.CollectionsViewModel
import com.alex.hubplay.ui.detail.DetailScreen
import com.alex.hubplay.ui.detail.DetailViewModel
import com.alex.hubplay.ui.home.HomeScreen
import com.alex.hubplay.ui.home.HomeViewModel
import com.alex.hubplay.ui.home.components.Tab
import com.alex.hubplay.ui.livetv.ChannelOrderScreen
import com.alex.hubplay.ui.livetv.ChannelOrderViewModel
import com.alex.hubplay.ui.livetv.LiveTvScreen
import com.alex.hubplay.ui.livetv.LiveTvViewModel
import com.alex.hubplay.ui.login.LoginScreen
import com.alex.hubplay.ui.login.LoginViewModel
import com.alex.hubplay.ui.player.PlayerScreen
import com.alex.hubplay.ui.player.PlayerViewModel
import com.alex.hubplay.ui.search.SearchScreen
import com.alex.hubplay.ui.search.SearchViewModel
import com.alex.hubplay.ui.series.SeriesScreen
import com.alex.hubplay.ui.series.SeriesViewModel
import com.alex.hubplay.ui.settings.SettingsScreen
import com.alex.hubplay.ui.settings.SettingsViewModel
import com.alex.hubplay.ui.settings.TrustedServersScreen
import com.alex.hubplay.ui.settings.TrustedServersViewModel
import com.alex.hubplay.ui.whoiswatching.WhoIsWatchingScreen
import com.alex.hubplay.ui.whoiswatching.WhoIsWatchingViewModel

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
                factory = LoginViewModel.factory(
                    container.deviceCodeRepository,
                    container.lanDiscovery,
                    container.certChallengeBus,
                ),
            )
            LoginScreen(
                viewModel = viewModel,
                onAuthenticated = {
                    // Always land on the WhoIsWatching gateway. The
                    // screen itself auto-skips to Home when the tree
                    // has ≤ 1 profile, so solo accounts never see the
                    // picker.
                    navController.navigate(Route.WhoIsWatching.path) {
                        popUpTo(Route.Login.path) { inclusive = true }
                    }
                },
            )
        }

        // ── Who's watching? (multi-profile picker) ───────────────────
        composable(Route.WhoIsWatching.path) {
            val vm = viewModel<WhoIsWatchingViewModel>(
                factory = WhoIsWatchingViewModel.factory(container.profileRepository),
            )
            WhoIsWatchingScreen(
                viewModel       = vm,
                onNavigateHome  = {
                    navController.navigate(Route.Home.path) {
                        popUpTo(Route.WhoIsWatching.path) { inclusive = true }
                    }
                },
                onSignOut       = {
                    // Wipe bearer + refresh + active-profile flag and
                    // drop the user back on Login. Mirrors the logOut
                    // closure defined further down for the Home-side
                    // screens, but pops the picker off the back stack
                    // so back-press doesn't bounce them right back in.
                    container.tokenStore.clearBlocking()
                    navController.navigate(Route.Login.path) {
                        popUpTo(Route.WhoIsWatching.path) { inclusive = true }
                    }
                },
            )
        }

        // Helper closures shared between Home / Movies / Series / LiveTv
        // so each surface uses the same navigation rules.
        val openItem: (String, MediaKind) -> Unit = { itemId, kind ->
            val route = if (kind == MediaKind.Series)
                Route.Series.route(itemId)
            else
                Route.Detail.route(itemId)
            navController.navigate(route)
        }
        val playItem: (String, Long) -> Unit = { itemId, resumePosSec ->
            navController.navigate(Route.Player.route(itemId, resumePosSec))
        }
        val logOut: () -> Unit = {
            container.tokenStore.clearBlocking()
            navController.navigate(Route.Login.path) {
                popUpTo(Route.Home.path) { inclusive = true }
            }
        }
        // Tab navigation between Home / Movies / Series / LiveTv. Uses
        // singleTop + restoreState so jumping back to a tab keeps its
        // scroll position and pulls don't pile up in the back stack.
        val navigateToTab: (Tab) -> Unit = { tab ->
            val target = when (tab) {
                Tab.Home        -> Route.Home.path
                Tab.Movies      -> Route.Movies.path
                Tab.Collections -> Route.Collections.path
                Tab.Series      -> Route.SeriesList.path
                Tab.LiveTv      -> Route.LiveTv.path
                Tab.Search      -> Route.Search.path
            }
            navController.navigate(target) {
                popUpTo(Route.Home.path) {
                    saveState = true
                    inclusive = false
                }
                launchSingleTop = true
                restoreState    = true
            }
        }
        val openCollection: (String) -> Unit = { collectionId ->
            navController.navigate(Route.CollectionDetail.route(collectionId))
        }
        val openSettings: () -> Unit = {
            navController.navigate(Route.Settings.path)
        }
        val openChannelOrder: () -> Unit = {
            navController.navigate(Route.ChannelOrder.path)
        }

        // ── Home ─────────────────────────────────────────────────────
        composable(Route.Home.path) {
            val viewModel = viewModel<HomeViewModel>(
                factory = HomeViewModel.factory(container.homeRepository, container.meEventsStream),
            )
            HomeScreen(
                viewModel       = viewModel,
                onOpenItem      = { itemId, kind ->
                    if (kind == MediaKind.Series) {
                        navController.navigate(Route.Series.route(itemId))
                    } else {
                        val resume = viewModel.trailerCurrentTimeSec.value
                        navController.navigate(Route.Detail.route(itemId, resume))
                    }
                },
                onPlayItem      = playItem,
                onNavigateToTab = navigateToTab,
                onLogOut        = logOut,
                onOpenSettings  = openSettings,
                profileName     = authState.activeProfileName,
                authState       = authState,
                okHttpClient    = container.mainOkHttp,
                idleController  = container.idleController,
            )
        }

        // ── Movies catalog ───────────────────────────────────────────
        composable(Route.Movies.path) {
            val vm = viewModel<CatalogViewModel>(
                factory = CatalogViewModel.factory(container.homeRepository, CatalogViewModel.Source.Movies),
            )
            val ui by vm.ui.collectAsState()
            CatalogScreen(
                selectedTab   = Tab.Movies,
                title         = "Películas",
                items         = ui.items,
                isLoading     = ui.isLoading,
                error         = ui.error,
                onRetry       = vm::load,
                onTabSelected = navigateToTab,
                onLogOut      = logOut,
                onSettings    = openSettings,
                cardContent   = { item -> PortraitCatalogCard(item, openItem) },
            )
        }

        // ── Series catalog ───────────────────────────────────────────
        composable(Route.SeriesList.path) {
            val vm = viewModel<CatalogViewModel>(
                factory = CatalogViewModel.factory(container.homeRepository, CatalogViewModel.Source.Series),
            )
            val ui by vm.ui.collectAsState()
            CatalogScreen(
                selectedTab   = Tab.Series,
                title         = "Series",
                items         = ui.items,
                isLoading     = ui.isLoading,
                error         = ui.error,
                onRetry       = vm::load,
                onTabSelected = navigateToTab,
                onLogOut      = logOut,
                onSettings    = openSettings,
                cardContent   = { item -> PortraitCatalogCard(item, openItem) },
            )
        }

        // ── Live TV (custom screen — not a Catalog flavour) ──────────
        composable(Route.LiveTv.path) {
            val vm = viewModel<LiveTvViewModel>(
                factory = LiveTvViewModel.factory(
                    container.liveTvRepository,
                    container.channelOrderStore,
                    container.meEventsStream,
                ),
            )
            LiveTvScreen(
                viewModel       = vm,
                authState       = authState,
                okHttpClient    = container.mainOkHttp,
                onPlayChannel   = { channelId -> playItem(channelId, 0L) },
                onTabSelected   = navigateToTab,
                onLogOut        = logOut,
                onSettings      = openSettings,
                onReorderChannels = openChannelOrder,
            )
        }

        // ── Channel order / hide ─────────────────────────────────────
        composable(Route.ChannelOrder.path) {
            val vm = viewModel<ChannelOrderViewModel>(
                factory = ChannelOrderViewModel.factory(
                    container.liveTvRepository,
                    container.channelOrderStore,
                    container.tokenStore,
                ),
            )
            ChannelOrderScreen(
                viewModel = vm,
                onBack    = { navController.popBackStack() },
            )
        }

        // ── Search ───────────────────────────────────────────────────
        composable(Route.Search.path) {
            val vm = viewModel<SearchViewModel>(
                factory = SearchViewModel.factory(container.homeRepository),
            )
            SearchScreen(
                viewModel     = vm,
                onTabSelected = navigateToTab,
                onOpenItem    = openItem,
                onLogOut      = logOut,
                onSettings    = openSettings,
            )
        }

        // ── Settings ─────────────────────────────────────────────────
        composable(Route.Settings.path) {
            val vm = viewModel<SettingsViewModel>(
                factory = SettingsViewModel.factory(container.tokenStore, container.crashLogger),
            )
            val forgetServer: () -> Unit = {
                // Wipes BOTH tokens + server URL, then drops back to login.
                container.tokenStore.forgetServerBlocking()
                navController.navigate(Route.Login.path) {
                    popUpTo(Route.Home.path) { inclusive = true }
                }
            }
            val changeProfile: () -> Unit = {
                // Drop only the picked-profile flag — bearer + server URL
                // stay so the picker can call /me/profiles + switch the
                // token in-place. Pop Home so back-press from the new
                // session doesn't land on the previous user's content.
                container.tokenStore.clearActiveProfileBlocking()
                navController.navigate(Route.WhoIsWatching.path) {
                    popUpTo(Route.Home.path) { inclusive = true }
                }
            }
            SettingsScreen(
                viewModel             = vm,
                onBack                = { navController.popBackStack() },
                onLogOut              = logOut,
                onForgetServer        = forgetServer,
                onChangeProfile       = changeProfile,
                onReorderChannels     = openChannelOrder,
                onOpenTrustedServers  = { navController.navigate(Route.TrustedServers.path) },
            )
        }

        // ── Trusted servers (TOFU pin list) ──────────────────────────
        composable(Route.TrustedServers.path) {
            val vm = viewModel<TrustedServersViewModel>(
                factory = TrustedServersViewModel.factory(container.certPinStore),
            )
            TrustedServersScreen(
                viewModel = vm,
                onBack    = { navController.popBackStack() },
            )
        }

        // ── Detail ───────────────────────────────────────────────────
        composable(
            route     = Route.Detail.path,
            arguments = listOf(
                navArgument(Route.Detail.ARG_ITEM_ID) { type = NavType.StringType },
                navArgument(Route.Detail.ARG_TRAILER_RESUME) {
                    type = NavType.LongType; defaultValue = 0L
                },
            ),
        ) { entry ->
            val itemId = entry.arguments?.getString(Route.Detail.ARG_ITEM_ID) ?: return@composable
            val trailerResume = entry.arguments?.getLong(Route.Detail.ARG_TRAILER_RESUME) ?: 0L
            val viewModel = viewModel<DetailViewModel>(
                factory = DetailViewModel.factory(container.homeRepository, itemId),
            )
            DetailScreen(
                viewModel          = viewModel,
                trailerResumeSec   = trailerResume,
                onPlay             = { id, resume ->
                    navController.navigate(Route.Player.route(id, resume))
                },
                onBack             = { navController.popBackStack() },
                onOpenCollection   = openCollection,
            )
        }

        // ── Collections grid ─────────────────────────────────────────
        composable(Route.Collections.path) {
            val vm = viewModel<CollectionsViewModel>(
                factory = CollectionsViewModel.factory(container.homeRepository),
            )
            CollectionsScreen(
                viewModel     = vm,
                onOpen        = openCollection,
                onTabSelected = navigateToTab,
                onLogOut      = logOut,
                onSettings    = openSettings,
            )
        }

        // ── Collection detail (saga hero + member movies) ────────────
        composable(
            route     = Route.CollectionDetail.path,
            arguments = listOf(
                navArgument(Route.CollectionDetail.ARG_COLLECTION_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            val rawId = entry.arguments?.getString(Route.CollectionDetail.ARG_COLLECTION_ID) ?: return@composable
            val collectionId = java.net.URLDecoder.decode(rawId, Charsets.UTF_8)
            val vm = viewModel<CollectionDetailViewModel>(
                factory = CollectionDetailViewModel.factory(container.homeRepository, collectionId),
            )
            CollectionDetailScreen(
                viewModel  = vm,
                onOpenItem = openItem,
                onBack     = { navController.popBackStack() },
            )
        }

        // ── Series ───────────────────────────────────────────────────
        composable(
            route     = Route.Series.path,
            arguments = listOf(
                navArgument(Route.Series.ARG_SERIES_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            val seriesId  = entry.arguments?.getString(Route.Series.ARG_SERIES_ID) ?: return@composable
            val viewModel = viewModel<SeriesViewModel>(
                factory = SeriesViewModel.factory(container.homeRepository, seriesId),
            )
            SeriesScreen(
                viewModel     = viewModel,
                onPlayEpisode = { id, resume ->
                    navController.navigate(Route.Player.route(id, resume))
                },
                onBack        = { navController.popBackStack() },
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
                factory = PlayerViewModel.factory(
                    api          = container.hubplayApi,
                    liveTvRepo   = container.liveTvRepository,
                    itemId       = itemId,
                    resumePosSec = resumePosSec,
                ),
            )
            PlayerScreen(
                viewModel      = viewModel,
                authState      = authState,
                okHttpClient   = container.mainOkHttp,
                idleController = container.idleController,
                onBack         = { navController.popBackStack() },
            )
        }
    }
}
