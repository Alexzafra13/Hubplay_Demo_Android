package com.alex.hubplay.data.api

import com.alex.hubplay.data.api.dto.LatestResponse
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NullToEmptyListAdapterFactoryTest {

    private val moshi = Moshi.Builder().add(NullToEmptyListAdapterFactory()).build()

    @Test
    fun `genres null del wire no tira la pagina de peliculas`() {
        // Réplica del wire real de producción: el 5º ítem sin metadatos
        // llegaba con "genres": null y Moshi rechazaba la lista entera.
        val json = """
            {"data":{"items":[
              {"id":"a","title":"Con géneros","type":"movie","genres":["Terror"]},
              {"id":"b","title":"Sin géneros","type":"movie","genres":null},
              {"id":"c","title":"Sin clave","type":"movie"}
            ],"limit":60,"offset":0,"total":3}}
        """.trimIndent()

        val parsed = moshi.adapter(LatestResponse::class.java).fromJson(json)
        assertNotNull(parsed)
        val items = parsed?.data?.items.orEmpty()
        assertEquals(3, items.size)
        assertEquals(listOf("Terror"), items[0].genres)
        assertEquals(emptyList<String>(), items[1].genres)
        assertEquals(emptyList<String>(), items[2].genres)
    }
}
