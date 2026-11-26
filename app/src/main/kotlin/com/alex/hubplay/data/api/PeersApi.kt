package com.alex.hubplay.data.api

import com.alex.hubplay.data.api.dto.PeerContinueResponse
import com.alex.hubplay.data.api.dto.PeerHitsResponse
import com.alex.hubplay.data.api.dto.PeerItemsResponse
import com.alex.hubplay.data.api.dto.PeerLibrariesResponse
import com.alex.hubplay.data.api.dto.PeerProgressRequest
import com.alex.hubplay.data.api.dto.PeerProgressResponse
import com.alex.hubplay.data.api.dto.PeerStreamSessionResponse
import com.alex.hubplay.data.api.dto.PeersResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Federación del lado del usuario (`/api/v1/me/peers/…`): lo que los
 * servidores amigos comparten con el nuestro. Todo pasa por NUESTRO
 * servidor, que firma con su JWT de peer; la app no ve nunca la URL del
 * amigo. Un amigo apagado da 502 `PEER_UNREACHABLE` en las rutas de un
 * solo amigo y simplemente no aporta resultados en las agregadas.
 */
interface PeersApi {

    /** Servidores emparejados. Vacío = no hay nada compartido (se oculta la sección). */
    @GET("me/peers")
    suspend fun listPeers(): PeersResponse

    /** Todas las bibliotecas compartidas, de todos los amigos, en una llamada. */
    @GET("me/peers/libraries")
    suspend fun listLibraries(): PeerLibrariesResponse

    /** Items de una biblioteca (de la caché del servidor si el amigo no responde). */
    @GET("me/peers/{peerId}/libraries/{libraryId}/items")
    suspend fun listItems(
        @Path("peerId")    peerId:    String,
        @Path("libraryId") libraryId: String,
        @Query("offset")   offset:    Int = 0,
        @Query("limit")    limit:     Int = PAGE_SIZE,
    ): PeerItemsResponse

    /** Lo último añadido en cada amigo ([perPeer] por amigo, máx. 50). */
    @GET("me/peers/recent")
    suspend fun recent(@Query("limit") perPeer: Int = RECENT_PER_PEER): PeerHitsResponse

    /** Búsqueda repartida entre amigos (2 s por amigo; los lentos no salen). */
    @GET("me/peers/search")
    suspend fun search(
        @Query("q")     query:   String,
        @Query("limit") perPeer: Int = SEARCH_PER_PEER,
    ): PeerHitsResponse

    /** "Seguir viendo" sobre items de amigos (progreso guardado en NUESTRO servidor). */
    @GET("me/peers/continue-watching")
    suspend fun continueWatching(@Query("limit") limit: Int = CONTINUE_LIMIT): PeerContinueResponse

    /**
     * Abre una sesión de streaming en el amigo con NUESTRAS capacidades
     * (el amigo decide direct/remux/transcode) y devuelve el master HLS
     * en nuestro servidor.
     */
    @POST("me/peers/{peerId}/stream/{itemId}/session")
    suspend fun startStream(
        @Path("peerId") peerId: String,
        @Path("itemId") itemId: String,
        @Header("X-Hubplay-Client-Capabilities") capabilities: String,
    ): PeerStreamSessionResponse

    @GET("me/peers/{peerId}/items/{itemId}/progress")
    suspend fun getProgress(
        @Path("peerId") peerId: String,
        @Path("itemId") itemId: String,
    ): PeerProgressResponse

    /** 204. `duration_ticks` 0 conserva la duración ya guardada. */
    @POST("me/peers/{peerId}/items/{itemId}/progress")
    suspend fun saveProgress(
        @Path("peerId") peerId: String,
        @Path("itemId") itemId: String,
        @Body body: PeerProgressRequest,
    )

    companion object {
        const val PAGE_SIZE = 60
        const val RECENT_PER_PEER = 12
        const val SEARCH_PER_PEER = 10
        const val CONTINUE_LIMIT = 20
    }
}
