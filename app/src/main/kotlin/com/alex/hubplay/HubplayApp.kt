package com.alex.hubplay

import android.app.Application
import com.alex.hubplay.data.AppContainer

/**
 * Application entry point. Constructs the [AppContainer] which holds the
 * singletons (network, storage, repositories) the rest of the app pulls
 * via composition-local in Compose.
 *
 * This is plain manual DI on purpose — Hilt/Koin add ~1.2 MB to the APK
 * and a non-trivial annotation surface for what is, today, ~5 singletons.
 * If the dependency graph grows past ~15 nodes we'll graduate to Hilt.
 */
class HubplayApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
