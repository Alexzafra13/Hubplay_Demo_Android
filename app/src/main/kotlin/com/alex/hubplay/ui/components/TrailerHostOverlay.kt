package com.alex.hubplay.ui.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.alex.hubplay.data.LocalTrailerHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Singleton `WebView` montado al nivel raíz de la app, dirigido por
 * [LocalTrailerHost]. Cuando `current.videoKey` cambia, se hace `loadDataWithBaseURL`
 * con el iframe nuevo. Cuando la `key` se mantiene a través de la navegación
 * (Home → Detail del mismo item), el `WebView` NO se recarga y el vídeo sigue
 * sin interrupción — esto es la diferencia con tener un `HeroTrailerView` por
 * pantalla, que se desmontaba en cada nav y forzaba un re-load.
 *
 * Z-order: este overlay se renderiza DETRÁS del contenido de las pantallas
 * (es la primera capa del Box raíz en `HubplayApp`). Cada pantalla hace su
 * backdrop transparente cuando el trailer activo es para SU item, dejando
 * pasar el vídeo. Los gradientes y contenido de la pantalla sí siguen
 * encima del trailer.
 *
 * Lifecycle: pause/resume del Activity → pause/resume del player vía postMessage.
 */
@Composable
fun TrailerHostOverlay(modifier: Modifier = Modifier) {
    val host           = LocalTrailerHost.current
    val current        = host.current.value
    val revealed       = host.revealed.value
    val fadeOutOnHide  = host.fadeOutOnHide.value
    val lifecycleOwner = LocalLifecycleOwner.current

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var embeddable by remember(current?.videoKey) { mutableStateOf<Boolean?>(null) }

    val webViewWanted = rememberWebViewWanted(trailerRequested = current != null)

    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val alpha by animateFloatAsState(
        targetValue   = if (revealed) 1f else 0f,
        // Al ocultar por cambio de card/navegación, snap: el backdrop de la
        // pantalla vuelve opaco al instante (ver trailerBackdropAlphaSpec),
        // así que desvanecer la WebView debajo era pintar una capa 1920×1080
        // durante 24 frames sin que se viera. Si el tráiler ACABA por sí
        // solo, fundido: el vídeo sigue pintando su último segundo mientras
        // el backdrop vuelve por encima, y el usuario ve un crossfade limpio
        // en vez de un corte a la carátula.
        animationSpec = trailerOverlayAlphaSpec(revealed, fadeOutOnHide),
        label         = "trailer-host-alpha",
    )

    // Pre-flight oEmbed por cada nuevo videoKey. Consultamos primero el cache
    // del host — un trailer no se vuelve no-embeddable entre foco y foco, y
    // ese GET a youtube.com/oembed cuesta 300-800ms en wifi doméstica. Solo
    // pagamos el round-trip la PRIMERA vez por key durante la sesión.
    LaunchedEffect(current?.videoKey, current?.site) {
        val key = current?.videoKey
        if (key == null || current.site != "YouTube") {
            embeddable = null
            return@LaunchedEffect
        }
        val cached = host.getCachedEmbeddable(key)
        if (cached != null) {
            embeddable = cached
            return@LaunchedEffect
        }
        val ok = withContext(Dispatchers.IO) { isEmbeddable(key) }
        host.cacheEmbeddable(key, ok)
        embeddable = ok
    }

    // Driver principal: cuando `current?.videoKey` cambia, decidimos qué
    // hacer con el WebView. Si la key se mantiene (continuidad Home→Detail),
    // Compose NO re-ejecuta este LaunchedEffect → el WebView no se entera de
    // nada y el vídeo sigue. Si va a null (hide), limpiamos a about:blank
    // para liberar recursos. Si cambia a otra key (o vuelve tras hide),
    // cargamos el iframe nuevo.
    LaunchedEffect(current?.videoKey, embeddable, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        val key = current?.videoKey
        if (key == null) {
            // Orden importa: PRIMERO postMessage pauseVideo al iframe (corta
            // audio en ~10ms vía YouTube IFrame API). LUEGO loadUrl about:blank
            // (destruye el iframe entero pero tarda 50-100ms). Sin el pause
            // previo el audio sigue sonando ese medio segundo de transición.
            wv.evaluateJavascript(JS_PAUSE, null)
            wv.stopLoading()
            wv.loadUrl("about:blank")
            return@LaunchedEffect
        }
        if (current.site != "YouTube") return@LaunchedEffect
        if (embeddable != true) return@LaunchedEffect

        wv.loadDataWithBaseURL(
            TRAILER_BASE_URL,
            buildIframeHtml(key, host.currentTimeSec.value),
            "text/html", "UTF-8", null,
        )
    }

    // Watchdog: si tras 6s de carga el host sigue sin revelar (ningún
    // reportTime → reportPlaying), asumimos fallo silencioso (YouTube pintó
    // su overlay de error) y avisamos al host. Se lee `host.revealed`, que
    // el propio host resetea al cambiar de key, y NO un `remember(key)`
    // local: el bridge JS se crea una sola vez en `factory` y capturaba el
    // MutableState de la PRIMERA composición, así que a partir del segundo
    // tráiler el flag nuevo nunca se ponía a true y este watchdog ocultaba
    // el tráiler a los 6 s de cargar (el "backdrop vuelve y luego otra vez
    // el tráiler" que se veía ~2 s después de arrancar).
    LaunchedEffect(current?.videoKey) {
        val key = current?.videoKey ?: return@LaunchedEffect
        delay(WATCHDOG_MS)
        if (!host.revealed.value && current.videoKey == key) {
            Log.d(TAG, "trailer watchdog: no playback after ${WATCHDOG_MS}ms for $key")
            host.reportEnded()
        }
    }

    DisposableEffect(lifecycleOwner, webViewRef) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            val web = webViewRef ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> web.evaluateJavascript(JS_PLAY, null)
                Lifecycle.Event.ON_PAUSE  -> web.evaluateJavascript(JS_PAUSE, null)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Box(modifier = modifier.fillMaxSize().alpha(alpha)) {
        if (!webViewWanted) return@Box
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                @SuppressLint("SetJavaScriptEnabled")
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                    isFocusable = false
                    isFocusableInTouchMode = false

                    settings.apply {
                        javaScriptEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        domStorageEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = false
                        userAgentString = DESKTOP_USER_AGENT
                        @SuppressLint("SetAllowMixedContent")
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    // Sin capa hardware: con LAYER_TYPE_HARDWARE cada frame de
                    // vídeo se copiaba a una textura 1920×1080 y luego se
                    // componía (5-20 ms/frame en la GPU del TV box). El
                    // functor del WebView dibuja directo en el frame; el alpha
                    // del revelado lo gestiona HWUI con su propia capa solo
                    // mientras dura el fundido.
                    setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                    setInitialScale(100)

                    android.webkit.CookieManager.getInstance()
                        .setAcceptThirdPartyCookies(this, true)

                    val bridge = object {
                        @JavascriptInterface
                        fun onEnded(graceful: Boolean) {
                            Log.d(TAG, "trailer ended (graceful=$graceful)")
                            mainHandler.post { host.reportEnded(graceful) }
                        }

                        @JavascriptInterface
                        fun reportTime(seconds: Double) {
                            mainHandler.post {
                                host.reportPlaying()
                                host.reportTime(seconds.toLong())
                            }
                        }
                    }
                    addJavascriptInterface(bridge, "TrailerBridge")

                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView?, errorCode: Int,
                            description: String?, failingUrl: String?,
                        ) {
                            mainHandler.post { host.reportEnded() }
                        }
                    }

                    webViewRef = this
                }
            },
            onRelease = { view ->
                // El overlay normalmente NUNCA sale de composición durante la
                // vida de la app (vive en el root). Esto solo dispara si
                // HubplayApp completo se recompone (cambio de Activity, logout
                // que reinicia el árbol, etc).
                runCatching {
                    view.removeJavascriptInterface("TrailerBridge")
                    view.loadUrl("about:blank")
                    view.stopLoading()
                    view.destroy()
                }
                webViewRef = null
            },
        )
    }
}

