package com.alex.hubplay.data

import com.alex.hubplay.data.api.HubplayApi
import com.alex.hubplay.data.api.dto.IdentifyRequest

/**
 * Herramientas de metadatos de la ficha (Detalle): permiso del usuario,
 * "Actualizar metadatos" e "Identificar" contra TMDb. Espejo del kebab de
 * la web (`useDetailMenu`). Lo consume [HomeRepositoryImpl] por delegación
 * para que la interfaz [HomeRepository] siga siendo el único punto de
 * entrada de los ViewModels (y de sus fakes en tests).
 */
class MetadataTools(
    private val api:        HubplayApi,
    private val tokenStore: TokenStore,
) {
    // Cache por (servidor, perfil): el permiso no cambia dentro de una
    // sesión y Detalle se abre muchas veces. Se invalida solo al cambiar
    // de perfil o de servidor.
    @Volatile private var canEditCache: Pair<String, Boolean>? = null

    suspend fun fetchCanEditMetadata(): Boolean {
        val auth = tokenStore.authStateFlow.value
        val key = "${auth.serverUrl}|${auth.activeProfileId}"
        canEditCache?.takeIf { it.first == key }?.let { return it.second }
        val me = api.getMe().data
        val can = me?.canEditMetadata ?: (me?.role == "admin")
        canEditCache = key to can
        return can
    }

    suspend fun refreshItemMetadata(itemId: String) {
        api.refreshItemMetadata(itemId)
    }

    suspend fun fetchIdentifyCandidates(itemId: String, query: String?, year: Int?): List<IdentifyCandidate> {
        val q = query?.trim()?.takeIf { it.isNotEmpty() }
        return api.identifyCandidates(itemId, q, year).data.orEmpty().mapNotNull { dto ->
            val id = dto.externalId ?: return@mapNotNull null
            IdentifyCandidate(
                externalId = id,
                title      = dto.title.orEmpty(),
                year       = dto.year,
                overview   = dto.overview.orEmpty(),
                posterUrl  = dto.posterUrl?.takeIf { it.isNotBlank() },
                score      = dto.score ?: 0.0,
            )
        }
    }

    suspend fun identifyItem(itemId: String, externalId: String) {
        api.identifyItem(itemId, IdentifyRequest(externalId = externalId))
    }
}
