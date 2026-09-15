package com.alex.hubplay.ui.metadata

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** [identifyQuery]: el título con el que se siembra la búsqueda en TMDb. */
class IdentifyQueryTest {

    @Test
    fun `quita el año entre paréntesis del nombre de carpeta`() {
        assertThat(identifyQuery("The Mandalorian (2019)")).isEqualTo("The Mandalorian")
        assertThat(identifyQuery("Bumblebee (2018) ")).isEqualTo("Bumblebee")
    }

    @Test
    fun `quita el año entre corchetes o tras guion`() {
        assertThat(identifyQuery("Bumblebee [2018]")).isEqualTo("Bumblebee")
        assertThat(identifyQuery("Bumblebee - 2018")).isEqualTo("Bumblebee")
        assertThat(identifyQuery("Bumblebee: 2018")).isEqualTo("Bumblebee")
    }

    @Test
    fun `respeta títulos sin año o con números sueltos que son parte del título`() {
        assertThat(identifyQuery("Toy Story 3")).isEqualTo("Toy Story 3")
        assertThat(identifyQuery("Blade Runner 2049")).isEqualTo("Blade Runner 2049")
        assertThat(identifyQuery("1917")).isEqualTo("1917")
        assertThat(identifyQuery("Ash 01")).isEqualTo("Ash 01")
        assertThat(identifyQuery("Toc, toc")).isEqualTo("Toc, toc")
    }

    @Test
    fun `si solo hay año, deja el título tal cual`() {
        assertThat(identifyQuery("2019")).isEqualTo("2019")
        assertThat(identifyQuery("(2019)")).isEqualTo("(2019)")
    }
}
