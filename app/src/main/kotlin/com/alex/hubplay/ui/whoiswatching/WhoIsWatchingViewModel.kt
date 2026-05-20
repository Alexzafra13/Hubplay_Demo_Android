package com.alex.hubplay.ui.whoiswatching

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.Profile
import com.alex.hubplay.data.ProfileListResult
import com.alex.hubplay.data.ProfileRepository
import com.alex.hubplay.data.SwitchResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the "Who's watching?" picker.
 *
 * Lifecycle:
 *   1. On construction, [load] fetches `/me/profiles`.
 *   2. If the tree has ≤ 1 profile, we pin the current user as active
 *      and emit [Effect.SkipToHome] — the screen never visibly renders.
 *      Mirror of the web's `useEffect(() => navigate("/", { replace }))`.
 *   3. If there are multiple, [select] either:
 *      - opens the PIN dialog (when `hasPin`), or
 *      - calls `/auth/switch-profile` directly (PIN-less profiles).
 *   4. On success → [Effect.NavigateHome]. Token rotation is handled
 *      inside [ProfileRepository] — the NavGraph just consumes the
 *      effect and pops back to Home.
 */
class WhoIsWatchingViewModel(
    private val profileRepo: ProfileRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(WhoIsWatchingUi(isLoading = true))
    val ui: StateFlow<WhoIsWatchingUi> = _ui.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, error = null) }
            when (val result = profileRepo.list()) {
                is ProfileListResult.Unauthorized -> {
                    // Bearer is dead and the refresh chain already failed
                    // (interceptor wiped tokens). Retry would just loop
                    // on 401 — kick the user back to Login instead.
                    _effects.send(Effect.SignOut)
                }
                is ProfileListResult.Failed -> {
                    // Transient (network / 5xx). Surface a Retry + an
                    // explicit Sign-out escape so the user is never
                    // trapped on this screen.
                    _ui.update {
                        it.copy(isLoading = false, profiles = emptyList(), error = result.message)
                    }
                }
                is ProfileListResult.Ok -> {
                    val profiles = result.profiles
                    when {
                        profiles.isEmpty() -> {
                            // Solo deploy with no profile rows. The current
                            // bearer is already the only identity available.
                            _effects.send(Effect.SkipToHome)
                        }
                        profiles.size == 1 -> {
                            val solo = profiles.single()
                            profileRepo.pinCurrentAsActive(solo.id, solo.displayName)
                            _effects.send(Effect.SkipToHome)
                        }
                        else -> {
                            _ui.update {
                                it.copy(
                                    isLoading      = false,
                                    profiles       = profiles,
                                    error          = null,
                                    pendingProfile = null,
                                    pinError       = false,
                                    switching      = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** Manual escape hatch from the error state. */
    fun signOut() {
        viewModelScope.launch { _effects.send(Effect.SignOut) }
    }

    fun select(profile: Profile) {
        if (profile.hasPin) {
            _ui.update { it.copy(pendingProfile = profile, pinError = false) }
        } else {
            doSwitch(profile, pin = null)
        }
    }

    /** Called from the PIN dialog. Empty / wrong PIN keeps the dialog open. */
    fun submitPin(pin: String) {
        val target = _ui.value.pendingProfile ?: return
        doSwitch(target, pin = pin)
    }

    fun dismissPinDialog() {
        _ui.update { it.copy(pendingProfile = null, pinError = false) }
    }

    private fun doSwitch(profile: Profile, pin: String?) {
        viewModelScope.launch {
            _ui.update { it.copy(switching = true) }
            when (val result = profileRepo.switch(profile.id, pin, profile.displayName)) {
                is SwitchResult.Success -> {
                    _ui.update { it.copy(switching = false, pendingProfile = null) }
                    _effects.send(Effect.NavigateHome)
                }
                SwitchResult.InvalidPin -> {
                    _ui.update { it.copy(switching = false, pinError = true) }
                }
                SwitchResult.NotAllowed -> {
                    _ui.update {
                        it.copy(switching = false, pendingProfile = null, error = result.toErrorMessage())
                    }
                }
                is SwitchResult.Failure -> {
                    _ui.update {
                        it.copy(switching = false, pendingProfile = null, error = result.toErrorMessage())
                    }
                }
            }
        }
    }

    private fun SwitchResult.toErrorMessage(): String = when (this) {
        is SwitchResult.Failure -> message
        SwitchResult.NotAllowed -> "not allowed"
        else -> "unknown"
    }

    companion object {
        fun factory(profileRepo: ProfileRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                WhoIsWatchingViewModel(profileRepo) as T
        }
    }
}

@Immutable
data class WhoIsWatchingUi(
    val isLoading:      Boolean        = false,
    val profiles:       List<Profile>  = emptyList(),
    val error:          String?        = null,
    val pendingProfile: Profile?       = null,
    val pinError:       Boolean        = false,
    val switching:      Boolean        = false,
)

sealed class Effect {
    /** Picker not needed (≤ 1 profile) — go straight to Home. */
    data object SkipToHome : Effect()

    /** User picked a profile and the token swap succeeded. */
    data object NavigateHome : Effect()

    /**
     * Bearer is dead (auto-detected 401 with no refresh path), or the
     * user hit the "Sign out" escape on the error state. Caller wipes
     * tokens and bounces to Login.
     */
    data object SignOut : Effect()
}
