package com.alex.hubplay.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.SupervisorJob

/**
 * Persistent token storage backed by AndroidX DataStore.
 *
 * Why DataStore (and not EncryptedSharedPreferences):
 *   - DataStore exposes a Flow API that integrates with Compose
 *     state collection — the AuthState a UI watches is "free".
 *   - EncryptedSharedPreferences uses Android Keystore which has well-
 *     documented reliability issues on factory-reset / OEM ROM edge
 *     cases (see issue tracker #164901843). Refresh tokens with
 *     reuse-detection on the server side mean a stolen token without
 *     the device → backend chain is far less useful than the
 *     "encrypted at rest" wins suggest.
 *   - Tokens are excluded from auto-backup via res/xml/data_extraction_rules.xml
 *     so an attacker with another Google account can't restore them.
 *
 * If the threat model changes (e.g. shipping on rooted devices) we'd
 * wrap this with a per-install AES-GCM key sealed by the Keystore.
 */
class TokenStore(private val context: Context) {

    private val Context.dataStore by preferencesDataStore(name = "hubplay_auth")

    private val keyAccess     = stringPreferencesKey("access_token")
    private val keyRefresh    = stringPreferencesKey("refresh_token")
    private val keyServerUrl  = stringPreferencesKey("server_url")

    val accessTokenFlow: Flow<String?>  = context.dataStore.data.map { it[keyAccess] }
    val refreshTokenFlow: Flow<String?> = context.dataStore.data.map { it[keyRefresh] }
    val serverUrlFlow: Flow<String?>    = context.dataStore.data.map { it[keyServerUrl] }

    /**
     * Hot derived state: any consumer composable can collectAsState() on
     * this and React to login / logout without a manual subscription.
     */
    val authStateFlow = combine().stateIn(
        scope        = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        started      = SharingStarted.Eagerly,
        initialValue = AuthState(false, null, null, null),
    )

    private fun combine(): Flow<AuthState> {
        return kotlinx.coroutines.flow.combine(
            accessTokenFlow,
            refreshTokenFlow,
            serverUrlFlow,
        ) { access, refresh, server ->
            AuthState(
                isAuthenticated = !access.isNullOrBlank() && !server.isNullOrBlank(),
                accessToken     = access,
                refreshToken    = refresh,
                serverUrl       = server,
            )
        }
    }

    suspend fun store(serverUrl: String, access: String, refresh: String) {
        context.dataStore.edit { prefs ->
            prefs[keyServerUrl] = serverUrl.trimEnd('/')
            prefs[keyAccess]    = access
            prefs[keyRefresh]   = refresh
        }
    }

    suspend fun storeServerUrl(serverUrl: String) {
        context.dataStore.edit { prefs ->
            prefs[keyServerUrl] = serverUrl.trimEnd('/')
        }
    }

    suspend fun storeTokens(access: String, refresh: String) {
        context.dataStore.edit { prefs ->
            prefs[keyAccess]  = access
            prefs[keyRefresh] = refresh
        }
    }

    suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.remove(keyAccess)
            prefs.remove(keyRefresh)
            // Keep serverUrl so the user doesn't have to retype it
            // after a session expiry / refresh chain revoke.
        }
    }

    /**
     * Synchronous variant for the rare moments where coroutine context
     * is awkward (e.g. logout button in nav graph). Acceptable because
     * DataStore's edit suspends only on disk IO, which is cheap.
     */
    fun clearBlocking() = runBlocking { clear() }

    /**
     * Read once for places that need the value right now (interceptor,
     * device-code finalisation). Suspends rather than blocks.
     */
    suspend fun snapshot(): AuthState = authStateFlow.first()
}

data class AuthState(
    val isAuthenticated: Boolean,
    val accessToken:     String?,
    val refreshToken:    String?,
    val serverUrl:       String?,
)
