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
    videoKey: String,
    site:     String,
    modifier: Modifier = Modifier,
    onReveal: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    if (site != "YouTube") return

    val lifecycleOwner = LocalLifecycleOwner.current
    var stage by remember(videoKey) { mutableStateOf(-1) }
    var webViewRef by remember(videoKey) { mutableStateOf<WebView?>(null) }

    val alpha by animateFloatAsState(
        targetValue   = if (stage == 2) 1f else 0f,
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
                    }
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
                    }, "TrailerBridge")

                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (url?.contains("about:blank") == true) return
                            view?.postDelayed({
                                if (stage == 1) {
                                    view.evaluateJavascript(JS_HIDE_CHROME, null)
                                    view.evaluateJavascript(JS_END_LISTENER, null)
                                    stage = 2
                                    onReveal()
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

                    val safe = videoKey.replace("\"", "")
                    loadUrl(
                        "https://www.youtube-nocookie.com/embed/$safe" +
                        "?autoplay=1&mute=1&controls=0&modestbranding=1" +
                        "&playsinline=1&rel=0&iv_load_policy=3&disablekb=1" +
                        "&showinfo=0&enablejsapi=1&vq=hd1080",
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

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
    "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

private const val JS_HIDE_CHROME = """
    (function() {
      var s = document.createElement('style');
      s.textContent = '.ytp-chrome-top,.ytp-chrome-bottom,.ytp-gradient-top,.ytp-gradient-bottom,.ytp-pause-overlay,.ytp-watermark,.ytp-show-cards-title,.ytp-impression-link{display:none!important;opacity:0!important}.html5-video-player{overflow:hidden}';
      document.head.appendChild(s);
    })();
"""

private const val JS_END_LISTENER = """
    (function() {
      var v = document.querySelector('video');
      if (v) {
        v.addEventListener('ended', function() { TrailerBridge.onEnded(); });
      } else {
        var t = setInterval(function() {
          var v2 = document.querySelector('video');
          if (v2) {
            clearInterval(t);
            v2.addEventListener('ended', function() { TrailerBridge.onEnded(); });
          }
        }, 500);
        setTimeout(function() { clearInterval(t); }, 10000);
      }
    })();
"""

private const val JS_PLAY = """
    (function() { var v = document.querySelector('video'); if (v) v.play(); })();
"""
private const val JS_PAUSE = """
    (function() { var v = document.querySelector('video'); if (v) v.pause(); })();
"""
private const val JS_UNMUTE = """
    (function() {
      var v = document.querySelector('video');
      if (v) { v.muted = false; v.volume = 0.8; }
    })();
"""
