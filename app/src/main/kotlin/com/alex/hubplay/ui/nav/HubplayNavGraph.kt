package com.alex.hubplay.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.alex.hubplay.data.AppContainer
import com.alex.hubplay.data.Content
import com.alex.hubplay.data.MediaKind
import com.alex.hubplay.ui.catalog.CatalogScreen
import com.alex.hubplay.ui.catalog.CatalogViewModel
import com.alex.hubplay.ui.catalog.PortraitCatalogCard
import com.alex.hubplay.ui.collections.CollectionDetailScreen
import com.alex.hubplay.ui.collections.CollectionDetailViewModel
import com.alex.hubplay.ui.collections.CollectionsScreen
import com.alex.hubplay.ui.collections.CollectionsViewModel
import com.alex.hubplay.ui.components.TvShell
import com.alex.hubplay.ui.detail.DetailScreen
import com.alex.hubplay.ui.detail.DetailViewModel
import com.alex.hubplay.ui.home.HomeActions
import com.alex.hubplay.ui.home.HomeScreen
import com.alex.hubplay.ui.home.HomeViewModel
import com.alex.hubplay.ui.home.components.Tab
import com.alex.hubplay.ui.livetv.ChannelOrderScreen
import com.alex.hubplay.ui.livetv.ChannelOrderViewModel
import com.alex.hubplay.ui.livetv.LiveTvScreen
import com.alex.hubplay.ui.livetv.LiveTvViewModel
import com.alex.hubplay.ui.login.LoginScreen
import com.alex.hubplay.ui.login.LoginViewModel
import com.alex.hubplay.ui.person.PersonDetailScreen
import com.alex.hubplay.ui.person.PersonDetailViewModel
import com.alex.hubplay.ui.player.PlayerRequest
import com.alex.hubplay.ui.player.PlayerScreen
import com.alex.hubplay.ui.player.PlayerViewModel
import com.alex.hubplay.ui.search.SearchScreen
import com.alex.hubplay.ui.search.SearchViewModel
import com.alex.hubplay.ui.series.SeriesScreen
import com.alex.hubplay.ui.series.SeriesViewModel
import com.alex.hubplay.ui.settings.DevicesScreen
import com.alex.hubplay.ui.settings.DevicesViewModel
import com.alex.hubplay.ui.settings.HomeLayoutScreen
import com.alex.hubplay.ui.settings.HomeLayoutViewModel
import com.alex.hubplay.ui.settings.SettingsActions
import com.alex.hubplay.ui.settings.SettingsScreen
import com.alex.hubplay.ui.settings.SettingsTarget
import com.alex.hubplay.ui.settings.SettingsViewModel
import com.alex.hubplay.ui.settings.TrustedServersScreen
import com.alex.hubplay.ui.settings.TrustedServersViewModel
import com.alex.hubplay.ui.shared.SharedDetailScreen
import com.alex.hubplay.ui.shared.SharedDetailViewModel
import com.alex.hubplay.ui.shared.SharedScreen
import com.alex.hubplay.ui.shared.SharedTarget
import com.alex.hubplay.ui.shared.SharedViewModel
import com.alex.hubplay.ui.studio.StudioDetailScreen
import com.alex.hubplay.ui.studio.StudioDetailViewModel
import com.alex.hubplay.ui.whoiswatching.WhoIsWatchingScreen
import com.alex.hubplay.ui.whoiswatching.WhoIsWatchingViewModel

