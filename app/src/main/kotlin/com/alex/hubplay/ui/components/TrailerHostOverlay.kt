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
import androidx.compose.animation.core.animateFloatAsState
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
    val lifecycleOwner = LocalLifecycleOwner.current

    // hasTickedForCurrentKey: se resetea al cambiar `current?.videoKey` gracias
    // al key del remember. El watchdog lo lee para detectar si el player no
    // arrancó (vídeo restringido, error 153) tras 6s.
    var hasTickedForCurrentKey by remember(current?.videoKey) { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var embeddable by remember(current?.videoKey) { mutableStateOf<Boolean?>(null) }

    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val alpha by animateFloatAsState(
        targetValue   = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
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

    // Watchdog: si tras 6s de carga seguimos sin tick, asumimos fallo
    // silencioso (YouTube pintó error overlay) y avisamos al host.
    LaunchedEffect(current?.videoKey) {
        val key = current?.videoKey ?: return@LaunchedEffect
        delay(6_000)
        if (!hasTickedForCurrentKey && current.videoKey == key) {
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
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    setInitialScale(100)

                    android.webkit.CookieManager.getInstance()
                        .setAcceptThirdPartyCookies(this, true)

                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onEnded() {
                            mainHandler.post { host.reportEnded() }
                        }
                        @JavascriptInterface
                        fun reportTime(seconds: Double) {
                            mainHandler.post {
                                hasTickedForCurrentKey = true
                                host.reportPlaying()
                                host.reportTime(seconds.toLong())
                            }
                        }
                    }, "TrailerBridge")

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

private const val TAG = "TrailerHostOverlay"
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
              var polling = null;
              // Estado para detección fiable del fin del trailer. YouTube
              // no siempre dispara state=0 (ENDED); a veces va
              // PLAYING → PAUSED → silencio y se queda en la end-screen
              // con sugerencias (la "pantalla gris con play" que el usuario
              // ve si no la cazamos a tiempo).
              var lastTime   = -1;
              var stallCount = 0;
              var duration   = 0;
              var ended      = false;
              function parse(d){ try { return (typeof d==='string')?JSON.parse(d):d; } catch(e){ return null; } }
              function send(cmd){
                if (f.contentWindow) {
                  f.contentWindow.postMessage(JSON.stringify({event:'command',func:cmd,args:''}), '*');
                }
              }
              function fireEnded(){
                if (ended) return;
                ended = true;
                if (polling) { clearInterval(polling); polling = null; }
                TrailerBridge.onEnded();
              }
              window.addEventListener('message', function(e){
                var d = parse(e.data); if (!d) return;
                var state = (d.event === 'onStateChange') ? d.info :
                            (d.info && typeof d.info.playerState !== 'undefined') ? d.info.playerState : null;
                if (state === 1) {
                  // Si ya disparamos fireEnded (fade-out anticipado, stall
                  // o state=0), NO re-arrancamos el ciclo. Sin esto, tras
                  // fade-out anticipado YouTube seguía emitiendo state=1
                  // → polling restart → reportTime → reveal otra vez →
                  // backdrop flickea.
                  if (ended) return;
                  // Audio: iframe arranca muted (mute=1 obligatorio para
                  // autoplay en WebView). Al entrar PLAYING, unmute +
                  // volumen 80% como hace YouTube TV.
                  if (f.contentWindow) {
                    f.contentWindow.postMessage('{"event":"command","func":"unMute","args":""}', '*');
                    f.contentWindow.postMessage('{"event":"command","func":"setVolume","args":[80]}', '*');
                  }
                  if (duration === 0) send('getDuration');
                  if (!polling) polling = setInterval(function(){ send('getCurrentTime'); }, 300);
                }
                if (state === 0) {
                  // SOLO state=0 (ENDED) como señal directa. Antes incluía
                  // state=2 (PAUSED), pero YouTube dispara PAUSED brevemente
                  // mid-play (buffer interno, cambio de calidad) y producía
                  // un flicker de ~1s del backdrop a los 2-3s de empezar.
                  // El fade-out anticipado por duration y el stall detection
                  // siguen siendo las redes de seguridad.
                  fireEnded();
                }
                if (d.event === 'infoDelivery' && d.info != null) {
                  var info = d.info;
                  if (typeof info === 'number') {
                    if (duration === 0 && info > 30) {
                      duration = info;
                    } else {
                      // GATE del reveal: solo reportTime cuando currentTime>0.1.
                      // Antes de eso, state=1 ya disparó PERO el frame aún
                      // no se ha pintado (decode+GPU pending en Mi Box S).
                      // Esperar a que YouTube reporte progreso real garantiza
                      // que la transición backdrop→trailer descubre video,
                      // no una WebView negra.
                      if (info > 0.1) {
                        TrailerBridge.reportTime(info);
                      }
                      if (duration > 0 && info >= duration - 1.5) {
                        fireEnded();
                        return;
                      }
                      if (info > 5 && info <= lastTime + 0.1) {
                        stallCount++;
                        if (stallCount >= 3) { fireEnded(); return; }
                      } else {
                        stallCount = 0;
                      }
                      lastTime = info;
                    }
                  } else if (typeof info.currentTime === 'number') {
                    if (info.currentTime > 0.1) {
                      TrailerBridge.reportTime(info.currentTime);
                    }
                    if (typeof info.duration === 'number' && info.duration > 0) {
                      duration = info.duration;
                    }
                  }
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
