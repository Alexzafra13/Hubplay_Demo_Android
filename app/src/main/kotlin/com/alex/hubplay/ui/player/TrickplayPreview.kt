package com.alex.hubplay.ui.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.Closeable
import java.io.File

// ═══════════════════════════════════════════════════════════════════════════
//  Miniaturas al avanzar / retroceder (trickplay), como Jellyfin y Netflix.
//
//  La hoja del servidor tiene hasta 200 miniaturas de 320×180 (≈4800×2700 px):
//  decodificada entera serían ~50 MB, demasiado para un TV box. Se descarga a
//  un fichero de caché y BitmapRegionDecoder recorta SOLO la miniatura que
//  toca (unos 230 KB), con una caché pequeña para ir y volver sin redecodificar.
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Hoja de miniaturas; [thumb] decodifica una sola. [decodeRegion] es el
 * recorte real (BitmapRegionDecoder sobre el fichero); los tests de
 * capturas pasan uno sobre un Bitmap en memoria porque Robolectric no
 * implementa BitmapRegionDecoder (devuelve un bitmap negro).
 */
class TrickplaySprite internal constructor(
    private val decodeRegion: (Rect, BitmapFactory.Options) -> Bitmap?,
    private val release:      () -> Unit = {},
) : Closeable {

    private val cache = LruCache<Int, Bitmap>(CACHE_THUMBS)
    private val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }

    @Synchronized
    fun thumb(tile: SpriteTile): Bitmap? = cache.get(tile.index) ?: runCatching {
        decodeRegion(Rect(tile.left, tile.top, tile.left + tile.width, tile.top + tile.height), options)
    }.getOrNull()?.also { cache.put(tile.index, it) }

    @Synchronized
    override fun close() {
        cache.evictAll()
        release()
    }

    companion object {
        private const val CACHE_THUMBS = 12

        /** Descarga la hoja (con la sesión de [client]) y la abre; null si algo falla. */
        suspend fun open(client: OkHttpClient, url: String, dest: File): TrickplaySprite? = withContext(Dispatchers.IO) {
            runCatching {
                if (!dest.exists() || dest.length() == 0L) {
                    dest.parentFile?.mkdirs()
                    val tmp = File(dest.path + ".part")
                    client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                        check(resp.isSuccessful) { "HTTP ${resp.code}" }
                        tmp.outputStream().use { out -> resp.body!!.byteStream().copyTo(out) }
                    }
                    check(tmp.renameTo(dest)) { "rename" }
                }
                @Suppress("DEPRECATION") // newInstance(String) es API 31; minSdk 26
                val decoder = BitmapRegionDecoder.newInstance(dest.path, false)!!
                TrickplaySprite(decodeRegion = decoder::decodeRegion, release = decoder::recycle)
            }.getOrNull()
        }
    }
}

/** Hoja de miniaturas del item, descargada en segundo plano y cerrada al salir. */
@Composable
fun rememberTrickplaySprite(info: TrickplayInfo?, server: String?, client: OkHttpClient): TrickplaySprite? {
    val context = LocalContext.current
    val sprite by produceState<TrickplaySprite?>(initialValue = null, info, server) {
        value = null
        if (info == null || server.isNullOrBlank()) return@produceState
        val dest = File(context.cacheDir, "trickplay/${info.itemId}.png")
        value = TrickplaySprite.open(client, server.trimEnd('/') + info.spritePath, dest)
    }
    DisposableEffect(sprite) {
        val current = sprite
        onDispose { current?.close() }
    }
    return sprite
}

/** Cada salto del mando lo anota aquí; la miniatura se enseña un rato tras el último. */
@Stable
class ScrubState {
    var positionMs by mutableLongStateOf(0L)
        private set
    var tick by mutableIntStateOf(0)
        private set

    fun mark(targetMs: Long) {
        positionMs = targetMs
        tick++
    }
}

/**
 * Miniatura sobre la barra en la posición del salto, con la hora debajo.
 * Se ve [SHOW_MS] tras el último salto. Sin hoja (aún generándose, o el
 * servidor no tiene) no pinta nada.
 */
@Composable
fun BoxScope.TrickplayThumb(
    scrub:      ScrubState,
    trickplay:  TrickplayInfo?,
    sprite:     TrickplaySprite?,
    durationMs: () -> Long,
) {
    if (trickplay == null || sprite == null) return
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(scrub.tick) {
        if (scrub.tick == 0) return@LaunchedEffect
        visible = true
        delay(SHOW_MS)
        visible = false
    }
    if (!visible) return
    val tile = trickplay.tileAt(scrub.positionMs)
    val image by produceState<ImageBitmap?>(initialValue = null, tile.index, sprite) {
        value = withContext(Dispatchers.IO) { sprite.thumb(tile)?.asImageBitmap() }
    }
    val bitmap = image ?: return
    // Sin duración conocida no se sabe dónde va en la barra: mejor nada.
    val duration = durationMs().takeIf { it > 0 } ?: return
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val barWidth = maxWidth - CHROME_SIDE * 2
        val fraction = (scrub.positionMs.toFloat() / duration).coerceIn(0f, 1f)
        val x = (CHROME_SIDE + barWidth * fraction - THUMB_WIDTH / 2)
            .coerceIn(CHROME_SIDE, maxWidth - CHROME_SIDE - THUMB_WIDTH)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier
                .align(Alignment.BottomStart)
                .offset(x = x)
                .padding(bottom = THUMB_BOTTOM),
        ) {
            Box(
                modifier = Modifier
                    .size(THUMB_WIDTH, THUMB_WIDTH * trickplay.thumbHeight / trickplay.thumbWidth)
                    .clip(RoundedCornerShape(6.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(6.dp)),
            ) {
                Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Text(
                text       = formatTime(scrub.positionMs),
                color      = Color.White,
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.padding(top = 4.dp).height(20.dp),
            )
        }
    }
}

private const val SHOW_MS = 1_500L
private val THUMB_WIDTH = 224.dp

/** Margen lateral del chrome (CHROME_PADDING de VodPlayerChrome): la barra va de lado a lado menos esto. */
private val CHROME_SIDE = 48.dp

/** Por encima de la barra: botones + hora + barra ≈ 122 dp desde abajo. */
private val THUMB_BOTTOM = 128.dp
