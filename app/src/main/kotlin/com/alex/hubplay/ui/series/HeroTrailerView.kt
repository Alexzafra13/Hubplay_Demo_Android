package com.alex.hubplay.ui.series

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
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

/**
 * Netflix-style autoplay-muted YouTube trailer over a series hero.
 *
 * Mirrors the web HeroTrailer behaviour
 * (`web/src/components/media/HeroTrailer.tsx`) by embedding the SAME
 * `youtube-nocookie.com/embed/...` iframe inside a plain WebView. We
 * tried the wrapper library `android-youtube-player` first and its
 * `onStateChange` callbacks never fired on Android TV WebViews —
 * dropping the wrapper and using the iframe directly works because
 * YouTube guarantees the embed surface across browsers (the web
 * client relies on the exact same URL).
 *
 * Lifecycle / cost model is the same as before:
 *
 *   1. **Pre-flight oEmbed** — if YouTube oEmbed responds non-2xx
 *      (region-restricted, removed, embed-disabled) we skip mounting
 *      the WebView entirely. The user sees the static backdrop.
 *   2. **Load delay** — wait 2500 ms before instantiating the WebView,
 *      so the static backdrop gets a clean first paint.
 *   3. **Reveal delay** — wait 1200 ms after `onPageFinished` to hide
 *      YouTube's brief pre-roll chrome, then fade in.
 *   4. **Watchdog** — if `onPageFinished` doesn't arrive in 10s we
 *      dismiss. (Bumped from 6s to 10s because slow Android TV
 *      WebViews can take longer to load the embed page.)
 *   5. **`onReveal` / `onDismiss`** — host crossfades the backdrop
 *      in lockstep so two visual layers never fight for attention.
 */
