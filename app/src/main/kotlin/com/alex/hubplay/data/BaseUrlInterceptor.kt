package com.alex.hubplay.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Rewrites the request URL's authority (scheme/host/port) to whatever
 * the user pointed the app at during pairing.
 *
 * Why: Retrofit needs a baseUrl at *construction* time, but the user's
 * server URL isn't known until after they finish the Login screen. This
 * interceptor lets us hand Retrofit a placeholder (`http://server/`)
 * and rewrite it on every request.
 *
 * Reads the server URL from [TokenStore.snapshotNow] — a non-blocking
 * in-memory read. Falls back to the placeholder when no serverUrl is
 * stored — Retrofit will just fail the request with a connection error
 * which we handle upstream as "no server configured".
 */
class BaseUrlInterceptor(private val tokenStore: TokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val serverUrl = tokenStore.snapshotNow().serverUrl ?: return chain.proceed(original)

        val configured = serverUrl.toHttpUrl()
        val rewritten  = original.url.newBuilder()
            .scheme(configured.scheme)
            .host(configured.host)
            .port(configured.port)
            .build()

        return chain.proceed(original.newBuilder().url(rewritten).build())
    }
}
