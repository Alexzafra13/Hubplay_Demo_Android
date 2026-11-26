package com.alex.hubplay.data

import androidx.compose.runtime.Immutable
import com.alex.hubplay.data.api.PeersApi
import com.alex.hubplay.data.api.dto.MediaStreamDto
import com.alex.hubplay.data.api.dto.PeerContinueDto
import com.alex.hubplay.data.api.dto.PeerItemDto
import com.alex.hubplay.data.api.dto.PeerLibraryDto
import com.alex.hubplay.data.api.dto.PeerProgressRequest

/**
 * "Compartido contigo": catálogos de servidores amigos (federación) vistos
 * a través de NUESTRO servidor. Traduce los DTO de `/me/peers/…` a
 * [Content.Shared] para que rails, tarjetas y ficha los pinten como al
 * resto del catálogo.
 */
class PeersRepository(
    private val api:       PeersApi,
    /** URL del servidor emparejado (sin `/` final); lambda para los tests. */
    private val serverUrl: suspend () -> String,
) {
    constructor(api: PeersApi, tokenStore: TokenStore) :
        this(api, { tokenStore.snapshot().serverUrl?.trimEnd('/').orEmpty() })

    /**
     * Últimos items vistos en listados (LRU). La ficha y el reproductor los
     * leen para pintar título y póster al instante: el servidor no tiene un
     * "GET item" de un amigo, solo listados.
     */
    private val recentItems = object : LinkedHashMap<String, Content.Shared>(CACHE_SIZE, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Content.Shared>?) =
            size > CACHE_SIZE
    }

    /** Item de un amigo ya visto en algún listado, si sigue en la caché. */
    fun cached(peerId: String, itemId: String): Content.Shared? =
        synchronized(recentItems) { recentItems["$peerId:$itemId"] }

    /**
     * El item, de la caché o buscándolo en la primera página de su
     * biblioteca (tras reabrir la app en la ficha). Null si ya no está.
     */
    suspend fun find(peerId: String, libraryId: String?, itemId: String): Content.Shared? {
        cached(peerId, itemId)?.let { return it }
        val lib = libraryId ?: return null
        val server = serverUrl()
        val peerName = api.listPeers().data.orEmpty().firstOrNull { it.id == peerId }?.name.orEmpty()
        return api.listItems(peerId, lib).data?.items.orEmpty()
            .map { it.toShared(server, peerId = peerId, peerName = peerName, libraryId = lib) }
            .let(::remember)
            .firstOrNull { it.id == itemId }
    }

    private fun remember(items: List<Content.Shared>): List<Content.Shared> {
        synchronized(recentItems) { items.forEach { recentItems[it.key] = it } }
        return items
    }

    /** ¿Hay algún amigo emparejado? Decide si se enseña la sección. */
    suspend fun hasPeers(): Boolean = api.listPeers().data.orEmpty().isNotEmpty()

    /** Bibliotecas compartidas de todos los amigos, en el orden del servidor. */
    suspend fun libraries(): List<SharedLibrary> =
        api.listLibraries().data.orEmpty().map { it.toDomain() }

    /** Una página de una biblioteca compartida. */
    suspend fun libraryItems(library: SharedLibrary, offset: Int = 0, limit: Int = PeersApi.PAGE_SIZE): SharedPage {
        val server = serverUrl()
        val page = api.listItems(library.peerId, library.libraryId, offset, limit).data
        val items = page?.items.orEmpty().map {
            it.toShared(server, peerId = library.peerId, peerName = library.peerName, libraryId = library.libraryId)
        }
        remember(items)
        return SharedPage(items = items, total = page?.total ?: items.size, fromCache = page?.fromCache == true)
    }

    /** Lo último que han añadido los amigos (rail de Inicio y cabecera de la sección). */
    suspend fun recent(): List<Content.Shared> {
        val server = serverUrl()
        return remember(api.recent().data?.hits.orEmpty().mapNotNull { it.toSharedHit(server) }.distinctBy { it.key })
    }

    /** Búsqueda en los catálogos de los amigos. */
    suspend fun search(query: String): List<Content.Shared> {
        if (query.isBlank()) return emptyList()
        val server = serverUrl()
        return remember(api.search(query).data?.hits.orEmpty().mapNotNull { it.toSharedHit(server) }.distinctBy { it.key })
    }

    /** Items de amigos a medio ver, el más reciente primero. */
    suspend fun continueWatching(): List<Content.Shared> {
        val server = serverUrl()
        return remember(api.continueWatching().data.orEmpty().map { it.toShared(server) }.distinctBy { it.key })
    }

    /** Posición guardada para un item de un amigo (null = nada que reanudar). */
    suspend fun resumePosition(peerId: String, itemId: String): SharedProgress {
        val p = api.getProgress(peerId, itemId).data ?: return SharedProgress()
        return SharedProgress(
            positionSec = p.positionTicks / TICKS_PER_SECOND,
            durationSec = p.durationTicks / TICKS_PER_SECOND,
            completed   = p.completed,
        )
    }

    /**
     * Abre la sesión de streaming en el amigo y devuelve la ruta del master
     * HLS en nuestro servidor. Null si el amigo no da URL.
     */
    suspend fun startStream(peerId: String, itemId: String, capabilities: String): SharedStream? {
        val data = api.startStream(peerId, itemId, capabilities).data ?: return null
        val master = data.masterPlaylistUrl?.takeIf { it.isNotBlank() } ?: return null
        return SharedStream(masterPath = master, method = data.strategy, sessionId = data.peerSessionId)
    }

    /**
     * Subtítulos de la sesión con la forma de `media_streams` (la del
     * selector de pistas). Vacío si la sesión no tiene id o el amigo falla.
     */
    suspend fun subtitles(peerId: String, stream: SharedStream): List<MediaStreamDto> {
        val sid = stream.sessionId ?: return emptyList()
        return api.subtitles(peerId, sid).data.orEmpty().map {
            MediaStreamDto(
                streamIndex = it.index,
                streamType  = "subtitle",
                codec       = it.codec,
                isDefault   = it.default,
                isForced    = it.forced,
                language    = it.language,
                title       = it.title,
            )
        }
    }

    /** Guardado de progreso para el [ProgressReporter] del reproductor. */
    fun progressSink(peerId: String, itemId: String): ProgressSink = PeerProgressSink(api, peerId, itemId)

    // ─── DTO → dominio ───────────────────────────────────────────────────────

    private fun PeerLibraryDto.toDomain() = SharedLibrary(
        peerId      = peerId,
        peerName    = peerName.orEmpty(),
        libraryId   = libraryId,
        libraryName = libraryName.orEmpty(),
        contentType = contentType,
        canPlay     = canPlay,
    )

    private fun PeerItemDto.toSharedHit(server: String): Content.Shared? {
        val peer = peerId ?: return null
        return toShared(server, peerId = peer, peerName = peerName.orEmpty(), libraryId = libraryId)
    }

    private fun PeerItemDto.toShared(server: String, peerId: String, peerName: String, libraryId: String?) =
        Content.Shared(
            id          = id,
            title       = title.orEmpty(),
            subtitle    = peerName.takeIf { it.isNotBlank() },
            posterUrl   = sharedImage(posterUrl, server, IMG_W_SHARED_CARD),
            backdropUrl = sharedImage(posterUrl, server, IMG_W_SHARED_BACKDROP),
            overview    = overview,
            year        = year?.takeIf { it > 0 },
            peerId      = peerId,
            peerName    = peerName,
            libraryId   = libraryId,
            remoteKind  = MediaKind.from(type),
        )

    private fun PeerContinueDto.toShared(server: String): Content.Shared {
        val positionSec = positionTicks / TICKS_PER_SECOND
        val durationSec = durationTicks / TICKS_PER_SECOND
        return Content.Shared(
            id           = id,
            title        = title.orEmpty(),
            subtitle     = peerName?.takeIf { it.isNotBlank() },
            posterUrl    = sharedImage(posterUrl, server, IMG_W_SHARED_CARD),
            backdropUrl  = sharedImage(posterUrl, server, IMG_W_SHARED_BACKDROP),
            overview     = overview,
            year         = year?.takeIf { it > 0 },
            progressPct  = (percentage / PERCENT).toFloat().coerceIn(0f, 1f),
            resumePosSec = positionSec,
            durationSec  = durationSec,
            peerId       = peerId,
            peerName     = peerName.orEmpty(),
            libraryId    = libraryId,
            remoteKind   = MediaKind.from(type),
        )
    }

    companion object {
        /** Carátula de tarjeta (las de los rails miden ~120 dp de ancho). */
        const val IMG_W_SHARED_CARD = 400

        /** El póster hace de fondo en el hero: más ancho. */
        const val IMG_W_SHARED_BACKDROP = 1000

        private const val PERCENT = 100.0
        private const val CACHE_SIZE = 256
        private const val LOAD_FACTOR = 0.75f

        /**
         * Ruta del proxy de pósters de nuestro servidor → URL absoluta con
         * `?w=`. Un servidor que aún no reenvía `w` al amigo lo ignora y
         * sirve el póster original (más pesado, pero funciona).
         */
        fun sharedImage(path: String?, server: String, width: Int): String? {
            if (path.isNullOrBlank()) return null
            if ("://" in path) return path
            if (server.isBlank()) return null
            val clean = if (path.startsWith("/")) path else "/$path"
            val sep = if ('?' in clean) '&' else '?'
            return "$server$clean${sep}w=$width"
        }
    }
}

