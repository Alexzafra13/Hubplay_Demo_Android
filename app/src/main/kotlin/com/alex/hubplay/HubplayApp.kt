package com.alex.hubplay

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.alex.hubplay.data.AppContainer

/**
 * Application entry point. Constructs the [AppContainer] which holds the
 * singletons (network, storage, repositories) the rest of the app pulls
 * via composition-local in Compose.
 *
 * Also wires Coil's singleton ImageLoader to use the same OkHttp client
 * the API layer uses — without this, image URLs that need bearer auth
 * (the entire `/images/file/{id}` surface on HubPlay) would 401 because
 * Coil's default network engine spins up its own OkHttp with no
 * interceptors. Routing image loads through `appContainer.mainOkHttp`
 * means every request automatically carries `Authorization: Bearer …`,
 * gets the BaseUrlInterceptor's host rewrite, and benefits from the
 * shared connection pool.
 *
 * Manual DI on purpose — Hilt/Koin add ~1.2 MB to the APK and a non-
 * trivial annotation surface for what is, today, ~7 singletons. If the
 * dependency graph grows past ~15 nodes we'll graduate to Hilt.
 */
class HubplayApp : Application(), SingletonImageLoader.Factory {

    /**
     * Lazy on purpose. Coil's SingletonImageLoader factory hook
     * (`newImageLoader`) can be invoked before `Application.onCreate()`
     * completes — any ContentProvider in a third-party dep that
     * triggers a Coil image load during its own initialisation will
     * pull on the singleton factory before our `onCreate` body has
     * run. With a `lateinit var` that races into an
     * UninitializedPropertyAccessException at startup; with `by lazy`
     * the container is built on first access regardless of who asks
     * first.
     */
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Touch the lazy so the construction cost happens here on the
        // main-thread startup path (predictable) rather than later on
        // whichever thread first reads .container.
        container.tokenStore
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = container.mainOkHttp))
            }
            .build()
    }
}
