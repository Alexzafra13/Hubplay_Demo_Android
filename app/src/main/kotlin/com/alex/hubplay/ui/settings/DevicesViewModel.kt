package com.alex.hubplay.ui.settings

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.TokenStore
import com.alex.hubplay.data.api.HubplayApi
import com.alex.hubplay.data.api.dto.MySessionDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Ajustes → "Dispositivos conectados": las sesiones del usuario (lo que la
 * web llama "Tus dispositivos"), con esta tele marcada y la opción de
 * cerrar la sesión de cualquier otro aparato. La propia no se cierra desde
 * aquí: para eso está "Cerrar sesión".
 */
class DevicesViewModel(
    private val api:        HubplayApi,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(DevicesUiState(isLoading = true))
    val ui: StateFlow<DevicesUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(isLoading = true, error = false) }
        viewModelScope.launch {
            runCatching { api.listMySessions(tokenStore.snapshotNow().refreshToken).data.orEmpty() }
                .onSuccess { rows -> _ui.value = DevicesUiState(devices = sortDevices(rows.map(::toDevice))) }
                .onFailure {
                    Log.w(TAG, "list sessions: ${it.message}")
                    _ui.update { s -> s.copy(isLoading = false, error = true) }
                }
        }
    }

    /** Cierra la sesión de otro aparato (optimista; si falla, vuelve a la lista real). */
    fun revoke(device: Device) {
        if (device.current) return
        _ui.update { it.copy(devices = it.devices.filterNot { d -> d.id == device.id }) }
        viewModelScope.launch {
            runCatching { api.revokeMySession(device.id) }
                .onFailure {
                    Log.w(TAG, "revoke session ${device.id}: ${it.message}")
                    load()
                }
        }
    }

    companion object {
        private const val TAG = "DevicesViewModel"

        fun factory(api: HubplayApi, tokenStore: TokenStore) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DevicesViewModel(api, tokenStore) as T
        }

        /** Esta tele primero; luego el más reciente. */
        fun sortDevices(devices: List<Device>): List<Device> =
            devices.sortedWith(compareByDescending<Device> { it.current }.thenByDescending { it.lastActive })

        fun toDevice(dto: MySessionDto) = Device(
            id         = dto.id,
            name       = dto.deviceName?.takeIf { it.isNotBlank() },
            ip         = dto.ipAddress?.takeIf { it.isNotBlank() },
            lastActive = parseInstant(dto.lastActiveAt) ?: parseInstant(dto.createdAt),
            current    = dto.current,
            linked     = dto.authMethod == "device_link",
        )

        private fun parseInstant(value: String?): Instant? =
            value?.let { runCatching { Instant.parse(it) }.getOrNull() }
    }
}

@Immutable
data class DevicesUiState(
    val isLoading: Boolean = false,
    val error:     Boolean = false,
    val devices:   List<Device> = emptyList(),
)

/** Un aparato con sesión abierta. */
@Immutable
data class Device(
    val id:         String,
    val name:       String?,
    val ip:         String?,
    val lastActive: Instant?,
    /** Esta misma tele. */
    val current:    Boolean,
    /** Emparejado con código / QR (TV) en vez de usuario y contraseña. */
    val linked:     Boolean,
)
