package com.alex.hubplay.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Sincroniza el intro de marca con el splash del sistema.
 *
 * El splash de la plataforma (SplashScreen API, o su backport en
 * API < 31) cubre la ventana hasta que la Activity dibuja su primer
 * frame; si el intro Compose arrancara nada más componerse, correría
 * entero DEBAJO de esa capa y el usuario solo vería el icono estático.
 * `MainActivity` abre la compuerta desde `setOnExitAnimationListener`
 * (el momento exacto en que el sistema retira su splash) y el intro
 * espera a ella, con un tope por si algún OEM no dispara el listener.
 */
object BrandIntroGate {
    private val _opened = MutableStateFlow(false)
    val opened: StateFlow<Boolean> = _opened

    fun open() {
        _opened.value = true
    }
}
