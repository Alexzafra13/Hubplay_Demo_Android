package com.alex.hubplay.data.api

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.Type

/**
 * Trata un `null` explícito del wire como lista vacía para cualquier
 * `List<T>` del contrato.
 *
 * Motivo: el backend serializa slices Go sin inicializar como `null`
 * (`"genres": null` en películas sin metadatos). Los DTOs declaran
 * `List<String> = emptyList()`, pero el default de Kotlin solo aplica
 * cuando la clave FALTA; con `null` explícito Moshi lanza
 * `JsonDataException: Non-null value 'genres' was null` y tiraba la
 * página entera de Películas por un solo ítem sin géneros.
 *
 * Se registra en el `Moshi.Builder` de [com.alex.hubplay.data.AppContainer]
 * y cubre todos los `List<…>` de golpe, sin tocar DTO a DTO. Un `List<T>?`
 * (nullable) sigue recibiendo `null` como hasta ahora.
 */
class NullToEmptyListAdapterFactory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (annotations.isNotEmpty() || Types.getRawType(type) != List::class.java) return null
        val delegate = moshi.nextAdapter<List<Any?>>(this, type, annotations)
        return object : JsonAdapter<List<Any?>>() {
            override fun fromJson(reader: JsonReader): List<Any?> {
                if (reader.peek() == JsonReader.Token.NULL) {
                    reader.nextNull<Unit>()
                    return emptyList()
                }
                return delegate.fromJson(reader) ?: emptyList()
            }

            override fun toJson(writer: JsonWriter, value: List<Any?>?) {
                delegate.toJson(writer, value)
            }
        }
    }
}
