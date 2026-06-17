package com.alex.hubplay.data

import android.content.Context
import com.alex.hubplay.BuildConfig
import com.alex.hubplay.data.api.AuthApi
import com.alex.hubplay.data.api.HubplayApi
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

/**
 * Manual DI container — see HubplayApp.kt for the rationale (no Hilt
 * yet). Each field is a singleton lazily constructed on first access
 * and held for the process lifetime.
 *
 * Construction order matters here because some clients depend on
 * others (the main api needs the AuthInterceptor which needs a
 * separate "refresh" Retrofit instance with no auth interceptor).
 */
class AppContainer(context: Context) {

    val tokenStore: TokenStore = TokenStore(context.applicationContext)

    val authStateFlow = tokenStore.authStateFlow

    private val moshi: Moshi = Moshi.Builder()
        .build()

    /**
     * App-lifetime coroutine scope for background tasks that outlive any
     * single ViewModel — screensaver refresh, future telemetry uploads,
     * cert-pin file writes triggered by the dialog. SupervisorJob so one
     * failing child doesn't take the rest with it.
     */
    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * TOFU certificate pinning store + the bus the trust manager uses to
     * surface "unknown cert, want to trust?" to the UI. Both clients
     * (refresh + main) and any ExoPlayer OkHttpDataSource we wire up
     * must share the same TrustManager / HostnameVerifier — otherwise
     * the HLS segment fetches bypass our pin store and hard-fail on the
     * same cert the user just accepted in the dialog.
     */
    val certPinStore:     CertPinStore     = CertPinStore(context.applicationContext)
    val certChallengeBus: CertChallengeBus = CertChallengeBus(certPinStore, appScope)
    private val pinnedTrustManager: PinnedCertTrustManager =
        PinnedCertTrustManager(certPinStore, certChallengeBus)
    private val pinnedHostnameVerifier = PinnedHostnameVerifier(certPinStore)
    private val pinnedSslSocketFactory = SSLContext.getInstance("TLS")
        .apply { init(null, arrayOf<TrustManager>(pinnedTrustManager), null) }
        .socketFactory

    /**
     * Bare OkHttp + Retrofit for the refresh endpoint. NO AuthInterceptor
     * to avoid recursing back into itself when a refresh returns 401.
     */
    private val refreshClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(BaseUrlInterceptor(tokenStore))
        .sslSocketFactory(pinnedSslSocketFactory, pinnedTrustManager)
        .hostnameVerifier(pinnedHostnameVerifier)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .also { if (BuildConfig.DEBUG) it.addInterceptor(loggingInterceptor()) }
        .build()

    private val refreshRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(PLACEHOLDER_BASE_URL)
        .client(refreshClient)
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val refreshAuthApi: AuthApi = refreshRetrofit.create(AuthApi::class.java)

    /**
     * The "real" client every screen consumes. Adds Bearer + auto-refresh
     * + base-URL rewrite. Long timeouts on read because /me/events SSE
     * holds the connection open indefinitely. Exposed so Coil's
     * SingletonImageLoader can route image fetches through the same
     * authenticated client (otherwise /images/file/{id} returns 401).
     */
    val mainOkHttp: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(BaseUrlInterceptor(tokenStore))
        .addInterceptor(AuthInterceptor(tokenStore, refreshAuthApi))
        .sslSocketFactory(pinnedSslSocketFactory, pinnedTrustManager)
        .hostnameVerifier(pinnedHostnameVerifier)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .also { if (BuildConfig.DEBUG) it.addInterceptor(loggingInterceptor()) }
        .build()

    /**
     * Derived client for SSE (/me/events) and HLS streaming — infinite
     * read timeout so long-lived connections don't get killed. Shares the
     * connection pool, auth interceptor, and TLS config with [mainOkHttp].
     */
    val sseOkHttp: OkHttpClient = mainOkHttp.newBuilder()
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(PLACEHOLDER_BASE_URL)
        .client(mainOkHttp)
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val deviceCodeRepository: DeviceCodeRepository = DeviceCodeRepository(
        authApi    = retrofit.create(AuthApi::class.java),
        tokenStore = tokenStore,
    )

