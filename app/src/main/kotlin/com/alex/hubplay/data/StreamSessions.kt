package com.alex.hubplay.data

import android.util.Log
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.alex.hubplay.data.api.HubplayApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Libera en el servidor lo que el reproductor deja de usar, sin esperar a
 * los reapers por inactividad:
 *
 * - **VOD**: cada transcode/remux es un ffmpeg con un cupo por usuario
 *   (1 sin aceleración por hardware). El servidor lo mantenía 90 s tras
 *   salir del reproductor, y el siguiente episodio o la siguiente peli
 *   podía recibir 503 hasta entonces.
 * - **Directo**: cada canal en transmux es un ffmpeg más una conexión al
 *   proveedor IPTV (muchos solo permiten una o dos por cuenta). Con el id
 *   de viewer (`?v=`) el servidor sabe quién mira y corta al salir el
 *   último, en vez de a los 30 s.
 *
 * Todo es fire-and-forget en el scope de la app: el ViewModel que llama ya
 * se está destruyendo y un fallo solo significa que actúa el reaper.
 */
class StreamSessions(
    private val api:   HubplayApi,
    private val scope: CoroutineScope,
) {
    /** Para todas las sesiones del usuario sobre [itemId] (inocuo en direct play). */
    fun stopVod(itemId: String) {
        scope.launch {
            runCatching { api.stopStreamSession(itemId) }
                .onFailure { Log.d(TAG, "stop session $itemId: ${it.message}") }
        }
    }

    /**
     * Da de baja a [viewerId] del canal tras [delayMs]. El margen existe para
     * no matar una sesión que otro viewer de la app va a reutilizar enseguida
     * (salir del reproductor y que la vista previa vuelva a pedir el mismo
     * canal): si ese otro viewer se une antes, la baja solo le quita a este.
     */
    fun leaveChannel(channelId: String, viewerId: String, delayMs: Long = 0L) {
        scope.launch {
            if (delayMs > 0) delay(delayMs)
            runCatching { api.leaveChannelViewer(channelId, viewerId) }
                .onFailure { Log.d(TAG, "leave channel $channelId: ${it.message}") }
        }
    }

    companion object {
        private const val TAG = "StreamSessions"

        /** Margen al salir de un canal que la vista previa puede retomar. */
        const val LEAVE_GRACE_MS = 4_000L

        /** Id efímero de viewer: uno por reproductor montado. */
        fun newViewerId(): String = UUID.randomUUID().toString()

        /** URL del stream de un canal con el viewer registrado. */
        fun channelStreamPath(channelId: String, viewerId: String): String =
            "/api/v1/channels/$channelId/stream?v=$viewerId"
    }
}

/**
 * Acceso desde composables profundos (vista previa de canales en Inicio y
 * TV en directo) sin pasar el objeto por cada firma. Null en previews y
 * capturas: entonces no se avisa al servidor y actúa su reaper.
 */
val LocalStreamSessions: ProvidableCompositionLocal<StreamSessions?> =
    staticCompositionLocalOf { null }
