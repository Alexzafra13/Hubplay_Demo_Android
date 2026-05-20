package com.alex.hubplay.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.BuildConfig
import com.alex.hubplay.data.AuthState
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
 *
 * Future home for: stream-quality cap, default subtitle language,
 * remember-position-on-back, theme, multi-account.
 */
class SettingsViewModel(
    private val tokenStore: TokenStore,
) : ViewModel() {

    val ui: StateFlow<SettingsUiState> = tokenStore.authStateFlow
        .map { state -> state.toUi() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), SettingsUiState())

    private fun AuthState.toUi() = SettingsUiState(
        serverUrl    = serverUrl,
        appVersion   = BuildConfig.VERSION_NAME,
        buildFlavor  = if (BuildConfig.DEBUG) "debug" else "release",
    )

    companion object {
        fun factory(tokenStore: TokenStore) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(tokenStore) as T
            }
        }
    }
}

data class SettingsUiState(
    val serverUrl:   String? = null,
    val appVersion:  String  = "",
    val buildFlavor: String  = "",
)
