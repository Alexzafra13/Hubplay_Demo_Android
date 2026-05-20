package com.alex.hubplay.ui.screensaver

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.alex.hubplay.R
import com.alex.hubplay.data.ScreensaverSlide
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Jellyfin-style ambient screensaver.
 *
 * What you see:
 *   - A full-screen backdrop crossfades to the next every [SLIDE_DURATION_MS].
 *   - Each backdrop has its own slow Ken Burns animation (pan +
 *     gentle zoom over 14 s, linear easing) so the image never feels
 *     static. Range tuned conservatively so the movement reads as
 *     "alive" not "drifting" at 3 m away on a 55" panel.
 *   - The current item's title + year fade in after a beat so the
 *     image carries on its own first, then context arrives.
 *   - A subtle bottom-end clock + bottom-start brand wordmark frame
 *     the composition without competing with the art.
 *
 * What it doesn't do:
 *   - Audio. Adding a player here would defeat the "ambient" point
 *     and burn the panel during pre-bedtime use.
 *   - Random transitions. Crossfade only; nothing flashier than that
 *     plays well at the cinema-on-a-wall use case.
 *
 * Input dismissal is handled one level up: MainActivity sees the key
 * event, calls IdleController.onInteraction() which flips isIdle
 * back to false, the root composable removes this overlay. The
 * MainActivity intentionally CONSUMES the dismiss event so the user's
 * first key press only wakes the app — it doesn't also scroll the
 * underlying screen, which would feel jarring.
 */
@Composable
fun ScreensaverOverlay(
    slides:   List<ScreensaverSlide>,
    modifier: Modifier = Modifier,
) {
    if (slides.isEmpty()) {
        // No backdrops cached yet — render a plain dark surface with
        // brand watermark + clock. Better than nothing if the user
        // hit idle before /me/home/trending resolved.
        EmptyScreensaver(modifier = modifier)
        return
    }

    var index by remember { mutableIntStateOf(0) }
    val current by remember { derivedStateOf { slides[index % slides.size] } }

    LaunchedEffect(slides) {
        // Reset on pool refresh so a brand-new slide list starts at 0.
        index = 0
        while (true) {
            delay(SLIDE_DURATION_MS)
            index = (index + 1) % slides.size
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // ── Crossfading backdrop layer ─────────────────────────────────
        Crossfade(
            targetState   = current,
            animationSpec = tween(CROSSFADE_MS, easing = LinearEasing),
            label         = "screensaver-slide",
        ) { slide ->
            KenBurnsBackdrop(slide = slide)
        }

        // Cinematic vignette — bottom gets darker so the title text
        // and clock read against the image regardless of which scene
        // is showing.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.78f),
                    ),
                ),
        )

        // ── Title block (bottom-center, fades in after a beat) ─────────
        SlideCaption(
            slide    = current,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp, start = 64.dp, end = 64.dp),
        )

        // ── Brand watermark, bottom-start
        Image(
            painter            = painterResource(R.drawable.brand_wordmark),
            contentDescription = null,
            modifier           = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 32.dp, bottom = 28.dp)
                .height(22.dp)
                .alpha(0.55f),
        )

        // ── Clock, bottom-end
        ClockBadge(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 32.dp, bottom = 28.dp),
        )
    }
}

@Composable
private fun KenBurnsBackdrop(slide: ScreensaverSlide) {
    // One Animatable each per (slide.id, dimension) — keyed on the id
    // so cross-fading into a new slide creates fresh animations rather
    // than continuing the previous one.
    val density = LocalDensity.current
    val scale = remember(slide.id) { Animatable(1.04f) }
    val tx    = remember(slide.id) { Animatable(-24f) }  // dp

    LaunchedEffect(slide.id) {
        scale.animateTo(
            targetValue   = 1.10f,
            animationSpec = tween(durationMillis = SLIDE_DURATION_MS.toInt(), easing = LinearEasing),
        )
    }
    LaunchedEffect(slide.id) {
        tx.animateTo(
            targetValue   = 24f,
            animationSpec = tween(durationMillis = SLIDE_DURATION_MS.toInt(), easing = LinearEasing),
        )
    }

    AsyncImage(
        model              = slide.backdropUrl,
        contentDescription = slide.title,
        contentScale       = ContentScale.Crop,
        modifier           = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                translationX = with(density) { tx.value.dp.toPx() }
            },
    )
}

@Composable
private fun SlideCaption(slide: ScreensaverSlide, modifier: Modifier = Modifier) {
    // Stagger the caption fade-in so the image owns the first second
    // of attention. Re-keys on slide.id so each new image gets its own
    // fade.
    val alpha = remember(slide.id) { Animatable(0f) }
    LaunchedEffect(slide.id) {
        delay(1_500L)
        alpha.animateTo(1f, animationSpec = tween(800))
    }

    Column(
        modifier            = modifier.fillMaxWidth().alpha(alpha.value),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text       = slide.title,
            color      = Color.White,
            fontSize   = 28.sp,
            fontWeight = FontWeight.Bold,
            maxLines   = 1,
        )
        slide.year?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text     = it.toString(),
                color    = Color.White.copy(alpha = 0.72f),
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun ClockBadge(modifier: Modifier = Modifier) {
    // Re-renders once a minute. Composing a clock with second granularity
    // wastes battery and the user can't tell the difference from 3 m away.
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val nowVal = LocalTime.now()
            now = nowVal
            // Sleep until the next minute boundary so the displayed
            // time and the clock change in sync.
            val secondsToNextMinute = 60 - nowVal.second
            delay(secondsToNextMinute * 1_000L)
        }
    }

    Row(
        modifier              = modifier,
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text       = now.format(CLOCK_FORMAT),
            color      = Color.White,
            fontSize   = 32.sp,
            fontWeight = FontWeight.Light,
        )
    }
}

@Composable
private fun EmptyScreensaver(modifier: Modifier = Modifier) {
    Box(
        modifier         = modifier.fillMaxSize().background(Color(0xFF0A0A0A)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter            = painterResource(R.drawable.brand_wordmark),
            contentDescription = "HubPlay",
            modifier           = Modifier.height(48.dp).alpha(0.40f),
        )
        ClockBadge(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = 28.dp),
        )
    }
}

private const val SLIDE_DURATION_MS = 14_000L
private const val CROSSFADE_MS      = 1_400
private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
