package com.alex.hubplay.ui.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.CertChallenge
import com.alex.hubplay.data.CertChallengeBus
import com.alex.hubplay.data.DeviceCodeRepository
import com.alex.hubplay.data.DeviceCodeStart
import com.alex.hubplay.data.DeviceCodeStatus
import com.alex.hubplay.data.LanDiscovery
import com.alex.hubplay.data.LanServer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val certChallengeBus:     CertChallengeBus,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private val discoveryJob: Job

    /**
     * Auto-skip stage 1 when there's exactly one HubPlay server on the
     * LAN — same idea as Steam Link picking the only PC it finds. Set
     * to true once after the grace window so a user who explicitly
     * cancels pairing isn't yanked back into the same server again.
     */
    @Volatile private var autoSkipConsumed: Boolean = false

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
        // mDNS discovery is push-driven and never "finishes" by itself, so
        // a naive UI would show the "Buscando…" spinner forever on a LAN
        // that has nothing to announce (router blocks multicast, remote
        // user, etc.). Flip lanSearching off after a fixed window — the
        // discoveryJob keeps running underneath so a late arrival still
        // populates the list.
        viewModelScope.launch {
            delay(LAN_SEARCH_TIMEOUT_MS)
            _uiState.update { it.copy(lanSearching = false) }
        }
        viewModelScope.launch { armAutoSkip() }

        // Mirror the global cert-challenge bus into our UI state so the
        // Login screen renders the TOFU dialog. Auto-retry on accept:
        // the user just told us "yes, trust this cert" — the next call
        // through the same OkHttp client now succeeds, so re-running
        // pickServer with the stored URL completes the pairing without
        // them having to press Continue again.
        viewModelScope.launch {
            certChallengeBus.pending.collect { ch ->
                _uiState.update { it.copy(certChallenge = ch) }
            }
        }
        viewModelScope.launch {
            certChallengeBus.accepted.collect { ch ->
                val url = _uiState.value.serverUrl
                if (url.isNotBlank() && extractHost(url).equals(ch.host, ignoreCase = true)) {
                    pickServer(url)
                }
            }
        }
    }

    /**
     * Waits [AUTO_SKIP_GRACE_MS] after construction so mDNS has time to
     * surface more than one candidate (we don't want to commit to the
     * first announcer if a second is arriving 200 ms behind). Then, if
     * exactly one entry was discovered AND the user hasn't typed
     * anything AND we're still on stage 1, jump straight to pairing.
     */
    private suspend fun armAutoSkip() {
        delay(AUTO_SKIP_GRACE_MS)
        val state = _uiState.value
        if (autoSkipConsumed)                                return
        if (state.stage != LoginStage.ServerUrl)             return
        if (state.serverUrl.isNotBlank())                    return
        if (state.lanDiscovery.size != 1)                    return
        autoSkipConsumed = true
        pickServer(state.lanDiscovery.first().url, fromAuto = true)
    }

    fun onServerUrlChange(url: String) {
        // Any typing cancels the auto-skip race — user wants control.
        autoSkipConsumed = true
        _uiState.update { it.copy(serverUrl = url, error = null) }
    }

    /**
     * Drives stage 1 → stage 2 for both code paths: the user tapping a
     * LAN card / Continue, and the auto-skip when there's a single LAN
     * server. The [fromAuto] flag toggles a UI hint so the screen can
     * render "Conectando con HubPlay…" instead of a generic spinner.
     */
    fun pickServer(url: String, fromAuto: Boolean = false) {
        val normalized = normalizeServerUrl(url) ?: run {
            _uiState.update { it.copy(error = "URL inválida") }
            return
        }
        _uiState.update {
            it.copy(
                serverUrl     = normalized,
                isStarting    = true,
                error         = null,
                autoConnected = fromAuto,
            )
        }
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
                    // Walk the cause chain at WARN so logcat shows the
                    // root cause (cert subject, expiry, hostname mismatch,
                    // refused connection) without us having to ship a
                    // debug build. Grep with: `adb logcat | grep LoginVM`.
                    Log.w(TAG, "device/start failed for $normalized")
                    var cur: Throwable? = err
                    val seen = mutableSetOf<Throwable>()
                    while (cur != null && seen.add(cur)) {
                        Log.w(TAG, "  caused by ${cur::class.java.name}: ${cur.message}")
                        cur = cur.cause
                    }
                    _uiState.update {
                        it.copy(
                            isStarting    = false,
                            autoConnected = false,
                            error         = friendlyConnectError(err, normalized),
                        )
                    }
                },
            )
        }
    }

    /** Continue button on the URL input — kept as a thin alias of pickServer. */
    fun onContinueClicked() {
        pickServer(_uiState.value.serverUrl)
    }

    /** User tapped "Confiar y conectar" in the TOFU dialog. */
    fun acceptCertChallenge(challenge: CertChallenge) {
        certChallengeBus.accept(challenge)
        // Don't clear the error yet — auto-retry might fail for another
        // reason. The collector above wipes it on retry success / new
        // failure. We DO clear the pending challenge so the dialog goes
        // away immediately; the bus's compareAndSet handles the race.
        _uiState.update { it.copy(certChallenge = null, error = null, isStarting = true) }
    }

    /** User tapped "Cancelar" in the TOFU dialog. */
    fun dismissCertChallenge() {
        certChallengeBus.dismiss()
        _uiState.update { it.copy(certChallenge = null) }
    }

    fun onCancelPairing() {
        pollingJob?.cancel()
        pollingJob = null
        // Cancelling means "I want to pick another server" — never
        // auto-skip back into the same one we just left.
        autoSkipConsumed = true
        _uiState.update {
            it.copy(
                stage         = LoginStage.ServerUrl,
                pairingStart  = null,
                pollStatus    = null,
                autoConnected = false,
            )
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
        private const val TAG = "LoginVM"

        /**
         * Grace window before auto-skip considers "only one server" to
         * be the final count. Long enough that mDNS announcers arriving
         * within a few hundred ms of each other all get counted; short
         * enough that the user doesn't notice the wait on a single-server
         * LAN.
         */
        private const val AUTO_SKIP_GRACE_MS = 1_200L

        /**
         * After this many ms with no hits we drop the "Buscando en tu red…"
         * indicator. Discovery itself keeps running so a delayed announcer
         * still surfaces — only the UI hint goes quiet.
         */
        private const val LAN_SEARCH_TIMEOUT_MS = 6_000L

        fun factory(
            repository:       DeviceCodeRepository,
            lanDiscovery:     LanDiscovery,
            certChallengeBus: CertChallengeBus,
        ) = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LoginViewModel(repository, lanDiscovery, certChallengeBus) as T
                }
            }
    }
}

