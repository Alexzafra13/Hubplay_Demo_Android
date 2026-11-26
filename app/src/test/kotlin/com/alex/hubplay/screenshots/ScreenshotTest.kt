package com.alex.hubplay.screenshots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import coil3.SingletonImageLoader
import com.alex.hubplay.data.LocalTrailerHost
import com.alex.hubplay.data.TrailerHost
import com.alex.hubplay.ui.theme.BgBase
import com.alex.hubplay.ui.theme.HubPlayTheme
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Base de las capturas: pinta Composables reales en la JVM al tamaño de la
 * Mi TV (960×540 dp, xhdpi → 1920×1080 px) y guarda PNG en
 * `app/build/screenshots/`. Se lanzan con
 * `./gradlew :app:testDebugUnitTest -Pscreenshots --tests '*screenshots*'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-rES-w960dp-h540dp-land-television-xhdpi", application = Application::class)
abstract class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun installFakeImages() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        SingletonImageLoader.setUnsafe(FakeArtwork.imageLoader(ctx))
    }

    /** Host de tráileres de la raíz (sin WebView: se simula con reportPlaying). */
    protected lateinit var trailerHost: TrailerHost

    /** Monta [content] con el tema y los CompositionLocals de la raíz de la app. */
    protected fun setTvContent(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val scope = rememberCoroutineScope()
            val host = remember { TrailerHost(scope).also { trailerHost = it } }
            HubPlayTheme {
                CompositionLocalProvider(LocalTrailerHost provides host) {
                    Box(Modifier.fillMaxSize().background(BgBase)) { content() }
                }
            }
        }
        // Antes del primer frame: en modo táctil los clickables no aceptan
        // foco. OJO: el foco INICIAL de cada pantalla no se reproduce fiel
        // (en la TV lo da ViewRootImpl); las capturas lo fijan con la
        // primera pulsación de press().
        compose.runOnUiThread { leaveTouchMode(compose.activity.window.decorView) }
    }

    /**
     * La TV va en modo "sin táctil": sin esto los clickables de Compose no
     * aceptan foco y el D-pad no hace nada. Robolectric arranca en modo
     * táctil e ignora setInTouchMode, así que se toca AttachInfo a mano.
     */
    private fun leaveTouchMode(root: View) {
        val attachInfo = View::class.java.getDeclaredField("mAttachInfo").apply { isAccessible = true }.get(root)
        attachInfo.javaClass.getDeclaredField("mInTouchMode").apply { isAccessible = true }.setBoolean(attachInfo, false)
        // Avisa a Compose (InputModeManager pasa a Keyboard).
        ViewTreeObserver::class.java.getDeclaredMethod("dispatchOnTouchModeChanged", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(root.viewTreeObserver, false)
    }

    /** Avanza reloj de Compose y looper principal (debounces, animaciones, carga). */
    protected fun settle(ms: Long = 1500) {
        var left = ms
        while (left > 0) {
            val step = minOf(left, 100L)
            compose.mainClock.advanceTimeBy(step)
            ShadowLooper.idleMainLooper(step, TimeUnit.MILLISECONDS)
            Thread.sleep(5)
            left -= step
        }
    }

    /** Pulsa una tecla del mando como lo haría la TV (KeyEvent por la Activity). */
    protected fun press(keyCode: Int, times: Int = 1, settleMs: Long = 600) {
        repeat(times) {
            compose.runOnUiThread {
                val root = compose.activity.window.decorView
                if (root.isInTouchMode) leaveTouchMode(root)
                val handled = root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
                // Lo que hace ViewRootImpl en la TV cuando nadie consume la
                // flecha: buscar foco en esa dirección (primer foco incluido).
                if (!handled) composeView(root)?.requestFocus(directionOf(keyCode))
            }
            settle(settleMs)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun rootViews(): List<View> = runCatching {
        val wmg = Class.forName("android.view.WindowManagerGlobal")
        val instance = wmg.getMethod("getInstance").invoke(null)
        (wmg.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<View>).toList()
    }.getOrDefault(emptyList())

    private fun composeView(v: View): View? {
        if (v.javaClass.simpleName == "AndroidComposeView") return v
        if (v is ViewGroup) for (i in 0 until v.childCount) composeView(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun directionOf(keyCode: Int) = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
        KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
        else -> View.FOCUS_DOWN
    }

    /** Pone el foco en el nodo con ese texto (sustituye al foco inicial de la TV). */
    protected fun focusText(text: String) {
        compose.onNode(hasText(text) and hasClickAction(), useUnmergedTree = false)
            .performSemanticsAction(SemanticsActions.RequestFocus)
        settle(1200)
    }

    protected fun snap(name: String) {
        val dir = File(System.getProperty("hubplay.screenshotDir") ?: "build/screenshots").apply { mkdirs() }
        // captureToImage espera un redibujado que con el looper en pausa de
        // Robolectric no llega: se dibuja la vista raíz a mano.
        val root = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        // Todas las ventanas (la de la Activity y las de diálogos / hojas
        // inferiores, que Compose abre aparte), en orden, en su posición.
        for (view in rootViews().ifEmpty { listOf(root) }) {
            if (!view.isShown || view.width == 0) continue
            val loc = IntArray(2).also { view.getLocationOnScreen(it) }
            canvas.save()
            canvas.translate(loc[0].toFloat(), loc[1].toFloat())
            view.draw(canvas)
            canvas.restore()
        }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
