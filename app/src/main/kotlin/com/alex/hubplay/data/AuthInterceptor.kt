package com.alex.hubplay.data

import com.alex.hubplay.data.api.AuthApi
import com.alex.hubplay.data.api.dto.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bearer-token interceptor with automatic refresh on 401.
 *
 * Flow:
 *   1. Inject Authorization: Bearer <accessToken> on every request.
 *   2. If the response is 401, lock and try a refresh exactly once.
 *   3. On refresh success: store the new tokens, retry the original
 *      request once with the fresh access token.
 *   4. On refresh failure: wipe tokens. The reactive AuthState flow
 *      will pop the UI back to Login automatically.
 *
 * The AtomicBoolean serializes refreshes across concurrent failed
 * requests — without it 5 parallel 401s would each kick off their own
 * refresh and race. The first one that wins drains the queue.
 *
 * NOTE: AuthApi here is a *different* Retrofit instance than the main
 * api (no interceptor) so we can't recurse infinitely. Constructed in
 * AppContainer.
 */
class AuthInterceptor(
    private val tokenStore: TokenStore,
    private val refreshApi: AuthApi,
) : Interceptor {

    private val isRefreshing = AtomicBoolean(false)

    override fun intercept(chain: Interceptor.Chain): Response {
        val state    = runBlocking { tokenStore.snapshot() }
        val original = chain.request()
        val accessToken = state.accessToken

        // Auth endpoints don't need (and would loop on) bearer auth.
        val skipAuth = original.url.encodedPath.let {
            it.endsWith("/auth/login")    ||
            it.endsWith("/auth/refresh")  ||
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

        // Try a single refresh + retry. Whoever sets isRefreshing wins;
        // the others read the freshly stored token and retry too.
        val refreshToken = state.refreshToken ?: return response.alsoWipe()
        if (isRefreshing.compareAndSet(false, true)) {
            try {
                val refreshed = runCatching {
                    runBlocking { refreshApi.refresh(RefreshRequest(refreshToken = refreshToken)) }
                }.getOrNull() ?: return response.alsoWipe()

                runBlocking {
                    tokenStore.storeTokens(
                        access  = refreshed.accessToken  ?: return@runBlocking,
                        refresh = refreshed.refreshToken ?: return@runBlocking,
                    )
                }
            } finally {
                isRefreshing.set(false)
            }
        } else {
            // Another thread is refreshing — give it a beat. The retry
            // below picks up whichever access token is now stored.
            Thread.sleep(150)
        }

        // Retry once with the fresh token.
        response.close()
        val newAccess = runBlocking { tokenStore.snapshot().accessToken }
            ?: return chain.proceed(request)
        val retried = original.newBuilder()
            .header("Authorization", "Bearer $newAccess")
            .build()
        return chain.proceed(retried)
    }

    private fun Response.alsoWipe(): Response {
        runBlocking { tokenStore.clear() }
        return this
    }
}