/**
 * El WebView se crea DESPUÉS del arranque: instanciar Chromium cuesta
 * ~0,6 s de hilo principal en la Mi TV y estaba en el camino crítico del
 * primer frame. Se monta al primer tráiler o, si no lo hay, pasado
 * [WEBVIEW_WARMUP_MS] (con el intro ya terminado), lo que llegue antes.
 * Una vez a true no vuelve a false: el WebView vive toda la sesión.
 */
@Composable
private fun rememberWebViewWanted(trailerRequested: Boolean): Boolean {
    var wanted by remember { mutableStateOf(false) }
    LaunchedEffect(trailerRequested) { if (trailerRequested) wanted = true }
    LaunchedEffect(Unit) {
        delay(WEBVIEW_WARMUP_MS)
        wanted = true
    }
    return wanted
}

private suspend fun isEmbeddable(videoKey: String): Boolean {
    return try {
        val url = URL(
            "https://www.youtube.com/oembed?url=" +
            "https%3A%2F%2Fyoutu.be%2F$videoKey&format=json",
        )
        (url.openConnection() as HttpURLConnection).run {
            connectTimeout = 4_000
            readTimeout    = 4_000
            requestMethod  = "GET"
            try { responseCode in 200..299 } finally { disconnect() }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "oEmbed pre-flight failed for $videoKey: ${t.message}")
        false
    }
}

