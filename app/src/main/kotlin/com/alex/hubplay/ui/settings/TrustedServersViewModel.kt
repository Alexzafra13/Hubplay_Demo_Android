package com.alex.hubplay.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.CertPinStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the "Servidores de confianza" subscreen in Settings. Reads the
 * pin store as a flow so deletes update the list in place, and exposes
 * a [forget] action that wipes a single host from disk.
 *
 * Sorting choice: most-recently-added first. The user almost always
 * wants to act on the last cert they pinned (a recent renew or a fresh
 * server they're debugging), so newer-on-top matches intent.
 */
class TrustedServersViewModel(
    private val pinStore: CertPinStore,
) : ViewModel() {

    val entries: StateFlow<List<CertPinStore.Pin>> = pinStore.pins
        .map { pins -> pins.values.sortedByDescending { it.addedAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun forget(host: String) {
        viewModelScope.launch { pinStore.delete(host) }
    }

    companion object {
        fun factory(pinStore: CertPinStore) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TrustedServersViewModel(pinStore) as T
        }
    }
}
