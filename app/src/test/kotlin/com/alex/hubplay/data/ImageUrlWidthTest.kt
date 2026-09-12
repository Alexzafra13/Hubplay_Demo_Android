package com.alex.hubplay.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** [withImageWidth] reescribe el `w=` que [HomeRepository] añade a las imágenes del servidor. */
class ImageUrlWidthTest {

    @Test
    fun `replaces the width of a server image url`() {
        val url = "https://hubplay.example/api/v1/images/file/abc?w=1280"
        assertThat(withImageWidth(url, 1920))
            .isEqualTo("https://hubplay.example/api/v1/images/file/abc?w=1920")
    }

    @Test
    fun `keeps other query params and handles ampersand separator`() {
        val url = "https://hubplay.example/api/v1/images/file/abc?token=x&w=400"
        assertThat(withImageWidth(url, 1920))
            .isEqualTo("https://hubplay.example/api/v1/images/file/abc?token=x&w=1920")
    }

    @Test
    fun `leaves remote artwork without width untouched`() {
        val url = "https://image.tmdb.org/t/p/original/abc.jpg"
        assertThat(withImageWidth(url, 1920)).isEqualTo(url)
    }
}