/**
 * Animación del alpha del backdrop de una pantalla con tráiler (Home /
 * Detail / Series), simétrica a la del overlay:
 *  - Revelando el tráiler (`revealed` = true): fundido de 700 ms — al
 *    usuario le gusta ver cómo el backdrop deja paso al vídeo.
 *  - El tráiler terminó solo (`fadeOutOnHide`): fundido de vuelta, el
 *    vídeo aún se ve debajo mientras el backdrop reaparece.
 *  - Cualquier otro ocultado (cambio de card, navegación, error): snap a
 *    opaco. La WebView de debajo puede estar pintando la end-screen de
 *    YouTube o el tráiler anterior; animar aquí lo dejaría ver.
 */
fun trailerBackdropAlphaSpec(revealed: Boolean, fadeOutOnHide: Boolean): AnimationSpec<Float> = when {
    revealed      -> tween(durationMillis = BACKDROP_FADE_MS)
    fadeOutOnHide -> tween(durationMillis = ENDED_FADE_MS)
    else          -> snap()
}

/** Alpha del WebView: misma lógica que [trailerBackdropAlphaSpec], en espejo. */
private fun trailerOverlayAlphaSpec(revealed: Boolean, fadeOutOnHide: Boolean): AnimationSpec<Float> = when {
    revealed      -> tween(durationMillis = REVEAL_FADE_MS)
    fadeOutOnHide -> tween(durationMillis = ENDED_FADE_MS)
    else          -> snap()
}

private const val TAG = "TrailerHostOverlay"

/** Tiempo máximo de carga sin progreso antes de dar el tráiler por fallido. */
private const val WATCHDOG_MS = 6_000L

/** Sin tráiler pedido, el WebView se crea a este tiempo del arranque (intro y primer Inicio ya pintados). */
private const val WEBVIEW_WARMUP_MS = 5_000L

/** Fundido de entrada del WebView al revelar el tráiler. */
private const val REVEAL_FADE_MS = 400

/** Fundido del backdrop de las pantallas al revelar el tráiler. */
private const val BACKDROP_FADE_MS = 700

/** Crossfade de vuelta al backdrop cuando el tráiler termina solo. */
private const val ENDED_FADE_MS = 700
private const val TRAILER_BASE_URL = "https://hubplay.app"
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
    "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

