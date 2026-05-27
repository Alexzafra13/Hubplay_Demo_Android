package com.alex.hubplay.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    private val keyAccess           = stringPreferencesKey("access_token")
    private val keyRefresh          = stringPreferencesKey("refresh_token")
    private val keyServerUrl        = stringPreferencesKey("server_url")
    private val keyActiveProfileId  = stringPreferencesKey("active_profile_id")
    private val keyActiveProfileName = stringPreferencesKey("active_profile_name")

    val accessTokenFlow: Flow<String?>       = context.dataStore.data.map { it[keyAccess] }
    val refreshTokenFlow: Flow<String?>      = context.dataStore.data.map { it[keyRefresh] }
    val serverUrlFlow: Flow<String?>         = context.dataStore.data.map { it[keyServerUrl] }
    val activeProfileIdFlow: Flow<String?>   = context.dataStore.data.map { it[keyActiveProfileId] }
    val activeProfileNameFlow: Flow<String?> = context.dataStore.data.map { it[keyActiveProfileName] }

    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _authState = MutableStateFlow(AuthState(false, null, null, null, null, null))

    /**
     * Hot derived state: any consumer composable can collectAsState() on
     * this and react to login / logout without a manual subscription.
     *
     * Backed by a [MutableStateFlow] that mirrors DataStore emissions AND
     * accepts synchronous writes from the interceptor path (see
     * [storeTokensImmediate], [clearImmediate]). This eliminates
     * `runBlocking` in the OkHttp thread pool.
     */
    val authStateFlow: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        combine()
            .onEach { _authState.value = it }
            .launchIn(persistScope)
    }

    /** Non-blocking read for interceptors — always returns immediately. */
    fun snapshotNow(): AuthState = _authState.value

    /**
     * Update tokens synchronously in memory, then persist to disk async.
     * The interceptor can call [snapshotNow] right after and see the new
     * tokens without waiting for DataStore I/O.
     */
    fun storeTokensImmediate(access: String, refresh: String) {
        _authState.update { current ->
            current.copy(
                accessToken = access,
                refreshToken = refresh,
                isAuthenticated = access.isNotBlank() && !current.serverUrl.isNullOrBlank(),
            )
        }
        persistScope.launch { storeTokens(access, refresh) }
    }

    /**
     * Clear tokens synchronously in memory, then persist to disk async.
     * Used by [AuthInterceptor] when refresh fails — the reactive
     * [authStateFlow] pops the UI back to Login.
     */
    fun clearImmediate() {
        _authState.update {
            it.copy(
                accessToken = null,
                refreshToken = null,
                isAuthenticated = false,
                activeProfileId = null,
                activeProfileName = null,
            )
        }
        persistScope.launch { clear() }
    }

    private fun combine(): Flow<AuthState> {
        return kotlinx.coroutines.flow.combine(
            accessTokenFlow,
            refreshTokenFlow,
            serverUrlFlow,
            activeProfileIdFlow,
            activeProfileNameFlow,
        ) { access, refresh, server, profileId, profileName ->
            AuthState(
                isAuthenticated  = !access.isNullOrBlank() && !server.isNullOrBlank(),
                accessToken      = access,
                refreshToken     = refresh,
                serverUrl        = server,
                activeProfileId  = profileId,
                activeProfileName = profileName,
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

    /**
     * Persist the profile the user just picked in WhoIsWatching. We keep
     * the human-readable label too so the topbar / Settings can render
     * it without an extra fetch. Set to empty string to act as "clear",
     * but prefer [clearActiveProfile] for that.
     */
    suspend fun setActiveProfile(id: String, displayName: String?) {
        context.dataStore.edit { prefs ->
            prefs[keyActiveProfileId] = id
            if (!displayName.isNullOrBlank()) {
                prefs[keyActiveProfileName] = displayName
            } else {
                prefs.remove(keyActiveProfileName)
            }
        }
    }

    /**
     * Drop only the active-profile selection — keeps tokens + server URL
     * intact. Used by the "Cambiar perfil" entry in Settings to send the
     * user back to the picker without forcing a full re-login.
     */
    suspend fun clearActiveProfile() {
        context.dataStore.edit { prefs ->
            prefs.remove(keyActiveProfileId)
            prefs.remove(keyActiveProfileName)
        }
    }

    /**
     * "Log out" — drop tokens AND the active-profile selection, KEEP the
     * server URL so the user can re-pair against the same HubPlay
     * without re-typing the address. The expected flow afterwards is:
     * login screen → device-code pairing UI shows up with the URL
     * already filled.
     */
    suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.remove(keyAccess)
            prefs.remove(keyRefresh)
            prefs.remove(keyActiveProfileId)
            prefs.remove(keyActiveProfileName)
            // Keep serverUrl so the user doesn't have to retype it
            // after a session expiry / refresh chain revoke.
        }
    }

    /**
     * "Forget this server" — drop tokens, profile selection AND the
     * server URL. The user lands on the empty URL form, ready to point
     * the app at a different HubPlay (e.g. moved house, friend's library).
     */
    suspend fun forgetServer() {
        context.dataStore.edit { prefs ->
            prefs.remove(keyAccess)
            prefs.remove(keyRefresh)
            prefs.remove(keyActiveProfileId)
            prefs.remove(keyActiveProfileName)
            prefs.remove(keyServerUrl)
        }
    }

    /**
     * Synchronous variant for the rare moments where coroutine context
     * is awkward (e.g. logout button in nav graph). Acceptable because
     * DataStore's edit suspends only on disk IO, which is cheap.
     */
    fun clearBlocking() = runBlocking { clear() }
    fun forgetServerBlocking() = runBlocking { forgetServer() }
    fun clearActiveProfileBlocking() = runBlocking { clearActiveProfile() }

    /**
     * Read once for places that need the value right now (interceptor,
     * device-code finalisation). Suspends rather than blocks.
     */
    suspend fun snapshot(): AuthState = authStateFlow.first()

    /** @see snapshotNow — prefer that for new code. */
    fun serverUrlBlocking(): String? = snapshotNow().serverUrl
}

data class AuthState(
    val isAuthenticated:   Boolean,
    val accessToken:       String?,
    val refreshToken:      String?,
    val serverUrl:         String?,
    val activeProfileId:   String? = null,
    val activeProfileName: String? = null,
)
