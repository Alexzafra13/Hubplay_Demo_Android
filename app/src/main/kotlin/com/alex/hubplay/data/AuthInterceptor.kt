package com.alex.hubplay.data

import com.alex.hubplay.data.api.AuthApi
import com.alex.hubplay.data.api.dto.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Bearer-token interceptor with automatic refresh on 401.
 *
 * Token reads use [TokenStore.snapshotNow] — a non-blocking in-memory
 * read from the hot [MutableStateFlow]. Token writes after a successful
 * refresh use [TokenStore.storeTokensImmediate] which updates the
 * in-memory state synchronously and persists to disk asynchronously, so
 * the retry reads the fresh token without waiting for DataStore I/O.
 *
 * Refresh coordination: `synchronized(refreshLock)` ensures exactly one
 * thread refreshes; others block on the monitor, then double-check
 * whether the token was already updated before attempting their own
 * refresh. This eliminates the `Thread.sleep(150)` race from the
 * previous `AtomicBoolean` approach.
 *
 * The only remaining `runBlocking` wraps the Retrofit suspend call to
 * the refresh endpoint — that's a plain HTTP call on OkHttp's thread
 * pool, which is exactly what those threads are designed for. The
 * refresh client is a separate [OkHttpClient] without this interceptor,
 * so recursion is impossible.
 */
class AuthInterceptor(
    private val tokenStore: TokenStore,
    private val refreshApi: AuthApi,
) : Interceptor {

    private val refreshLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val accessToken = tokenStore.snapshotNow().accessToken
        val original = chain.request()

        val skipAuth = original.url.encodedPath.let {
            it.endsWith("/auth/login") ||
            it.endsWith("/auth/refresh") ||
            it.endsWith("/auth/device/start") ||
            it.endsWith("/auth/device/poll")
        }

        val request = if (!skipAuth && !accessToken.isNullOrBlank()) {
            original.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .build()
        } else {
            original
        }

        val response = chain.proceed(request)
        if (response.code != 401 || skipAuth) return response

        synchronized(refreshLock) {
            val current = tokenStore.snapshotNow()
            if (current.accessToken != null && current.accessToken != accessToken) {
                response.close()
                return chain.proceed(
                    original.newBuilder()
                        .header("Authorization", "Bearer ${current.accessToken}")
                        .build(),
                )
            }

            val refreshToken = current.refreshToken
            if (refreshToken.isNullOrBlank()) {
                tokenStore.clearImmediate()
                return response
            }

            val refreshed = runCatching {
                runBlocking { refreshApi.refresh(RefreshRequest(refreshToken = refreshToken)) }
            }.getOrNull()

            val newAccess = refreshed?.data?.accessToken
            val newRefresh = refreshed?.data?.refreshToken

            if (newAccess != null && newRefresh != null) {
                tokenStore.storeTokensImmediate(newAccess, newRefresh)
            } else {
                tokenStore.clearImmediate()
                return response
            }
        }

        response.close()
        val freshToken = tokenStore.snapshotNow().accessToken
            ?: return chain.proceed(request)
        return chain.proceed(
            original.newBuilder()
                .header("Authorization", "Bearer $freshToken")
                .build(),
        )
    }
}
