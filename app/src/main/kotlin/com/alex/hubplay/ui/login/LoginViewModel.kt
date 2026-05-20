package com.alex.hubplay.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.DeviceCodeRepository
import com.alex.hubplay.data.DeviceCodeStart
import com.alex.hubplay.data.DeviceCodeStatus
import com.alex.hubplay.data.LanDiscovery
import com.alex.hubplay.data.LanServer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the Login screen.
 *
 *  Stage 1 — server URL: user types a URL or taps a discovered LAN server,
 *  then Continue → POST /auth/device/start.
 *  Stage 2 — pairing wait: poll /auth/device/poll while the user approves
 *  the code from another device (either by scanning the QR or typing the
 *  short user_code).
 *
 *  mDNS discovery starts on init() and keeps running across both stages
 *  (cheap, push-driven, no battery cost while idle). It stops when this
 *  ViewModel is cleared.
 */
class LoginViewModel(
    private val deviceCodeRepository: DeviceCodeRepository,
    private val lanDiscovery:         LanDiscovery,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private val discoveryJob: Job

    init {
        discoveryJob = viewModelScope.launch {
            _uiState.update { it.copy(lanSearching = true) }
            lanDiscovery.discover().collect { entry ->
                _uiState.update { state ->
                    if (state.lanDiscovery.any { it.url == entry.url }) state
                    else state.copy(lanDiscovery = state.lanDiscovery + entry)
                }
            }
        }
    }

    fun onServerUrlChange(url: String) {
        _uiState.update { it.copy(serverUrl = url, error = null) }
    }

    fun onContinueClicked() {
        val raw = _uiState.value.serverUrl.trim()
        val normalized = normalizeUrl(raw) ?: run {
            _uiState.update { it.copy(error = "URL inválida") }
            return
        }

        _uiState.update { it.copy(isStarting = true, error = null) }
        viewModelScope.launch {
            val result = runCatching { deviceCodeRepository.start(normalized) }
            result.fold(
                onSuccess = { start ->
                    _uiState.update {
                        it.copy(
                            isStarting   = false,
                            stage        = LoginStage.Pairing,
                            pairingStart = start,
                            pollStatus   = DeviceCodeStatus.Pending,
                        )
                    }
                    startPolling(start)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isStarting = false, error = err.message ?: "Error de conexión")
                    }
                },
            )
        }
    }

    fun onCancelPairing() {
        pollingJob?.cancel()
        pollingJob = null
        _uiState.update {
            it.copy(stage = LoginStage.ServerUrl, pairingStart = null, pollStatus = null)
        }
    }

    private fun startPolling(start: DeviceCodeStart) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            deviceCodeRepository.poll(start).collect { status ->
                _uiState.update { it.copy(pollStatus = status) }
            }
        }
    }

    /**
     * Accepts `hubplay.duckdns.org`, `https://hubplay.duckdns.org`,
     * `http://192.168.1.50:8096`, etc. Returns null if it can't be
     * parsed into something that has at least a host. Strips trailing
     * slashes so the BaseUrlInterceptor's substitution is clean.
     *
     * Visible for tests.
     */
    internal fun normalizeUrl(input: String): String? = normalizeServerUrl(input)

    companion object {
        fun factory(repository: DeviceCodeRepository, lanDiscovery: LanDiscovery) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LoginViewModel(repository, lanDiscovery) as T
                }
            }
    }
}

/**
 * Pulled out as a top-level helper so tests can exercise it without
 * spinning up a ViewModel (which drags Android lifecycle dependencies).
 */
internal fun normalizeServerUrl(input: String): String? {
    if (input.isBlank()) return null
    val trimmed = input.trim()
    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://$trimmed"
    }
    val parsed = runCatching { java.net.URI(withScheme) }.getOrNull() ?: return null
    if (parsed.host.isNullOrBlank()) return null
    // Force the URI through toURL() to validate the scheme/host combo
    // then trim trailing slashes — BaseUrlInterceptor concatenates with
    // a leading `/` so doubled slashes break the path rewrite.
    val url = runCatching { parsed.toURL() }.getOrNull() ?: return null
    return url.toString().trimEnd('/')
}

data class LoginUiState(
    val serverUrl:     String = "",
    val isStarting:    Boolean = false,
    val stage:         LoginStage = LoginStage.ServerUrl,
    val pairingStart:  DeviceCodeStart? = null,
    val pollStatus:    DeviceCodeStatus? = null,
    val error:         String? = null,
    val lanSearching:  Boolean = false,
    val lanDiscovery:  List<LanServer> = emptyList(),
)

enum class LoginStage { ServerUrl, Pairing }