/** Progreso en `/me/peers/{peer}/items/{item}/progress` (posición + duración en ticks). */
private class PeerProgressSink(
    private val api:    PeersApi,
    private val peerId: String,
    private val itemId: String,
) : ProgressSink {
    override val label: String get() = "$peerId:$itemId"

    override suspend fun write(positionSec: Long, durationSec: Long, completed: Boolean) {
        api.saveProgress(
            peerId = peerId,
            itemId = itemId,
            body   = PeerProgressRequest(
                positionTicks = positionSec * TICKS_PER_SECOND,
                durationTicks = durationSec * TICKS_PER_SECOND,
                completed     = completed,
            ),
        )
    }

    /** No hay "visto" para items de amigos: se guarda completado al final. */
    override suspend fun markPlayed(durationSec: Long) = write(durationSec, durationSec, completed = true)
}

/** Biblioteca que un amigo comparte con nuestro servidor. */
@Immutable
data class SharedLibrary(
    val peerId:      String,
    val peerName:    String,
    val libraryId:   String,
    val libraryName: String,
    val contentType: String?,
    val canPlay:     Boolean,
) {
    val key: String get() = "$peerId:$libraryId"
}

@Immutable
data class SharedPage(
    val items:     List<Content.Shared>,
    val total:     Int,
    /** El amigo no respondió y el servidor sirve lo último que guardó. */
    val fromCache: Boolean,
)

