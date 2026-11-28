package com.alex.hubplay.screenshots

import android.view.KeyEvent.KEYCODE_BACK
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import coil3.compose.AsyncImage
import com.alex.hubplay.player.PlayerState
import com.alex.hubplay.ui.player.PlayerChapter
import com.alex.hubplay.ui.player.PlayerOptionsSheet
import com.alex.hubplay.ui.player.PlayerOptionsState
import com.alex.hubplay.ui.player.ScrubState
import com.alex.hubplay.ui.player.SkipKind
import com.alex.hubplay.ui.player.SkipSegment
import com.alex.hubplay.ui.player.TrickplayInfo
import com.alex.hubplay.ui.player.TrickplaySprite
import com.alex.hubplay.ui.player.TrickplayThumb
import com.alex.hubplay.ui.player.VideoFit
import com.alex.hubplay.ui.player.VodChromeActions
import com.alex.hubplay.ui.player.VodChromeInfo
import com.alex.hubplay.ui.player.VodPlayerLayer
import org.junit.After
import org.junit.Test

/** Chrome del reproductor VOD con un fotograma inventado detrás (sin vídeo real). */
class PlayerScreenshots : ScreenshotTest() {

    private val exo by lazy { ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build() }

    @After
    fun release() = exo.release()

    private val info = VodChromeInfo(
        title = "Código Ámbar",
        subtitle = "T3 · E1 · Fuego cruzado",
        backdropUrl = null,
        logoUrl = FakeArtwork.logo("Código Ámbar"),
        hasNext = true,
        // Intro desde el segundo 0: el reproductor de test está en 0.
        skipSegments = listOf(SkipSegment(SkipKind.Intro, startMs = 0, endMs = 90_000)),
    )

    @androidx.compose.runtime.Composable
    private fun frame() {
        AsyncImage(model = FakeArtwork.still("frame"), contentDescription = null, modifier = Modifier.fillMaxSize())
    }

    @Test
    fun chrome() {
        setTvContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                frame()
                VodPlayerLayer(
                    info = info,
                    exo = exo,
                    playerState = PlayerState(isReady = true),
                    preparing = false,
                    actions = VodChromeActions({}, {}, {}),
                )
            }
        }
        settle(1500)
        snap("player_01_chrome_skip")
        press(KEYCODE_BACK, settleMs = 1200)
        snap("player_02_hidden_pill")
    }

    /** Hoja falsa 10×3 de fotogramas numerados (como la del servidor). */
    private fun fakeSprite(): TrickplaySprite {
        val sheet = android.graphics.Bitmap.createBitmap(3200, 540, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(sheet)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 64f
            isFakeBoldText = true
        }
        for (i in 0 until 30) {
            val x = (i % 10) * 320f
            val y = (i / 10) * 180f
            val still = android.graphics.Bitmap.createScaledBitmap(FakeArtwork.render(FakeArtwork.still("tp$i")), 320, 180, true)
            canvas.drawBitmap(still, x, y, null)
            canvas.drawText("${i * 10}s", x + 16f, y + 72f, paint)
        }
        // Robolectric no implementa BitmapRegionDecoder: se recorta en memoria.
        return TrickplaySprite(decodeRegion = { r, _ ->
            android.graphics.Bitmap.createBitmap(sheet, r.left, r.top, r.width(), r.height())
        })
    }

    @Test
    fun trickplay() {
        val sprite = fakeSprite()
        val withThumbs = VodChromeInfo(
            title = info.title, subtitle = info.subtitle, backdropUrl = null, logoUrl = info.logoUrl, hasNext = true,
            trickplay = TrickplayInfo("x", intervalMs = 10_000, thumbWidth = 320, thumbHeight = 180, columns = 10, total = 30),
        )
        // Sin vídeo el ExoPlayer de test no tiene duración: la miniatura se
        // monta aparte con una duración de 50 min para ver dónde cae.
        val scrub = ScrubState()
        setTvContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                frame()
                VodPlayerLayer(
                    info = withThumbs,
                    exo = exo,
                    playerState = PlayerState(isReady = true, isPlaying = true),
                    preparing = false,
                    actions = VodChromeActions({}, {}, {}),
                    sprite = sprite,
                )
                TrickplayThumb(scrub, withThumbs.trickplay, sprite) { 3_000_000L }
            }
        }
        settle(1500)
        compose.runOnUiThread { scrub.mark(1_150_000L) }
        settle(400)
        snap("player_04_trickplay")
    }

    @Test
    fun options() {
        setTvContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                frame()
                PlayerOptionsSheet(
                    player = exo,
                    state = PlayerOptionsState(
                        fit = VideoFit.Fit,
                        chapters = listOf(
                            PlayerChapter(0, "Apertura"),
                            PlayerChapter(420_000, "El puerto"),
                            PlayerChapter(1_260_000, null),
                        ),
                        method = "direct_stream",
                        hasSubtitles = true,
                    ),
                    onFit = {},
                    onSubtitles = {},
                    onDismiss = {},
                )
            }
        }
        settle(1500)
        snap("player_03_options")
    }
}
