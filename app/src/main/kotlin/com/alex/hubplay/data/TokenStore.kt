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

    /**
     * Hot derived state: any consumer composable can collectAsState() on
     * this and React to login / logout without a manual subscription.
     */
    val authStateFlow = combine().stateIn(
        scope        = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        started      = SharingStarted.Eagerly,
        initialValue = AuthState(false, null, null, null, null, null),
    )

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

    /**
     * Synchronous read of the persisted server URL. Used by the SSE
     * stream's reconnect loop, where suspending across the EventSource
     * factory call would complicate the cancellation story. Acceptable
     * because DataStore reads are cheap and the call happens at
     * connect-time, not per-event.
     */
    fun serverUrlBlocking(): String? = runBlocking { serverUrlFlow.first() }
}

data class AuthState(
    val isAuthenticated:   Boolean,
    val accessToken:       String?,
    val refreshToken:      String?,
    val serverUrl:         String?,
    val activeProfileId:   String? = null,
    val activeProfileName: String? = null,
)