@Immutable
data class SharedProgress(
    val positionSec: Long = 0L,
    val durationSec: Long = 0L,
    val completed:   Boolean = false,
) {
    /**
     * Segundo desde el que reanudar: 0 si no hay posición, si se terminó o
     * si pasó del 90 % (lo mismo que la web).
     */
    val resumeSec: Long
        get() {
            if (completed || positionSec <= 0) return 0L
            if (durationSec > 0 && positionSec * RESUME_CUTOFF_DEN >= durationSec * RESUME_CUTOFF_NUM) return 0L
            return positionSec
        }

    private companion object {
        const val RESUME_CUTOFF_NUM = 9
        const val RESUME_CUTOFF_DEN = 10
    }
}

/** Sesión de streaming abierta en el amigo. */
@Immutable
data class SharedStream(
    /** Ruta del master HLS en NUESTRO servidor. */
    val masterPath: String,
    /** direct_play | direct_stream | transcode según decidió el amigo. */
    val method:     String?,
    /** Id de la sesión en el amigo (rutas de subtítulos). */
    val sessionId:  String? = null,
) {
    /** Base de los WebVTT de esta sesión: `…/session/{sid}/subtitles`. */
    val subtitlesPath: String
        get() = masterPath.substringBeforeLast('/') + "/subtitles"
}
