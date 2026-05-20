package com.alex.hubplay

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
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
        // Install the uncaught-exception handler FIRST so we capture
        // crashes that happen during the rest of onCreate too. The
        // logger delegates to the previous handler so the app still
        // dies on a real crash — we just record the trace first.
        com.alex.hubplay.data.CrashLogger.install(this).also {
            container.attachCrashLogger(it)
        }
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
            // ─── Memory cache ────────────────────────────────────────────
            // Default is 25 % of app heap, which on a TV box with a
            // generous heap (~256 MB) is more than enough but on a low-end
            // phone caps out around 50 MB — too small for the screensaver
            // pool + Home rails on 4K backdrops. Pinning to 30 % is a
            // measured bump: enough that the visible 4-5 backdrops in the
            // screensaver crossfade never evict each other, conservative
            // enough that we don't crowd out video playback.
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, percent = 0.30)
                    .strongReferencesEnabled(true)
                    .build()
            }
            // ─── Disk cache ──────────────────────────────────────────────
            // Defaults to ~2 % of free space which is excessive on a TV
            // (4 GB-ish on a 200 GB box). 256 MB caps it sensibly while
            // leaving room for ~5 000 backdrops at typical sizes — a year
            // of casual browsing.
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024L * 1024L)
                    .build()
            }
            .crossfade(300)
            .build()
    }
}
