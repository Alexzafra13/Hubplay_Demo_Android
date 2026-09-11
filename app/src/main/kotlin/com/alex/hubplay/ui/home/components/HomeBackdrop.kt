package com.alex.hubplay.ui.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.alex.hubplay.ui.theme.BgBase

/** Duración del fundido entre backdrops al cambiar de item. */
private const val BACKDROP_FADE_MS = 220

/**
 * Backdrop a pantalla completa de Inicio con fundido entre items y alpha
 * global (revelado del tráiler), todo SIN capas fuera de pantalla.
 *
 * Antes era `Crossfade` + `Modifier.alpha`. Cada uno crea un graphicsLayer
 * de 1920×1080 que se renderiza aparte y se compone después; durante el
 * fundido eran dos capas (entrante y saliente) más la del alpha. En el
 * GPU de un TV box (Mali) eso son 15-30ms por frame: la mitad del tirón
 * que se notaba al mover el foco por los rails.
 *
 * Ahora el fundido lo hace Coil: `crossfade` + `placeholderMemoryCacheKey`
 * con la URL anterior. El backdrop saliente sale de la caché de memoria
 * como placeholder y `CrossfadePainter` mezcla ambos painters con alpha
 * de pintura, sin capas. El alpha global va en `drawRect` (fondo) y en un
 * graphicsLayer con [CompositingStrategy.ModulateAlpha] (imagen), que
 * modula el alpha por operación de dibujo en vez de abrir un buffer.
 *
 * `alpha` es una lambda para leerse solo en la fase de dibujo: los 700ms
 * del revelado del tráiler no recomponen nada.
 */
@Composable
fun HomeBackdrop(
    url: String?,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Última URL pintada, solo para sembrar el placeholder del fundido.
    // A propósito NO es estado de Compose: no debe recomponer nada.
    val lastUrl = remember { arrayOfNulls<String>(1) }
    val request = remember(url) {
        val previous = lastUrl[0]
        lastUrl[0] = url
        url?.let { current ->
            ImageRequest.Builder(context)
                .data(current)
                .crossfade(BACKDROP_FADE_MS)
                .apply {
                    if (previous != null) placeholderMemoryCacheKey(MemoryCache.Key(previous))
                }
                .build()
        }
    }

    // Con la imagen cargada y opaca, el rectángulo de fondo es overdraw
    // puro (una pasada a pantalla completa menos por frame). Solo se pinta
    // mientras no hay imagen o mientras el tráiler está revelándose.
    var loaded by remember(request) { mutableStateOf(false) }

    Box(
        modifier = modifier.drawBehind {
            val a = alpha()
            if (!loaded || a < 1f) drawRect(BgBase, alpha = a)
        },
    ) {
        if (request != null) {
            AsyncImage(
                model              = request,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                onSuccess          = { loaded = true },
                modifier           = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.alpha = alpha()
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    },
            )
        }
    }
}