@Composable
fun HubplayNavGraph(
    navController: NavHostController,
    startRoute:    Route,
    container:     AppContainer,
) {
    val authState by container.authStateFlow.collectAsState()

    // Sin transiciones entre pantallas. El crossfade por defecto de
    // NavHost (700 ms de fadeIn/fadeOut) obliga a HWUI a componer las DOS
    // pantallas en capas a pantalla completa con alpha durante ~40 frames,
    // sobre el WebView del tráiler que sigue pintando vídeo debajo: en la
    // GPU del TV box eso era un 35 % de frames con tirón al abrir Detalle
    // desde Inicio. Un corte seco es lo que hacen las apps de TV grandes y,
    // con el tráiler continuando, se lee como "la ficha se abre encima".
    NavHost(
        navController      = navController,
        startDestination   = startRoute.path,
        enterTransition    = { EnterTransition.None },
        exitTransition     = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition  = { ExitTransition.None },
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
                    container.revokeServerSession()
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
            navController.navigate(itemRoute(itemId, kind))
        }
        val playItem: (String, Long) -> Unit = { itemId, resumePosSec ->
            navController.navigate(Route.Player.route(itemId, resumePosSec))
        }
        // Canal de TV: el reproductor se salta `/items` + `/info` (404).
        val playChannel: (String) -> Unit = { channelId ->
            navController.navigate(Route.Player.route(channelId, live = true))
        }
        // Federación: ficha y reproducción de un título de un amigo.
        val openShared: (Content.Shared) -> Unit = { item ->
            navController.navigate(Route.SharedDetail.route(item.peerId, item.id, item.libraryId))
        }
        val playShared: (Content.Shared, Long) -> Unit = { item, resumeSec ->
            if (item.isPlayable) {
                navController.navigate(Route.Player.route(item.id, resumeSec, peerId = item.peerId))
            } else {
                openShared(item)
            }
        }
        val logOut: () -> Unit = {
            container.revokeServerSession()
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
                Tab.Shared      -> Route.Shared.path
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
        val openPerson: (String) -> Unit = { personId ->
            navController.navigate(Route.Person.route(personId))
        }
        val openStudio: (String) -> Unit = { slug ->
            navController.navigate(Route.Studio.route(slug))
        }
        val openSettings: () -> Unit = {
            navController.navigate(Route.Settings.path)
        }
        val openChannelOrder: () -> Unit = {
            navController.navigate(Route.ChannelOrder.path)
        }
        // Menú lateral en TODAS las pantallas tras el login (no solo en las
        // de primer nivel): fichas, colecciones, personas, estudios y
        // Ajustes. Volver sigue siendo la tecla Atrás.
        val shell: @Composable (Tab?, Boolean, Boolean, @Composable () -> Unit) -> Unit =
            { tab, padContent, settings, content ->
                TvShell(
                    selectedTab      = tab,
                    onNavigateToTab  = navigateToTab,
                    onOpenSettings   = openSettings,
                    padContent       = padContent,
                    settingsSelected = settings,
                ) { content() }
            }

        // ── Home ─────────────────────────────────────────────────────
        composable(Route.Home.path) {
            val viewModel = viewModel<HomeViewModel>(
                factory = HomeViewModel.factory(
                    container.homeRepository,
                    container.meEventsStream,
                    container.peersRepository,
                ),
            )
            val actions = remember(viewModel) {
                HomeActions(
                    onOpenItem      = { itemId, kind ->
                        // El tráiler que sonaba en Inicio sigue en la ficha.
                        navController.navigate(itemRoute(itemId, kind, viewModel.trailerCurrentTimeSec.value))
                    },
                    onPlayItem      = playItem,
                    onPlayChannel   = playChannel,
                    onOpenShared    = openShared,
                    onPlayShared    = playShared,
                    onNavigateToTab = navigateToTab,
                    onOpenSettings  = openSettings,
                )
            }
            HomeScreen(
                viewModel       = viewModel,
                actions         = actions,
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
                isLoadingMore = ui.isLoadingMore,
                canLoadMore   = ui.canLoadMore,
                error         = ui.error,
                onRetry       = vm::load,
                onLoadMore    = vm::loadMore,
                onTabSelected = navigateToTab,
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
                isLoadingMore = ui.isLoadingMore,
                canLoadMore   = ui.canLoadMore,
                error         = ui.error,
                onRetry       = vm::load,
                onLoadMore    = vm::loadMore,
                onTabSelected = navigateToTab,
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
                onPlayChannel   = playChannel,
                onTabSelected   = navigateToTab,
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
            shell(Tab.LiveTv, true, false) {
                ChannelOrderScreen(
                    viewModel = vm,
                    onBack    = { navController.popBackStack() },
                )
            }
        }

        // ── Search ───────────────────────────────────────────────────
        composable(Route.Search.path) {
            val vm = viewModel<SearchViewModel>(
                factory = SearchViewModel.factory(container.homeRepository, container.peersRepository),
            )
            SearchScreen(
                viewModel     = vm,
                onTabSelected = navigateToTab,
                onOpenItem    = openItem,
                onSettings    = openSettings,
                onOpenShared  = openShared,
            )
        }

        // ── Settings ─────────────────────────────────────────────────
        composable(Route.Settings.path) {
            val vm = viewModel<SettingsViewModel>(
                factory = SettingsViewModel.factory(container.tokenStore, container.crashLogger),
            )
            val forgetServer: () -> Unit = {
                // Wipes BOTH tokens + server URL, then drops back to login.
                container.revokeServerSession()
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
            shell(null, true, true) {
                SettingsScreen(
                    viewModel = vm,
                    actions   = SettingsActions(
                        onBack                = { navController.popBackStack() },
                        onLogOut              = logOut,
                        onForgetServer        = forgetServer,
                        onChangeProfile       = changeProfile,
                        onReorderChannels     = openChannelOrder,
                        onOpen                = { target -> navController.navigate(target.route.path) },
                    ),
                )
            }
        }

        // ── Ajustes → Inicio (rails: orden y visibilidad, por perfil) ─
        composable(Route.HomeLayout.path) {
            val vm = viewModel<HomeLayoutViewModel>(
                factory = HomeLayoutViewModel.factory(container.homeRepository),
            )
            shell(null, true, true) {
                HomeLayoutScreen(
                    viewModel = vm,
                    onBack    = { navController.popBackStack() },
                )
            }
        }

        // ── Dispositivos conectados (/me/sessions) ───────────────────
        composable(Route.Devices.path) {
            val vm = viewModel<DevicesViewModel>(
                factory = DevicesViewModel.factory(container.hubplayApi, container.tokenStore),
            )
            shell(null, true, true) {
                DevicesScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
        }

        // ── Trusted servers (TOFU pin list) ──────────────────────────
        composable(Route.TrustedServers.path) {
            val vm = viewModel<TrustedServersViewModel>(
                factory = TrustedServersViewModel.factory(container.certPinStore),
            )
            shell(null, true, true) {
                TrustedServersScreen(
                    viewModel = vm,
                    onBack    = { navController.popBackStack() },
                )
            }
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
            shell(Tab.Movies, false, false) {
                DetailScreen(
                    viewModel          = viewModel,
                    trailerResumeSec   = trailerResume,
                    onPlay             = { id, resume ->
                        navController.navigate(Route.Player.route(id, resume))
                    },
                    onBack             = { navController.popBackStack() },
                    onOpenCollection   = openCollection,
                    onOpenPerson       = openPerson,
                    onOpenItem         = openItem,
                    onOpenStudio       = openStudio,
                )
            }
        }

        // ── Person detail (cast/crew tap-through) ────────────────────
        composable(
            route     = Route.Person.path,
            arguments = listOf(
                navArgument(Route.Person.ARG_PERSON_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            val rawId = entry.arguments?.getString(Route.Person.ARG_PERSON_ID) ?: return@composable
            val personId = java.net.URLDecoder.decode(rawId, "UTF-8")
            val vm = viewModel<PersonDetailViewModel>(
                factory = PersonDetailViewModel.factory(container.homeRepository, personId),
            )
            shell(null, true, false) {
                PersonDetailScreen(
                    viewModel  = vm,
                    onOpenItem = openItem,
                    onBack     = { navController.popBackStack() },
                )
            }
        }

        // ── Studio detail (chip tap-through) ─────────────────────────
        composable(
            route     = Route.Studio.path,
            arguments = listOf(
                navArgument(Route.Studio.ARG_STUDIO_SLUG) { type = NavType.StringType },
            ),
        ) { entry ->
            val rawSlug = entry.arguments?.getString(Route.Studio.ARG_STUDIO_SLUG) ?: return@composable
            val slug = java.net.URLDecoder.decode(rawSlug, "UTF-8")
            val vm = viewModel<StudioDetailViewModel>(
                factory = StudioDetailViewModel.factory(container.homeRepository, slug),
            )
            shell(null, true, false) {
                StudioDetailScreen(
                    viewModel  = vm,
                    onOpenItem = openItem,
                    onBack     = { navController.popBackStack() },
                )
            }
        }

        // ── Compartido contigo (federación) ──────────────────────────
        composable(Route.Shared.path) {
            val vm = viewModel<SharedViewModel>(
                factory = SharedViewModel.factory(container.peersRepository),
            )
            SharedScreen(
                viewModel     = vm,
                onOpenItem    = openShared,
                onResume      = { item -> playShared(item, item.resumePosSec) },
                onTabSelected = navigateToTab,
                onSettings    = openSettings,
            )
        }

        composable(
            route     = Route.SharedDetail.path,
            arguments = listOf(
                navArgument(Route.SharedDetail.ARG_PEER_ID) { type = NavType.StringType },
                navArgument(Route.SharedDetail.ARG_ITEM_ID) { type = NavType.StringType },
                navArgument(Route.SharedDetail.ARG_LIBRARY_ID) {
                    type = NavType.StringType; defaultValue = ""
                },
            ),
        ) { entry ->
            val target = sharedTargetOf(entry.arguments) ?: return@composable
            val vm = viewModel<SharedDetailViewModel>(
                factory = SharedDetailViewModel.factory(container.peersRepository, target),
            )
            shell(Tab.Shared, false, false) {
                SharedDetailScreen(
                    viewModel = vm,
                    onPlay    = playShared,
                    onBack    = { navController.popBackStack() },
                )
            }
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
            val collectionId = java.net.URLDecoder.decode(rawId, "UTF-8")
            val vm = viewModel<CollectionDetailViewModel>(
                factory = CollectionDetailViewModel.factory(container.homeRepository, collectionId),
            )
            shell(Tab.Collections, true, false) {
                CollectionDetailScreen(
                    viewModel  = vm,
                    onOpenItem = openItem,
                    onBack     = { navController.popBackStack() },
                )
            }
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
            shell(Tab.Series, false, false) {
                SeriesScreen(
                    viewModel        = viewModel,
                    onPlayEpisode    = { id, resume ->
                        navController.navigate(Route.Player.route(id, resume))
                    },
                    onBack           = { navController.popBackStack() },
                    onOpenPerson     = openPerson,
                    onOpenItem       = openItem,
                    onOpenStudio     = openStudio,
                    onOpenCollection = openCollection,
                )
            }
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
                navArgument(Route.Player.ARG_LIVE) {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument(Route.Player.ARG_PEER_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val request = playerRequestOf(entry.arguments) ?: return@composable
            val viewModel = viewModel<PlayerViewModel>(
                factory = PlayerViewModel.factory(container.playerDeps, request),
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

/** Los ids de amigo viajan URL-encoded en la ruta (pueden traer `:` o `/`). */
private fun decodeArg(raw: String?): String? = raw?.let { java.net.URLDecoder.decode(it, "UTF-8") }

private fun sharedTargetOf(args: android.os.Bundle?): SharedTarget? {
    val peerId = decodeArg(args?.getString(Route.SharedDetail.ARG_PEER_ID)) ?: return null
    val itemId = decodeArg(args?.getString(Route.SharedDetail.ARG_ITEM_ID)) ?: return null
    val library = decodeArg(args?.getString(Route.SharedDetail.ARG_LIBRARY_ID))?.takeIf { it.isNotBlank() }
    return SharedTarget(peerId = peerId, itemId = itemId, libraryId = library)
}

private fun playerRequestOf(args: android.os.Bundle?): PlayerRequest? {
    val itemId = args?.getString(Route.Player.ARG_ITEM_ID) ?: return null
    return PlayerRequest(
        itemId       = itemId,
        resumePosSec = args.getLong(Route.Player.ARG_RESUME),
        live         = args.getBoolean(Route.Player.ARG_LIVE),
        peerId       = decodeArg(args.getString(Route.Player.ARG_PEER_ID)),
    )
}

/** Ruta de cada subpantalla de Ajustes. */
private val SettingsTarget.route: Route
    get() = when (this) {
        SettingsTarget.HomeLayout     -> Route.HomeLayout
        SettingsTarget.TrustedServers -> Route.TrustedServers
        SettingsTarget.Devices        -> Route.Devices
    }

/** Ficha de un item según su tipo: serie, saga (colección) o película/episodio. */
private fun itemRoute(itemId: String, kind: MediaKind, trailerResumeSec: Long = 0L): String = when (kind) {
    MediaKind.Series     -> Route.Series.route(itemId)
    MediaKind.Collection -> Route.CollectionDetail.route(itemId)
    else                 -> Route.Detail.route(itemId, trailerResumeSec)
}
