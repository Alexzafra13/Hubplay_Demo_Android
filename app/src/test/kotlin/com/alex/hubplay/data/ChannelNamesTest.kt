package com.alex.hubplay.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelNamesTest {

    @Test
    fun `quita calidad entre parentesis`() {
        assertEquals("12TV Alicante", cleanChannelName("12TV Alicante (720p)"))
        assertEquals("La 1", cleanChannelName("La 1 (1080p)"))
        assertEquals("Canal Sur", cleanChannelName("Canal Sur (HD)"))
        assertEquals("Movistar", cleanChannelName("Movistar (4K)"))
    }

    @Test
    fun `quita etiquetas entre corchetes`() {
        assertEquals("3Cat Exclusiu 1", cleanChannelName("3Cat Exclusiu 1 (1080p) [Geo-blocked]"))
        assertEquals("Telecinco", cleanChannelName("Telecinco [Not 24/7]"))
    }

    @Test
    fun `no toca nombres limpios ni parentesis con sentido`() {
        assertEquals("28 kanala", cleanChannelName("28 kanala"))
        assertEquals("Canal 24h (Noticias)", cleanChannelName("Canal 24h (Noticias)"))
    }

    @Test
    fun `si solo habia etiquetas devuelve el crudo`() {
        assertEquals("[Geo-blocked]", cleanChannelName("[Geo-blocked]"))
    }
}
