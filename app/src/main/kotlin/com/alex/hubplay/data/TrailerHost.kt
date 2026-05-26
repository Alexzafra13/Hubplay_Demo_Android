package com.alex.hubplay.data

import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/** Lo que una pantalla quiere reproducir como trailer de fondo. */
data class TrailerRequest(
    val itemId:   String,
    val videoKey: String,
    val site:     String,
)

/**
 * Singleton del trailer hero. Vive a nivel de Activity (provisto vía
 * [LocalTrailerHost]) para que el WebView de [TrailerHostOverlay] sobreviva
 * a la navegación entre Home / Detail / Series — el vídeo sigue sonando
 * mientras la UI alrededor cambia.
 *
 * **Modelo de claims por referencia**: cada pantalla que quiere un trailer
 * llama `activate(...)` al entrar y `deactivate(token)` al salir (vía
 * `DisposableEffect`). El claim más reciente gana. Cuando la lista de
 * claims queda vacía, esperamos 500ms antes de ocultar — eso absorbe el
 * gap durante la transición de navegación: la pantalla saliente libera su
 * claim DESPUÉS de que la entrante haya añadido el suyo, pero si el orden
 * se altera por un frame, la espera evita un parpadeo.
 *
 * Si el nuevo claim activo tiene la MISMA `videoKey` que el actual, no se
 * resetea nada — el WebView sigue reproduciendo el mismo vídeo, sólo cambia
 * el contexto. Si cambia, reseteo `revealed` y `currentTimeSec` para que la
 * próxima carga arranque limpia.
 */
class TrailerHost(private val scope: CoroutineScope) {
    private val claims = linkedMapOf<UUID, TrailerRequest>()
    private var hideJob: Job? = null

    private val _current = mutableStateOf<TrailerRequest?>(null)
    val current: State<TrailerRequest?> = _current

    private val _revealed = mutableStateOf(false)
    val revealed: State<Boolean> = _revealed

    private val _currentTimeSec = mutableStateOf(0L)
    val currentTimeSec: State<Long> = _currentTimeSec

    // Cache de `isEmbeddable` por videoKey. Sin esto, cada cambio de card
    // dispara un GET HTTPS a youtube.com/oembed (~300-800 ms en wifi
    // doméstica + CPU lento). Un trailer no se vuelve no-embeddable
    // entre foco y foco. Se mantiene en memoria toda la sesión.
    private val embeddabilityCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun getCachedEmbeddable(videoKey: String): Boolean? = embeddabilityCache[videoKey]
    fun cacheEmbeddable(videoKey: String, value: Boolean) { embeddabilityCache[videoKey] = value }

    /**
     * Activa un claim para este item/key/site. [startAtSec] permite sembrar
     * la posición de arranque cuando la key es NUEVA — útil para entrar a
     * Detail por deep link con `&trailerResume=120`. Si la key ya es la
     * activa (continuidad Home→Detail), startAtSec se ignora porque el
     * `WebView` no se recarga.
     */
    @Synchronized
    fun activate(itemId: String, videoKey: String, site: String, startAtSec: Long = 0L): UUID {
        val token = UUID.randomUUID()
        claims[token] = TrailerRequest(itemId, videoKey, site)
        recompute(seedTimeSec = startAtSec)
        return token
    }

    @Synchronized
    fun deactivate(token: UUID) {
        claims.remove(token)
        recompute(seedTimeSec = 0L)
    }

    /** Llamado desde el JS bridge cuando YouTube reporta state=PLAYING. */
    fun reportPlaying() { _revealed.value = true }

    /** Llamado cuando el vídeo termina o falla. Oculta el alpha pero NO
     *  toca el claim — la pantalla sigue activa, simplemente el trailer
     *  no se ve hasta que se mueva el foco a otro item (key distinta). */
    fun reportEnded() { _revealed.value = false }

    /** Llamado periódicamente con la posición del vídeo (en segundos). */
    fun reportTime(sec: Long) { _currentTimeSec.value = sec }

    /**
     * Limpia el host AHORA — sin esperar al debounce de 500ms. Usado por
     * HomeScreen cuando el usuario mueve el foco a una card sin trailer:
     * queremos que el WebView del trailer ANTERIOR desaparezca al instante
     * (audio incluido), no que se quede sonando 500ms mientras vemos otra
     * card. NO toca `claims` — si una pantalla activa un claim después,
     * el host vuelve a estado normal.
     */
    @Synchronized
    fun hideNow() {
        hideJob?.cancel()
        hideJob = null
        _current.value = null
        _revealed.value = false
        _currentTimeSec.value = 0L
    }

    private fun recompute(seedTimeSec: Long) {
        val newCurrent = claims.values.lastOrNull()
        val prev = _current.value

        if (newCurrent != null) {
            hideJob?.cancel()
            hideJob = null
            if (prev?.videoKey != newCurrent.videoKey) {
                // Key nueva: limpiamos estado de reveal/posición. El
                // seedTimeSec del caller (Detail con resume, normal=0)
                // sobreescribe el reset por defecto.
                _revealed.value = false
                _currentTimeSec.value = seedTimeSec
            }
            _current.value = newCurrent
        } else {
            hideJob?.cancel()
            hideJob = scope.launch {
                delay(HIDE_DEBOUNCE_MS)
                _current.value = null
                _revealed.value = false
                _currentTimeSec.value = 0L
            }
        }
    }

    companion object {
        private const val HIDE_DEBOUNCE_MS = 500L
    }
}

val LocalTrailerHost = compositionLocalOf<TrailerHost> {
    error("TrailerHost not provided")
}
