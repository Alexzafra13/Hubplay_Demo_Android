package com.alex.hubplay.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Lo que una pantalla dentro de [TvShell] puede pedirle al armazón. Hoy:
 * ocultar el menú lateral mientras la ficha está en vista previa del
 * tráiler (5 s sin tocar el mando), para que el vídeo quede limpio.
 */
@Stable
class ShellChrome {
    var hideSidebar by mutableStateOf(false)
}

/** null fuera de [TvShell] (tests, pantallas sin armazón). */
val LocalShellChrome = staticCompositionLocalOf<ShellChrome?> { null }

/** Oculta el menú lateral de [TvShell] mientras [hidden]; lo devuelve al salir. */
@Composable
fun HideShellSidebar(hidden: Boolean) {
    val chrome = LocalShellChrome.current
    DisposableEffect(chrome, hidden) {
        chrome?.hideSidebar = hidden
        onDispose { chrome?.hideSidebar = false }
    }
}