private fun buildIframeHtml(videoKey: String, startAtSec: Long): String {
    val safe = videoKey.replace("\"", "").replace("<", "").replace(">", "")
    val startParam = if (startAtSec > 0) "&start=$startAtSec" else ""
    val src =
        "https://www.youtube-nocookie.com/embed/$safe" +
        "?autoplay=1&mute=1&controls=0" +
        "&modestbranding=1&playsinline=1&rel=0&iv_load_policy=3" +
        "&disablekb=1&showinfo=0&enablejsapi=1" +
        "&origin=$TRAILER_BASE_URL$startParam"
    return """
        <!DOCTYPE html>
        <html><head><meta name="viewport" content="width=1920">
        <style>
          html,body{margin:0;padding:0;background:#000;overflow:hidden;height:100%;width:100%}
          .wrap{position:absolute;top:0;left:0;right:0;bottom:0;overflow:hidden}
          iframe{position:absolute;top:-4%;left:-4%;width:108%;height:108%;border:0;pointer-events:none}
        </style>
        </head><body>
          <div class="wrap">
            <iframe id="yt" src="$src"
              allow="autoplay; encrypted-media; picture-in-picture"
              referrerpolicy="strict-origin-when-cross-origin"
              allowfullscreen></iframe>
          </div>
          <script>
            (function(){
              var f = document.getElementById('yt');
              // Protocolo real del IFrame API (verificado en la Mi TV con
              // logcat, 2026-09-12): tras `listening`, YouTube manda
              // `infoDelivery` cada ~270 ms con `info.currentTime` y la
              // duración en `info.progressState.duration`; los cambios de
              // estado llegan como `infoDelivery` con `info.playerState`
              // (y ahí sí `info.duration`). No hay evento `onStateChange`
              // separado ni respuestas numéricas a getCurrentTime/getDuration.
              //
              // Fin del tráiler: fundido ANTICIPADO a END_LEAD_SEC del final
              // (graceful: el vídeo sigue pintando su último segundo mientras
              // el backdrop vuelve). Redes de seguridad: playerState=0 (ENDED)
              // y atasco por reloj de pared. Tras `ended` se ignora TODO:
              // el mensaje de ENDED trae currentTime=duration y antes volvía
              // a revelar el WebView justo con la end-screen de YouTube
              // (el "frame con el botón de replay").
              var END_LEAD_SEC = 1.5;
              var STALL_MS     = 6000;
              var duration      = 0;
              var lastTime      = -1;
              var lastAdvanceAt = 0;
              var ended         = false;
              var primed        = false;
              var lastState     = -1;
              function parse(d){ try { return (typeof d==='string')?JSON.parse(d):d; } catch(e){ return null; } }
              function send(cmd, args){
                if (f.contentWindow) {
                  f.contentWindow.postMessage(JSON.stringify({event:'command',func:cmd,args:args||''}), '*');
                }
              }
              function fireEnded(graceful){
                if (ended) return;
                ended = true;
                TrailerBridge.onEnded(!!graceful);
              }
              window.addEventListener('message', function(e){
                if (ended) return;
                var d = parse(e.data); if (!d || d.event !== 'infoDelivery' || !d.info) return;
                var info  = d.info;
                var state = (typeof info.playerState === 'number') ? info.playerState : null;
                if (state !== null) lastState = state;
                if (state === 0) { fireEnded(false); return; }
                if (state === 1 && !primed) {
                  primed = true;
                  // Audio: el iframe arranca muted (mute=1 obligatorio para
                  // autoplay en WebView). Al primer PLAYING, unmute + 80 %.
                  send('unMute');
                  send('setVolume', [80]);
                  // Sin subtítulos automáticos: el trailer es fondo
                  // ambiental. unloadModule es la única vía fiable
                  // (cc_load_policy solo sabe forzarlos a ON).
                  send('unloadModule', ['captions']);
                  send('unloadModule', ['cc']);
                }
                if (typeof info.duration === 'number' && info.duration > 0) {
                  duration = info.duration;
                } else if (info.progressState && info.progressState.duration > 0) {
                  duration = info.progressState.duration;
                }
                if (typeof info.currentTime !== 'number') return;
                var t = info.currentTime;
                if (duration > 0 && t >= duration - END_LEAD_SEC) { fireEnded(true); return; }
                // GATE del reveal: solo reportTime con currentTime>0.1. Con
                // PLAYING el primer frame aún no está pintado (decode+GPU en
                // el TV box); esperar progreso real garantiza que la
                // transición backdrop→trailer descubre vídeo, no negro.
                if (t > 0.1) TrailerBridge.reportTime(t);
                var now = Date.now();
                if (t > lastTime + 0.05) {
                  lastTime = t;
                  lastAdvanceAt = now;
                } else if (lastState === 1 && t > 5 && lastAdvanceAt > 0 && now - lastAdvanceAt > STALL_MS) {
                  fireEnded(false);
                }
              });
              f.addEventListener('load', function(){
                f.contentWindow.postMessage('{"event":"listening"}', '*');
              });
            })();
          </script>
        </body></html>
    """.trimIndent()
}

private const val JS_PLAY = """
    (function() { var f = document.getElementById('yt'); if (f && f.contentWindow) f.contentWindow.postMessage('{"event":"command","func":"playVideo","args":""}', '*'); })();
"""
private const val JS_PAUSE = """
    (function() { var f = document.getElementById('yt'); if (f && f.contentWindow) f.contentWindow.postMessage('{"event":"command","func":"pauseVideo","args":""}', '*'); })();
"""
