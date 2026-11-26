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
import com.alex.hubplay.ui.player.SkipKind
import com.alex.hubplay.ui.player.SkipSegment
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
                    ),
                    onFit = {},
                    onDismiss = {},
                )
            }
        }
        settle(1500)
        snap("player_03_options")
    }
}
