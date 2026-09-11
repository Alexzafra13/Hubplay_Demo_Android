package com.alex.hubplay.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.alex.hubplay.R
import com.alex.hubplay.ui.theme.BgBase
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Fracción del ancho del wordmark que ocupa el mando (la marca). */
private const val MARK_FRACTION = 0.29f

/** Tiempos (ms). Total ≈ 1.2 s; en un arranque real solapa con la carga. */
private const val MARK_IN_MS   = 320
private const val TEXT_REVEAL_MS = 460
private const val HOLD_MS      = 220L
private const val FADE_OUT_MS  = 260

private const val MARK_SCALE_FROM = 0.86f

/** Tope de espera al splash del sistema (ms) si el listener no dispara. */
private const val GATE_TIMEOUT_MS = 2500L

/** Salida rápida y frenada larga: el texto "llega" y se asienta. */
private val TextRevealEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/** Altura del wordmark en el intro (canvas TV 540dp de alto). */
private val IntroWordmarkHeight = 96.dp

/** El intro se reproduce UNA vez por proceso: volver de segundo plano
 *  no debe repetirlo. */
private var introPlayed = false

/**
 * Intro de marca al arrancar: el mando aparece con un scale-in suave y
 * el nombre "HubPlay" se revela de izquierda a derecha saliendo del
 * mando; breve pausa y el overlay se funde dejando la app debajo.
 *
 * Coste: dos `Animatable` y un `clipRect` en `drawWithContent` sobre un
 * único vector — nada de vídeo ni bitmaps grandes. En una Mi Box es
 * imperceptible. Lo que sí cuesta es el TIEMPO: por eso dura ~1.2 s,
 * solapa con la carga real (el NavHost ya está montado debajo) y no se
 * repite al volver de segundo plano.
 *
 * Sustituye visualmente al icono del splash del sistema (que en TV
 * salía recortado por la máscara circular de SplashScreen).
 */
@Composable
fun BrandIntro(onFinished: () -> Unit) {
    if (introPlayed) {
        LaunchedEffect(Unit) { onFinished() }
        return
    }
    val reveal  = remember { Animatable(0f) }      // 0 → MARK_FRACTION → 1
    val scale   = remember { Animatable(MARK_SCALE_FROM) }
    val alpha   = remember { Animatable(0f) }
    val overlay = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // Esperar a que el sistema retire su splash (o al tope) para que
        // la animación no corra tapada. Mientras, el overlay es BgBase
        // opaco: idéntico al fondo del splash, sin salto visual.
        withTimeoutOrNull(GATE_TIMEOUT_MS) { BrandIntroGate.opened.first { it } }
        coroutineScope {
            // Fase A: el mando entra (alpha + scale) mientras el clip lo
            // descubre. Fase B: el texto se revela desde el mando.
            val a1 = async { alpha.animateTo(1f, tween(MARK_IN_MS, easing = LinearEasing)) }
            val a2 = async { scale.animateTo(1f, tween(MARK_IN_MS + TEXT_REVEAL_MS, easing = FastOutSlowInEasing)) }
            reveal.animateTo(MARK_FRACTION, tween(MARK_IN_MS, easing = FastOutSlowInEasing))
            reveal.animateTo(1f, tween(TEXT_REVEAL_MS, easing = TextRevealEasing))
            a1.await()
            a2.await()
        }
        kotlinx.coroutines.delay(HOLD_MS)
        overlay.animateTo(0f, tween(FADE_OUT_MS, easing = LinearEasing))
        introPlayed = true
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = overlay.value }
            .background(BgBase),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter            = painterResource(R.drawable.brand_wordmark),
            contentDescription = null,
            modifier           = Modifier
                .height(IntroWordmarkHeight)
                .graphicsLayer {
                    this.alpha  = alpha.value
                    scaleX      = scale.value
                    scaleY      = scale.value
                }
                .drawWithContent {
                    clipRect(right = size.width * reveal.value) {
                        this@drawWithContent.drawContent()
                    }
                },
        )
    }
}