/** Used by the auto-retry hook to compare bus.host against the typed URL. */
private fun extractHost(url: String): String =
    runCatching { java.net.URI(url).host.orEmpty() }.getOrNull().orEmpty()

/**
 * Translate the raw exception coming out of `/auth/device/start` into a
 * short, actionable message instead of dumping JDK-internal jargon
 * ("Chain validation failed", "Trust anchor for certification path not
 * found", "Hostname not verified") at the user.
 *
 * We deliberately do NOT offer a "trust anyway" escape hatch — a self-
 * hosted media server with a bad cert is most likely a deployment
 * mistake the user can fix, and silently bypassing TLS would hand any
 * downstream proxy the bearer token in cleartext.
 *
 * Pulled out as a top-level helper so tests can exercise it without
 * standing up the OkHttp / Retrofit chain.
 */
internal fun friendlyConnectError(err: Throwable, attemptedUrl: String): String {
    // Walk the cause chain once — TLS/network failures are usually
    // wrapped two levels deep (OkHttp → SSLException → CertPath…).
    var cur: Throwable? = err
    val seen = mutableSetOf<Throwable>()
    while (cur != null && seen.add(cur)) {
        val name = cur::class.java.simpleName
        val msg  = cur.message.orEmpty()
        when {
            name.contains("SSLPeerUnverified") ||
            name.contains("CertPath")          ||
            name.contains("Certificate")       ||
            "trust anchor" in msg.lowercase()  ||
            "chain validation" in msg.lowercase() -> {
                val isHttps = attemptedUrl.startsWith("https://", ignoreCase = true)
                return if (isHttps) {
                    "El servidor presentó un certificado que Android no pudo validar. " +
                        "Comprueba que la URL es correcta y que el certificado es válido."
                } else {
                    "Error de certificado al contactar con el servidor."
                }
            }
            name.contains("SSLHandshake") -> {
                return "No se pudo establecer una conexión segura con el servidor. " +
                    "Revisa la URL y vuelve a intentarlo."
            }
            name.contains("UnknownHost") -> {
                return "No se pudo resolver el host. Comprueba la URL y tu conexión."
            }
            name.contains("ConnectException") ||
            name.contains("SocketTimeout")    ||
            name.contains("ConnectTimeout") -> {
                return "No se pudo conectar al servidor. Comprueba que esté encendido y accesible."
            }
        }
        cur = cur.cause
    }
    return err.message?.takeIf { it.isNotBlank() } ?: "Error de conexión"
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

@androidx.compose.runtime.Immutable
data class LoginUiState(
    val serverUrl:     String = "",
    val isStarting:    Boolean = false,
    val stage:         LoginStage = LoginStage.ServerUrl,
    val pairingStart:  DeviceCodeStart? = null,
    val pollStatus:    DeviceCodeStatus? = null,
    val error:         String? = null,
    val lanSearching:  Boolean = false,
    val lanDiscovery:  List<LanServer> = emptyList(),
    /**
     * Set when [PinnedCertTrustManager] published an "unknown cert"
     * challenge for the URL the user is trying. Drives the TOFU dialog.
     */
    val certChallenge: CertChallenge? = null,
    /**
     * True when stage 1 was skipped automatically because a single LAN
     * server was the obvious pick. Drives the "Conectando con HubPlay…"
     * overlay so the user sees the auto-decision happen rather than a
     * naked spinner.
     */
    val autoConnected: Boolean = false,
)

enum class LoginStage { ServerUrl, Pairing }
