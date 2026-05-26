package com.alex.hubplay.ui.series

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun HeroTrailerView(
    videoKey:      String,
    site:          String,
    modifier:      Modifier = Modifier,
    startAtSec:    Long = 0L,
    onReveal:      () -> Unit = {},
    onDismiss:     () -> Unit = {},
    onCurrentTime: ((Long) -> Unit)? = null,
) {
    if (site != "YouTube") return

    val lifecycleOwner = LocalLifecycleOwner.current
    var stage by remember(videoKey) { mutableStateOf(-1) }
    var webViewRef by remember(videoKey) { mutableStateOf<WebView?>(null) }
    var hasPlaybackTick by remember(videoKey) { mutableStateOf(false) }

    // Solo revelamos cuando el <video> real ha empezado a reproducir
    // (hasPlaybackTick = true). Si YouTube pinta error 153 / "no embebible",
    // nunca llega el tick → alpha se queda en 0 → el usuario solo ve el
    // backdrop estático, nunca el error.
    val alpha by animateFloatAsState(
        targetValue   = if (stage == 2 && hasPlaybackTick) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label         = "trailer-alpha",
    )

    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    LaunchedEffect(videoKey) {
        stage = -1
        val ok = withContext(Dispatchers.IO) { isEmbeddable(videoKey) }
        if (ok) stage = 0 else { stage = 3; onDismiss() }
    }

    LaunchedEffect(stage) {
        if (stage == 0) { delay(800); stage = 1 }
    }

    LaunchedEffect(stage) {
        if (stage == 1) {
            delay(12_000)
            if (stage == 1) { stage = 3; onDismiss() }
        }
    }

    // Watchdog: si tras 5s en stage 2 el <video> no ha hecho ningún tick,
    // YouTube pintó su pantalla de error (153, restricted, etc). Dismiss
    // silencioso — como alpha sigue en 0, el usuario nunca vio nada.
    LaunchedEffect(stage) {
        if (stage == 2) {
            delay(5_000)
            if (stage == 2 && !hasPlaybackTick) {
                stage = 3
                onDismiss()
            }
        }
    }

    // Cuando el primer tick llega, sabemos que el embed ESTÁ reproduciendo —
    // ahora sí avisamos al padre (onReveal suspende screensaver, etc).
    LaunchedEffect(hasPlaybackTick, stage) {
        if (hasPlaybackTick && stage == 2) {
            onReveal()
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

    if (stage !in 1..2) return

    Box(modifier = modifier.fillMaxSize().alpha(alpha)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { ctx ->
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
                            mainHandler.post {
                                if (stage == 2) { stage = 3; onDismiss() }
                            }
                        }
                        @JavascriptInterface
                        fun reportTime(seconds: Double) {
                            mainHandler.post { hasPlaybackTick = true }
                            onCurrentTime?.invoke(seconds.toLong())
                        }
                    }, "TrailerBridge")

                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (url?.contains("about:blank") == true) return
                            view?.postDelayed({
                                if (stage == 1) {
                                    // La página synthetic ya tiene el script
                                    // inline que subscribe al player y reporta
                                    // estados. No necesitamos inyectar nada más.
                                    // onReveal lo dispara el LaunchedEffect
                                    // cuando llegue el primer state=playing
                                    // del iframe (vía postMessage).
                                    stage = 2
                                    view.evaluateJavascript(JS_UNMUTE, null)
                                }
                            }, 800)
                        }

                        override fun onReceivedError(
                            view: WebView?, errorCode: Int,
                            description: String?, failingUrl: String?,
                        ) {
                            if (stage in 0..1) { stage = 3; onDismiss() }
                        }
                    }

                    // Cargamos un HTML synthetic propio que aloja el iframe
                    // de YouTube. baseUrl real (no synthetic) para que YouTube
                    // nos vea como un embed legítimo de terceros con Referer
                    // y Origin coherentes — sin esto, loadUrl directo es una
                    // navegación top-level y YouTube aplica restricciones
                    // estrictas (error 153 / player no arranca).
                    loadDataWithBaseURL(
                        TRAILER_BASE_URL,
                        buildIframeHtml(videoKey, startAtSec),
                        "text/html",
                        "UTF-8",
                        null,
                    )
                    webViewRef = this
                }
            },
            onRelease = { view ->
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
            "https%3A%2F%2Fyoutu.be%2F${videoKey}&format=json",
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

private const val TAG = "HeroTrailerView"

// baseUrl para loadDataWithBaseURL. Tiene que ser un dominio "real" (no
// "about:blank" ni "data:") para que YouTube nos trate como embed normal de
// terceros. No necesita resolver — solo importa que esté en los headers
// Referer/Origin que envía el WebView al cargar el iframe.
private const val TRAILER_BASE_URL = "https://hubplay.app"

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
    "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

// HTML synthetic que aloja el iframe de YouTube. Mimetiza el flujo de la web:
// iframe con origin matching el baseUrl + script que subscribe a eventos del
// player vía postMessage (YouTube IFrame Player API).
//
// reportTime(0) se llama cuando el player entra en state 1 (PLAYING) — es
// nuestra señal de "el video está reproduciendo DE VERDAD". Si YouTube
// rechaza el embed (error 153, dominio no permitido, etc), state 1 nunca
// llega y el watchdog dismissa silenciosamente.
private fun buildIframeHtml(videoKey: String, startAtSec: Long): String {
    val safe = videoKey.replace("\"", "").replace("<", "").replace(">", "")
    val startParam = if (startAtSec > 0) "&start=$startAtSec" else ""
    // Sin loop: queremos single-play en home. Cuando termina, YouTube dispara
    // state=0 (ENDED) → onEnded → dismiss → vuelve el backdrop estático.
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
              function parse(d){ try { return (typeof d==='string')?JSON.parse(d):d; } catch(e){ return null; } }
              function send(cmd){
                if (f.contentWindow) {
                  f.contentWindow.postMessage(JSON.stringify({event:'command',func:cmd,args:''}), '*');
                }
              }
              window.addEventListener('message', function(e){
                var d = parse(e.data); if (!d) return;
                // YouTube IFrame API postea {event:'onStateChange', info:N}
                // donde info es el playerState: -1 unstarted, 0 ended,
                // 1 playing, 2 paused, 3 buffering, 5 cued.
                var state = (d.event === 'onStateChange') ? d.info :
                            (d.info && typeof d.info.playerState !== 'undefined') ? d.info.playerState : null;
                if (state === 1) {
                  TrailerBridge.reportTime(0);
                  // Empezamos a sondear currentTime para que HomeViewModel
                  // pueda pasar &start=X a Detail al navegar.
                  if (!polling) polling = setInterval(function(){ send('getCurrentTime'); }, 2000);
                }
                if (state === 0) {
                  if (polling) { clearInterval(polling); polling = null; }
                  TrailerBridge.onEnded();
                }
                // Respuesta a getCurrentTime: YouTube manda
                // {event:'infoDelivery', info:<segundos>} o, en variantes,
                // {event:'infoDelivery', info:{currentTime:X}}.
                if (d.event === 'infoDelivery' && d.info != null) {
                  if (typeof d.info === 'number') {
                    TrailerBridge.reportTime(d.info);
                  } else if (typeof d.info.currentTime === 'number') {
                    TrailerBridge.reportTime(d.info.currentTime);
                  }
                }
              });
              f.addEventListener('load', function(){
                // Necesario para que YouTube empiece a emitir onStateChange
                // a este window. Sin este postMessage inicial, el player
                // reproduce pero NO emite eventos.
                f.contentWindow.postMessage('{"event":"listening"}', '*');
              });
            })();
          </script>
        </body></html>
    """.trimIndent()
}

// Helpers postMessage hacia el iframe — ya no podemos tocar el <video>
// directamente porque vive en otro origin (youtube-nocookie.com) dentro
// del iframe. La YouTube IFrame Player API expone estos comandos vía
// postMessage cuando enablejsapi=1.
private const val JS_PLAY = """
    (function() { var f = document.getElementById('yt'); if (f && f.contentWindow) f.contentWindow.postMessage('{"event":"command","func":"playVideo","args":""}', '*'); })();
"""
private const val JS_PAUSE = """
    (function() { var f = document.getElementById('yt'); if (f && f.contentWindow) f.contentWindow.postMessage('{"event":"command","func":"pauseVideo","args":""}', '*'); })();
"""
private const val JS_UNMUTE = """
    (function() {
      var f = document.getElementById('yt');
      if (f && f.contentWindow) {
        f.contentWindow.postMessage('{"event":"command","func":"unMute","args":""}', '*');
        f.contentWindow.postMessage('{"event":"command","func":"setVolume","args":[80]}', '*');
      }
    })();
"""
