package com.alex.hubplay.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ¿Tiene este servidor amigos emparejados? Decide si el menú lateral
 * enseña "Compartido" y si Inicio pide los rails de amigos. Mismo patrón
 * que [CollectionsAvailability], pero arranca en `false`: casi nadie tiene
 * federación y una entrada que aparece tras cargar molesta menos que una
 * que desaparece.
 */
class SharedAvailability(
    private val repository: PeersRepository,
    authStateFlow:          StateFlow<AuthState>,
    private val scope:      CoroutineScope,
) {
    private val _hasPeers = MutableStateFlow(false)
    val hasPeers: StateFlow<Boolean> = _hasPeers.asStateFlow()

    init {
        // Se recalcula al cambiar de servidor o de sesión (no en cada
        // refresco de token: los amigos son los mismos).
        authStateFlow
            .distinctUntilChangedBy { it.serverUrl to it.isAuthenticated }
            .onEach { state ->
                if (state.isAuthenticated) refresh() else _hasPeers.value = false
            }
            .launchIn(scope)
    }

    fun refresh() {
        scope.launch {
            runCatching { repository.hasPeers() }
                .onSuccess { _hasPeers.value = it }
                .onFailure {
                    // Servidor antiguo sin federación (404) o red caída: se
                    // deja como estaba en vez de parpadear.
                    Log.w(TAG, "peers availability: ${it.message}")
                }
        }
    }

    companion object { private const val TAG = "SharedAvailability" }
}
