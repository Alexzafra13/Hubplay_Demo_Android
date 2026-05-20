package com.alex.hubplay.data

import android.content.Context
import com.alex.hubplay.BuildConfig
import com.alex.hubplay.data.api.AuthApi
import com.alex.hubplay.data.api.HubplayApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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
        .add(KotlinJsonAdapterFactory())
        .build()

    /**
     * Bare OkHttp + Retrofit for the refresh endpoint. NO AuthInterceptor
     * to avoid recursing back into itself when a refresh returns 401.
     */
    private val refreshClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(BaseUrlInterceptor(tokenStore))
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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)   // 0 = no timeout — required for SSE
        .also { if (BuildConfig.DEBUG) it.addInterceptor(loggingInterceptor()) }
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

    val homeRepository: HomeRepository = HomeRepository(hubplayApi, tokenStore)

    val liveTvRepository: LiveTvRepository = LiveTvRepository(hubplayApi, tokenStore)

    /**
     * Server-Sent Events stream over `/me/events` — drives cross-device
     * sync of Continue Watching, played/unplayed and favourites.
     */
    val meEventsStream: MeEventsStream = MeEventsStream(mainOkHttp, tokenStore, moshi)

    /**
     * App-lifetime coroutine scope for background tasks that outlive any
     * single ViewModel — screensaver refresh, future telemetry uploads.
     * SupervisorJob so one failing child doesn't take the rest with it.
     */
    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Idle detection driving the in-app screensaver. */
    val idleController: IdleController = IdleController(appScope)

    /** Backdrop pool the screensaver crossfades between. */
    val screensaverImageSource: ScreensaverImageSource = ScreensaverImageSource(homeRepository)

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
