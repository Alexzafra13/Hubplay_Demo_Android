package com.alex.hubplay.ui.player

import android.content.Context
import android.graphics.Color
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.alex.hubplay.R

/** Tamaño de los subtítulos, relativo al de Media3 (5,33 % del alto). */
enum class SubtitleSize(@StringRes val labelRes: Int, val scale: Float) {
    Small(R.string.player_sub_size_small, 0.8f),
    Normal(R.string.player_sub_size_normal, 1f),
    Large(R.string.player_sub_size_large, 1.3f),
    Huge(R.string.player_sub_size_huge, 1.6f),
}

/** Cómo se separa el texto del vídeo. */
enum class SubtitleLook(@StringRes val labelRes: Int) {
    Outline(R.string.player_sub_look_outline),
    Shadow(R.string.player_sub_look_shadow),
    Box(R.string.player_sub_look_box),
}

/**
 * Aspecto de los subtítulos de texto (los quemados por el servidor van en
 * el vídeo y no cambian). Se guarda en el aparato: es una preferencia de
 * la tele (distancia al sofá), no del perfil.
 */
@Immutable
data class SubtitleAppearance(
    val size: SubtitleSize = SubtitleSize.Normal,
    val look: SubtitleLook = SubtitleLook.Outline,
) {
    /** Aplica el aspecto al SubtitleView del PlayerView. */
    @UnstableApi
    fun applyTo(view: SubtitleView?) {
        view ?: return
        view.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * size.scale)
        view.setStyle(
            CaptionStyleCompat(
                Color.WHITE,
                if (look == SubtitleLook.Box) BOX_COLOR else Color.TRANSPARENT,
                Color.TRANSPARENT,
                when (look) {
                    SubtitleLook.Outline -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
                    SubtitleLook.Shadow  -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                    SubtitleLook.Box     -> CaptionStyleCompat.EDGE_TYPE_NONE
                },
                Color.BLACK,
                null,
            ),
        )
    }

    fun save(context: Context) {
        prefs(context).edit()
            .putString(KEY_SIZE, size.name)
            .putString(KEY_LOOK, look.name)
            .apply()
    }

    companion object {
        /** Negro al 70 %: legible sin tapar media imagen. */
        private const val BOX_COLOR = 0xB3000000.toInt()
        private const val PREFS = "hubplay_player"
        private const val KEY_SIZE = "subtitle_size"
        private const val KEY_LOOK = "subtitle_look"

        fun load(context: Context): SubtitleAppearance {
            val p = prefs(context)
            return SubtitleAppearance(
                size = SubtitleSize.entries.firstOrNull { it.name == p.getString(KEY_SIZE, null) } ?: SubtitleSize.Normal,
                look = SubtitleLook.entries.firstOrNull { it.name == p.getString(KEY_LOOK, null) } ?: SubtitleLook.Outline,
            )
        }

        private fun prefs(context: Context) =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}