    /** mDNS-based LAN discovery for the login screen. */
    val lanDiscovery: LanDiscovery = LanDiscovery(context.applicationContext)

    /**
     * The hand-written API surface the Home + Player screens consume
     * today. Lives alongside the auto-generated `AuthApi` until we move
     * everything to the generated client.
     */
    val hubplayApi: HubplayApi = retrofit.create(HubplayApi::class.java)

    val homeRepository: HomeRepository = HomeRepositoryImpl(hubplayApi, tokenStore)

    /**
     * Per-library channel order + hidden-set persistence. Used by the
     * reorder screen to write, and by [LiveTvViewModel] to read on
     * inventory load.
     */
    val channelOrderStore: ChannelOrderStore = ChannelOrderStore(context.applicationContext)

    val liveTvRepository: LiveTvRepository = LiveTvRepository(hubplayApi, tokenStore)

    /**
     * Federation — browse/play content shared by paired peer servers.
     * Pairing is admin-only (web); the TV client only consumes peers that
     * are already paired. Best-effort: a slow/offline peer never blocks the UI.
     */
    val federationRepository: FederationRepository = FederationRepositoryImpl(hubplayApi, tokenStore)

    /**
     * "Who's watching?" multi-profile picker. The repository talks to
     * `/me/profiles` (list) and `/auth/switch-profile` (mint new bearer
     * for the target profile) and persists the active selection back
     * into [TokenStore]. The NavGraph gates Home behind a non-null
     * `activeProfileId`.
     */
    val profileRepository: ProfileRepository = ProfileRepository(hubplayApi, tokenStore)

    /**
     * Server-Sent Events stream over `/me/events` — drives cross-device
     * sync of Continue Watching, played/unplayed and favourites.
     */
    val meEventsStream: MeEventsStream = MeEventsStream(sseOkHttp, tokenStore, moshi)

    /**
     * Drives the visibility of the "Colecciones" tab in TopNav — we
     * hide it on libraries that have no TMDb sagas matched yet so the
     * user doesn't land on an empty grid. Refreshes on login / forget
     * server transitions.
     */
    val collectionsAvailability: CollectionsAvailability =
        CollectionsAvailability(homeRepository, authStateFlow, appScope)

    /** Idle detection driving the in-app screensaver. */
    val idleController: IdleController = IdleController(appScope)

    /** Backdrop pool the screensaver crossfades between. */
    val screensaverImageSource: ScreensaverImageSource = ScreensaverImageSource(homeRepository)

    /**
     * On-device crash logger. Installed and attached from
     * HubplayApp.onCreate (before any composables can mount) so the
     * Settings screen can read recent traces via this reference.
     * Nullable here so unit tests don't have to bring up the
     * application-level handler.
     */
    @Volatile
    private var _crashLogger: CrashLogger? = null
    val crashLogger: CrashLogger? get() = _crashLogger
    fun attachCrashLogger(logger: CrashLogger) { _crashLogger = logger }

    init {
        // Keep the screensaver pool in sync with the paired session — on
        // login (or server switch) we re-fetch so the slideshow matches
        // the user's actual library. distinctUntilChangedBy avoids
        // re-fetching on every token refresh.
        authStateFlow
            .distinctUntilChangedBy { it.serverUrl to it.isAuthenticated }
            .onEach { state ->
                if (state.isAuthenticated) {
                    appScope.launch { runCatching { screensaverImageSource.refresh() } }
                }
            }
            .launchIn(appScope)
    }

    private fun loggingInterceptor() = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    companion object {
        /**
         * Retrofit demands a baseUrl at construction. Our real server URL
         * isn't known until the user pairs, so we hand it a placeholder
         * and let BaseUrlInterceptor rewrite at request time.
         */
        private const val PLACEHOLDER_BASE_URL = "http://server.placeholder/api/v1/"
    }
}
