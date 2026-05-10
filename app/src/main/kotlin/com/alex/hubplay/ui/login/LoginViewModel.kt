package com.alex.hubplay.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.DeviceCodeRepository
import com.alex.hubplay.data.DeviceCodeStart
import com.alex.hubplay.data.DeviceCodeStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the Login screen. Two stages:
 *
 *  Stage 1 — server URL: user types `https://hubplay.duckdns.org`,
 *  taps Continue. We POST /auth/device/start to get a user_code.
 *
 *  Stage 2 — pairing wait: we display the user_code and poll the
 *  server. When the server flips to "approved" we store the tokens
 *  via DeviceCodeRepository and the host composable navigates Home.
 *
 *  Errors are surfaced as a string in [LoginUiState.error]; the screen
 *  renders them as a snackbar/banner without going back to stage 1
 *  (so the user can retry without retyping the URL).
 */
class LoginViewModel(
    private val deviceCodeRepository: DeviceCodeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

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
                if (status is DeviceCodeStatus.Approved) {
                    // Host composable observes the AuthState flow and
                    // pops to Home — this VM doesn't navigate directly
                    // so it stays decoupled from NavController.
                }
            }
        }
    }

    /**
     * Accepts `hubplay.duckdns.org`, `https://hubplay.duckdns.org`,
     * `http://192.168.1.50:8096`, etc. Returns null if it can't be
     * parsed into something that has at least a host. Strips trailing
     * slashes so the BaseUrlInterceptor's substitution is clean.
     */
    private fun normalizeUrl(input: String): String? {
        if (input.isBlank()) return null
        val withScheme = if (input.startsWith("http://") || input.startsWith("https://")) {
            input
        } else {
            "https://$input"
        }
        return runCatching { java.net.URI(withScheme).toURL() }
            .getOrNull()
            ?.toString()
            ?.trimEnd('/')
    }

    companion object {
        fun factory(repository: DeviceCodeRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LoginViewModel(repository) as T
            }
        }
    }
}

data class LoginUiState(
    val serverUrl:    String = "",
    val isStarting:   Boolean = false,
    val stage:        LoginStage = LoginStage.ServerUrl,
    val pairingStart: DeviceCodeStart? = null,
    val pollStatus:   DeviceCodeStatus? = null,
    val error:        String? = null,
)

enum class LoginStage { ServerUrl, Pairing }