@Composable
fun HeroTrailerView(
    videoKey: String,
    site:     String,
    modifier: Modifier = Modifier,
    onReveal: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    if (site != "YouTube") {
        Log.d(TAG, "skip trailer — site=$site (only YouTube supported), key=$videoKey")
        return
    }
    Log.d(TAG, "HeroTrailerView composed key=$videoKey")

    val lifecycleOwner = LocalLifecycleOwner.current

    // Reveal state machine — same shape as before:
    //   stage = -1  pre-flight oEmbed
    //   stage =  0  pre-flight passed, waiting load delay
    //   stage =  1  WebView mounted, waiting for page-finished + settle
    //   stage =  2  revealed (fade in)
    //   stage =  3  dismissed (watchdog or onDismiss)
    var stage by remember(videoKey) { mutableStateOf(-1) }
    var webViewRef by remember(videoKey) { mutableStateOf<WebView?>(null) }

    val alpha by animateFloatAsState(
        targetValue   = if (stage == 2) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
        label         = "trailer-alpha",
    )

    // ── 1. Pre-flight oEmbed ──────────────────────────────────────────────
    LaunchedEffect(videoKey) {
        stage = -1
        val ok = withContext(Dispatchers.IO) { isEmbeddable(videoKey) }
        Log.d(TAG, "oEmbed pre-flight for $videoKey → embeddable=$ok")
        if (ok) stage = 0 else { stage = 3; onDismiss() }
    }

    // ── 2. Load delay ────────────────────────────────────────────────────
    LaunchedEffect(stage) {
        if (stage == 0) {
            delay(2_500)
            Log.d(TAG, "load delay elapsed, mounting WebView for $videoKey")
            stage = 1
        }
    }

    // ── 4. Watchdog ──────────────────────────────────────────────────────
    LaunchedEffect(stage) {
        if (stage == 1) {
            delay(10_000)
            if (stage == 1) {
                Log.w(TAG, "watchdog: WebView never finished loading within 10s, dismissing $videoKey")
                stage = 3
                onDismiss()
            }
        }
    }

    // ── 5. Lifecycle aware play/pause via JS calls into the iframe ──────
    // YouTube's iframe API exposes postMessage commands. The cheapest
    // way to pause/resume without pulling a full SDK is to call
    // `player.pauseVideo()` / `player.playVideo()` via the iframe's
    // contentWindow.postMessage. We let YouTube re-enter automatically
    // on Resume (it's muted anyway so the user perception is fine).
    DisposableEffect(lifecycleOwner, webViewRef) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            val web = webViewRef ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> web.evaluateJavascript(POST_PLAY, null)
                Lifecycle.Event.ON_PAUSE  -> web.evaluateJavascript(POST_PAUSE, null)
                else                       -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // The WebView is only rendered in the "mounted" (1) and "revealed"
    // (2) stages. Earlier we were rendering at stage 0 too, which meant
    // the embed was loading 2 seconds before the load-delay timer
    // elapsed and `onPageFinished` reached the reveal block when stage
    // was still 0, so the reveal was always skipped and the watchdog
    // always tripped.
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
                    // Black background so the brief load flash matches BgBase.
                    setBackgroundColor(android.graphics.Color.BLACK)

                    // No D-pad focus — the hero CTAs own focus.
                    isFocusable = false
                    isFocusableInTouchMode = false

                    settings.apply {
                        javaScriptEnabled = true
                        // Required for YouTube to autoplay without a tap.
                        mediaPlaybackRequiresUserGesture = false
                        domStorageEnabled = true
                        // The iframe relies on these for proper scaling
                        // inside the embedded player chrome.
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }

                    // YouTube's embed sets a session cookie inside the
                    // iframe (third-party cookies from our WebView's
                    // perspective). Without this the player loads but
                    // never starts playback on Android TV WebViews.
                    android.webkit.CookieManager.getInstance()
                        .setAcceptThirdPartyCookies(this, true)

                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            Log.d(TAG, "WebView onPageFinished url=$url stage=$stage")
                            // The page is loaded — give YouTube 1.2s to
                            // actually start playback before we fade the
                            // static backdrop out. The guard inside the
                            // postDelayed catches a fast dismiss/race.
                            view?.postDelayed({
                                if (stage == 1) {
                                    Log.d(TAG, "trailer revealed $videoKey")
                                    stage = 2
                                    onReveal()
                                    view.evaluateJavascript(POST_HD_QUALITY, null)
                                    view.evaluateJavascript(POST_UNMUTE, null)
                                } else {
                                    Log.d(TAG, "reveal skipped, stage=$stage (already revealed or dismissed)")
                                }
                            }, 1_200)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?,
                        ) {
                            Log.w(TAG, "WebView error code=$errorCode desc=$description url=$failingUrl")
                            if (stage in 0..1) {
                                stage = 3
                                onDismiss()
                            }
                        }
                    }

                    val html = buildIframeHtml(videoKey)
                    loadDataWithBaseURL(
                        "https://www.youtube-nocookie.com",
                        html,
                        "text/html",
                        "UTF-8",
                        null,
                    )
                    webViewRef = this
                }
            },
            onRelease = { view ->
                runCatching {
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
 * Builds the HTML wrapper around the YouTube iframe.
 *
 * Flag set (mirror of `web/src/components/media/HeroTrailer.tsx::trailerEmbedURL`):
 *  - autoplay=1 + mute=1 + playsinline=1: autoplay everywhere. We
 *    start muted because WebView (like browsers) blocks autoplay-with-
 *    sound without a user gesture. The reveal handler calls
 *    `postMessage(unMute)` ~1.2s after onPageFinished, when playback
 *    has already started, to bring sound in without re-triggering the
 *    autoplay gate.
 *  - enablejsapi=1: required for the postMessage commands (unMute,
 *    playVideo, pauseVideo) to be honoured by the iframe player.
 *  - controls=0 + modestbranding=1 + rel=0 + iv_load_policy=3 + showinfo=0:
 *    strip everything YouTube allows us to strip.
 *  - loop=1 + playlist=KEY: relaunch the video at end, never showing
 *    YouTube's "related videos" end card.
 *  - disablekb=1: D-pad keys never reach the player.
 *
 * Iframe sizing trick — same one Netflix / Plex use to hide what
 * YouTube DOESN'T let us strip with flags (the title overlay top-left
 * during the first seconds, the bottom progress hint, the "Watch on
 * YouTube" link). We size the iframe at 112% × 112% and shift it
 * 6% / 6% up-and-left, then clip via `overflow: hidden` on the
 * body. The hero ends up seeing only the central crop of the video —
 * most of YouTube's edge chrome falls outside the visible area.
 * Kept tight (was 130%) so the effective resolution stays high on
 * TV screens where the upscale is visible.
 */
private fun buildIframeHtml(videoKey: String): String {
    val safe = videoKey.replace("\"", "")
    return """
        <!DOCTYPE html>
        <html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>
          html,body{margin:0;padding:0;background:#000;overflow:hidden;height:100%;width:100%}
          .wrap{position:absolute;top:0;left:0;right:0;bottom:0;overflow:hidden}
          iframe{
            position:absolute;
            top:-6%; left:-6%;
            width:112%; height:112%;
            border:0;
            pointer-events:none;
          }
        </style>
        </head><body>
          <div class="wrap">
            <iframe
              src="https://www.youtube-nocookie.com/embed/$safe?autoplay=1&mute=1&controls=0&loop=1&playlist=$safe&modestbranding=1&playsinline=1&rel=0&iv_load_policy=3&disablekb=1&showinfo=0&enablejsapi=1&vq=hd1080"
              allow="autoplay; encrypted-media; picture-in-picture"
              allowfullscreen></iframe>
          </div>
        </body></html>
    """.trimIndent()
}

/**
 * oEmbed pre-flight. Cheap GET to YouTube — 200 means publicly
 * embeddable, anything else maps to "skip the trailer entirely".
 */
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
            try {
                responseCode in 200..299
            } finally {
                disconnect()
            }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "oEmbed pre-flight failed for $videoKey: ${t.message}")
        false
    }
}

private const val TAG = "HeroTrailerView"

// JS postMessage payloads for the YouTube iframe player API. The
// iframe must have `enablejsapi=1` in its src for the player to listen
// to these. We don't need YouTube's JS SDK on our side — the iframe
// itself injects the message handler.
private const val POST_PLAY = """
    (function() { var f = document.querySelector('iframe'); if (f && f.contentWindow) f.contentWindow.postMessage('{"event":"command","func":"playVideo","args":""}', '*'); })();
"""
private const val POST_PAUSE = """
    (function() { var f = document.querySelector('iframe'); if (f && f.contentWindow) f.contentWindow.postMessage('{"event":"command","func":"pauseVideo","args":""}', '*'); })();
"""
/**
 * The unmute trick: the iframe started with `mute=1` so autoplay was
 * granted unconditionally; once playback is rolling we send `unMute`
 * via the iframe API and the audio fades in. WebView never re-checks
 * the autoplay policy because the player is already playing — we
 * just changed its volume.
 */
private const val POST_HD_QUALITY = """
    (function() { var f = document.querySelector('iframe'); if (f && f.contentWindow) f.contentWindow.postMessage('{"event":"command","func":"setPlaybackQuality","args":["hd1080"]}', '*'); })();
"""
private const val POST_UNMUTE = """
    (function() { var f = document.querySelector('iframe'); if (f && f.contentWindow) { f.contentWindow.postMessage('{"event":"command","func":"unMute","args":""}', '*'); f.contentWindow.postMessage('{"event":"command","func":"setVolume","args":[80]}', '*'); } })();
"""
