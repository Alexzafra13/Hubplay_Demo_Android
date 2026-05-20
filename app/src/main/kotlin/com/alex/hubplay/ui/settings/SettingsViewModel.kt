package com.alex.hubplay.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.BuildConfig
import com.alex.hubplay.data.AuthState
import com.alex.hubplay.data.CrashLogger
import com.alex.hubplay.data.TokenStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Static-ish account / device settings.
 *
 * Currently exposes:
 *   - The paired server URL (read-only label).
 *   - The app version + build flavor for support contexts.
 *   - Two distinct exit doors:
 *       · Log out  → drop tokens, keep the server URL so the user can
 *                    re-pair into the same instance without retyping it.
 *       · Forget server → drop everything, back to the empty URL form.
 *     Previously these were one button; splitting matches what
 *     real-world users expect (mostly "session expired, re-log" vs.
 *     "I'm leaving this server entirely").
 *   - The recent on-device crash log (if any) for support.
 *
 * Future home for: stream-quality cap, default subtitle language,
 * remember-position-on-back, theme, multi-account.
 */
class SettingsViewModel(
    private val tokenStore:   TokenStore,
    private val crashLogger:  CrashLogger?,
) : ViewModel() {

    val ui: StateFlow<SettingsUiState> = tokenStore.authStateFlow
        .map { state -> state.toUi() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), SettingsUiState())

    private fun AuthState.toUi() = SettingsUiState(
        serverUrl    = serverUrl,
        appVersion   = BuildConfig.VERSION_NAME,
        buildFlavor  = if (BuildConfig.DEBUG) "debug" else "release",
    )

    /** Read the crash-log file. Returns "" when no crashes recorded. */
    fun readCrashLog(): String = crashLogger?.read().orEmpty()

    /** Erase the crash-log file. */
    fun clearCrashLog() { crashLogger?.clear() }

    companion object {
        fun factory(tokenStore: TokenStore, crashLogger: CrashLogger?) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(tokenStore, crashLogger) as T
                }
            }
    }
}

@androidx.compose.runtime.Immutable
data class SettingsUiState(
    val serverUrl:   String? = null,
    val appVersion:  String  = "",
    val buildFlavor: String  = "",
)
