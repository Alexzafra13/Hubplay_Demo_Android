package com.alex.hubplay.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs

/**
 * Carátulas inventadas para las capturas: cada URL `fake://<tipo>/<semilla>/<texto>`
 * se pinta con un degradado determinista (mismo título → mismos colores) y
 * texto, así las capturas se parecen a una biblioteca real sin red ni
 * imágenes con derechos.
 */
object FakeArtwork {

    fun poster(seed: String, title: String) = "fake://poster/$seed/${title.enc()}"
    fun backdrop(seed: String) = "fake://backdrop/$seed/-"
    fun still(seed: String) = "fake://still/$seed/-"
    fun logo(title: String) = "fake://logo/x/${title.enc()}"
    fun channel(seed: String, name: String) = "fake://channel/$seed/${name.enc()}"
    fun avatar(seed: String) = "fake://avatar/$seed/-"

    private fun String.enc() = replace("/", " ")

    fun imageLoader(context: android.content.Context): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(Gen) }
            .coroutineContext(Dispatchers.Unconfined)
            .build()

    private object Gen : Interceptor {
        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            val url = chain.request.data.toString()
            val bmp = render(url)
            return SuccessResult(image = bmp.asImage(), request = chain.request, dataSource = DataSource.MEMORY_CACHE)
        }
    }

    fun render(url: String): Bitmap {
        val parts = url.removePrefix("fake://").split("/", limit = 3)
        val kind = parts.getOrElse(0) { "backdrop" }
        val seed = parts.getOrElse(1) { url }
        val text = parts.getOrElse(2) { "" }.substringBefore('?')
        return when (kind) {
            "poster" -> scene(400, 600, seed, title = text)
            "still" -> scene(640, 360, seed, title = null)
            "logo" -> logoText(text)
            "channel" -> scene(320, 320, seed, title = text, flat = true)
            "avatar" -> scene(256, 256, seed, title = null, flat = true)
            // Cualquier otra URL (logos de canal construidos por id, etc.):
            // backdrop genérico.
            else -> scene(1280, 720, seed.ifBlank { url }, title = null)
        }
    }

    private fun hue(seed: String, salt: Int): Float = (abs((seed.hashCode() * 31 + salt * 7919) % 360)).toFloat()

    private fun hsv(h: Float, s: Float, v: Float) = Color.HSVToColor(floatArrayOf(h, s, v))

    private fun scene(w: Int, h: Int, seed: String, title: String?, flat: Boolean = false): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val h1 = hue(seed, 1)
        val h2 = (h1 + 40 + hue(seed, 2) / 6) % 360
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), hsv(h1, 0.55f, 0.45f), hsv(h2, 0.7f, 0.12f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        if (!flat) {
            // "Luces" de la escena: dos manchas radiales y un horizonte.
            p.shader = RadialGradient(w * 0.7f, h * 0.35f, w * 0.45f, hsv(h2, 0.4f, 0.95f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            p.alpha = 150
            c.drawCircle(w * 0.7f, h * 0.35f, w * 0.45f, p)
            p.shader = RadialGradient(w * 0.2f, h * 0.8f, w * 0.35f, hsv(h1, 0.6f, 0.8f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            p.alpha = 110
            c.drawCircle(w * 0.2f, h * 0.8f, w * 0.35f, p)
            p.shader = null
            p.alpha = 255
            p.color = hsv(h1, 0.6f, 0.08f)
            val path = android.graphics.Path().apply {
                moveTo(0f, h * 0.78f)
                lineTo(w * 0.3f, h * 0.68f)
                lineTo(w * 0.55f, h * 0.74f)
                lineTo(w * 0.8f, h * 0.62f)
                lineTo(w.toFloat(), h * 0.7f)
                lineTo(w.toFloat(), h.toFloat())
                lineTo(0f, h.toFloat())
                close()
            }
            c.drawPath(path, p)
        }
        if (!title.isNullOrBlank()) {
            p.shader = null
            p.color = Color.WHITE
            p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            p.textAlign = Paint.Align.CENTER
            p.textSize = w / 9f
            val words = title.uppercase().split(" ")
            val lines = mutableListOf<String>()
            var cur = ""
            for (word in words) {
                val next = if (cur.isEmpty()) word else "$cur $word"
                if (p.measureText(next) > w * 0.86f && cur.isNotEmpty()) {
                    lines += cur
                    cur = word
                } else {
                    cur = next
                }
            }
            if (cur.isNotEmpty()) lines += cur
            val block = (lines.size - 1) * p.textSize
            val startY = if (flat) h / 2f - block / 2 + p.textSize / 3 else h * 0.82f - block
            lines.forEachIndexed { i, line -> c.drawText(line, w / 2f, startY + i * p.textSize * 1.05f, p) }
        }
        return bmp
    }

    private fun logoText(title: String): Bitmap {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textSize = 120f
            letterSpacing = 0.04f
        }
        val text = title.uppercase()
        val w = (p.measureText(text) + 40).toInt().coerceAtLeast(200)
        val bmp = Bitmap.createBitmap(w, 170, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(text, 20f, 130f, p)
        return bmp
    }
}
