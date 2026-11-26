package com.alex.hubplay.screenshots

import com.alex.hubplay.data.CollectionDetail
import com.alex.hubplay.data.CollectionSummary
import com.alex.hubplay.data.Content
import com.alex.hubplay.data.HomeRailConfig
import com.alex.hubplay.data.HomeRailType
import com.alex.hubplay.data.HomeRepository
import com.alex.hubplay.data.IdentifyCandidate
import com.alex.hubplay.data.PersonDetail
import com.alex.hubplay.data.StudioDetail
import com.alex.hubplay.screenshots.FakeArtwork.backdrop
import com.alex.hubplay.screenshots.FakeArtwork.logo
import com.alex.hubplay.screenshots.FakeArtwork.poster
import com.alex.hubplay.screenshots.FakeArtwork.still

/** Biblioteca inventada (títulos ficticios) para las capturas. */
object SampleCatalog {

    private const val LOREM =
        "Una cartógrafa descubre que los mapas de su abuelo describen una ciudad que " +
            "aún no existe. Para encontrarla tendrá que cruzar el desierto con un " +
            "contrabandista que no cree en nada y un perro que lo sabe todo."

    val episodeTitles = listOf(
        "La llave", "Marea baja", "El testamento", "Fuego cruzado", "Cartas sin remite",
        "La noche larga", "Puerto franco", "Lo que queda", "El regreso",
    )

    val cast = listOf(
        "Lucía Ferrer" to "Inés", "Marco Velasco" to "Tomás", "Ana Rojo" to "Clara",
        "Diego Salas" to "El Capitán", "Elena Prats" to "Marta", "Hugo Bernal" to "Río",
    ).mapIndexed { i, (name, character) ->
        com.alex.hubplay.data.Person(
            id = "p$i",
            name = name,
            role = "actor",
            character = character,
            imageUrl = FakeArtwork.avatar("p$i"),
        )
    }

    private fun movie(i: Int, title: String, year: Int, genres: List<String>, rating: Float, progress: Float = 0f) =
        Content.Movie(
            id = "m$i",
            title = title,
            posterUrl = poster("m$i", title),
            backdropUrl = backdrop("m$i"),
            logoUrl = logo(title),
            overview = LOREM,
            genres = genres,
            rating = rating,
            year = year,
            progressPct = progress,
            resumePosSec = (progress * 6300).toLong(),
            durationSec = 6300,
            trailerKey = null,
            people = cast,
            studioName = "Estudios Brisa",
            studioSlug = "estudios-brisa",
        )

    private fun series(i: Int, title: String, year: Int, genres: List<String>, rating: Float) =
        Content.Series(
            id = "s$i",
            title = title,
            posterUrl = poster("s$i", title),
            backdropUrl = backdrop("s$i"),
            logoUrl = logo(title),
            overview = LOREM,
            genres = genres,
            rating = rating,
            year = year,
            people = cast,
        )

    val movies = listOf(
        movie(1, "El Último Faro", 2024, listOf("Drama", "Misterio"), 7.8f),
        movie(2, "Arena Roja", 2023, listOf("Aventura", "Ciencia ficción"), 8.1f),
        movie(3, "Noches de Neón", 2022, listOf("Thriller"), 6.9f),
        movie(4, "La Ciudad Dormida", 2025, listOf("Fantasía"), 7.2f),
        movie(5, "Ecos del Norte", 2021, listOf("Drama"), 7.5f),
        movie(6, "Marea Alta", 2024, listOf("Acción"), 6.4f),
        movie(7, "Los Relojeros", 2020, listOf("Comedia"), 7.0f),
        movie(8, "Vértigo Azul", 2023, listOf("Thriller", "Crimen"), 7.9f),
        movie(9, "Jardín de Invierno", 2019, listOf("Romance"), 6.8f),
        movie(10, "Órbita Cero", 2025, listOf("Ciencia ficción"), 8.4f),
    )

    val shows = listOf(
        series(1, "Distrito Siete", 2023, listOf("Crimen", "Drama"), 8.3f),
        series(2, "Las Islas", 2024, listOf("Aventura"), 7.6f),
        series(3, "Código Ámbar", 2022, listOf("Thriller"), 7.9f),
        series(4, "Casa Valdés", 2021, listOf("Drama"), 8.0f),
        series(5, "Frontera", 2025, listOf("Western"), 7.1f),
        series(6, "Señales", 2024, listOf("Ciencia ficción"), 8.6f),
    )

    val continueWatching: List<Content.Resumable> = listOf(
        movies[1].copy(progressPct = 0.42f, resumePosSec = 2646),
        Content.Episode(
            id = "e1",
            title = "Distrito Siete",
            subtitle = "S2 · E4 · La llave",
            posterUrl = still("e1"),
            backdropUrl = backdrop("s1"),
            overview = LOREM,
            progressPct = 0.7f,
            resumePosSec = 2100,
            durationSec = 3000,
            seriesId = "s1",
            seasonNumber = 2,
            episodeNumber = 4,
        ),
        movies[7].copy(progressPct = 0.15f, resumePosSec = 945),
        Content.Episode(
            id = "e2",
            title = "Las Islas",
            subtitle = "S1 · E2 · Marea baja",
            posterUrl = still("e2"),
            backdropUrl = backdrop("s2"),
            overview = LOREM,
            progressPct = 0.3f,
            resumePosSec = 900,
            durationSec = 3000,
            seriesId = "s2",
            seasonNumber = 1,
            episodeNumber = 2,
        ),
    )

    /** Como /me/next-up: fotograma del episodio + logo de serie, sin sinopsis. */
    val nextUp: List<Content.Episode> = listOf(
        Content.Episode(
            id = "e3",
            title = "Código Ámbar",
            subtitle = "S3 · E1 · Fuego cruzado",
            posterUrl = still("e3"),
            backdropUrl = still("e3"),
            logoUrl = logo("Código Ámbar"),
            seriesId = "s3",
            seasonNumber = 3,
            episodeNumber = 1,
            durationSec = 2900,
        ),
        Content.Episode(
            id = "e4",
            title = "Casa Valdés",
            subtitle = "S1 · E6 · El testamento",
            posterUrl = still("e4"),
            backdropUrl = still("e4"),
            seriesId = "s4",
            seasonNumber = 1,
            episodeNumber = 6,
            durationSec = 3200,
        ),
    )

    val liveNow: List<Content.LiveChannel> = listOf(
        Content.LiveChannel(
            id = "c1", title = "Canal Uno", subtitle = "Telediario",
            logoInitials = "C1", logoBg = "#1E3A8A", logoFg = "#FFFFFF",
        ),
        Content.LiveChannel(
            id = "c2", title = "Deportes+", subtitle = "Fútbol: Liga",
            logoInitials = "D+", logoBg = "#065F46", logoFg = "#FFFFFF",
        ),
        Content.LiveChannel(
            id = "c3", title = "Cine Clásico", subtitle = "Ciclo western",
            logoInitials = "CC", logoBg = "#7C2D12", logoFg = "#FFFFFF",
        ),
        Content.LiveChannel(
            id = "c4", title = "Noticias 24", subtitle = "Última hora",
            logoInitials = "24", logoBg = "#991B1B", logoFg = "#FFFFFF",
        ),
    )

    val trending: List<Content> =
        listOf(movies[9], shows[5], movies[0], shows[0], movies[1], movies[3], shows[2], movies[7])

    val layout = listOf(
        HomeRailConfig("cw", HomeRailType.ContinueWatching, null, "Continuar viendo"),
        HomeRailConfig("nu", HomeRailType.NextUp, null, "A continuación"),
        HomeRailConfig("tr", HomeRailType.Trending, null, "Tendencias"),
        HomeRailConfig("ln", HomeRailType.LiveNow, null, "En directo"),
        HomeRailConfig("lm", HomeRailType.LatestInLibrary, "lib-movies", "Últimas películas"),
        HomeRailConfig("ls", HomeRailType.LatestInLibrary, "lib-shows", "Últimas series"),
    )
}

/** Repositorio en memoria con [SampleCatalog]. */
open class SampleHomeRepository : HomeRepository {
    override suspend fun fetchContinueWatching() = SampleCatalog.continueWatching
    override suspend fun fetchTrending(limit: Int) = SampleCatalog.trending.take(limit)
    override suspend fun fetchRecommended(limit: Int) = SampleCatalog.trending.take(limit)
    override suspend fun fetchLatest(libraryId: String?, type: String?, limit: Int): List<Content> =
        if (libraryId == "lib-shows" || type == "series") SampleCatalog.shows else SampleCatalog.movies
    override suspend fun fetchLibraries() = mapOf("lib-movies" to "Películas", "lib-shows" to "Series")
    override suspend fun fetchLiveNow(limit: Int) = SampleCatalog.liveNow
    override suspend fun fetchHomeLayout() = SampleCatalog.layout
    override suspend fun fetchChildren(parentId: String): List<Content> = when {
        parentId.startsWith("s") && !parentId.contains("-") -> (1..3).map { n ->
            Content.Season(
                id = "$parentId-season$n",
                title = "Temporada $n",
                posterUrl = poster("$parentId-season$n", "Temporada $n"),
                parentId = parentId,
                seasonNumber = n,
            )
        }
        parentId.contains("-season") -> {
            val season = parentId.substringAfter("-season").toInt()
            (1..8).map { e ->
                Content.Episode(
                    id = "$parentId-e$e",
                    title = SampleCatalog.episodeTitles[(e + season) % SampleCatalog.episodeTitles.size],
                    subtitle = "S$season · E$e",
                    posterUrl = still("$parentId-e$e"),
                    backdropUrl = still("$parentId-e$e"),
                    overview = "Un capítulo en el que todo cambia y nada vuelve a ser como antes.",
                    seasonNumber = season,
                    episodeNumber = e,
                    durationSec = 2700L + e * 60,
                    seriesId = parentId.substringBefore("-season"),
                    parentId = parentId,
                    progressPct = if (season == 1 && e <= 2) 1f else if (season == 1 && e == 3) 0.4f else 0f,
                    watched = season == 1 && e <= 2,
                )
            }
        }
        else -> emptyList()
    }
    override suspend fun fetchNextUp() = SampleCatalog.nextUp
    override suspend fun fetchCatalogue(type: String, limit: Int, offset: Int, sortBy: String, sortOrder: String): List<Content> =
        if (offset > 0) emptyList() else if (type == "series") SampleCatalog.shows else SampleCatalog.movies
    override suspend fun fetchCollections() = emptyList<CollectionSummary>()
    override suspend fun fetchCollectionDetail(id: String): CollectionDetail = error("sin colecciones")
    override suspend fun fetchItemDetail(itemId: String): Content =
        (SampleCatalog.movies + SampleCatalog.shows).first { it.id == itemId }
    override suspend fun fetchPerson(personId: String): PersonDetail = error("sin personas")
    override suspend fun fetchStudio(slug: String): StudioDetail = error("sin estudios")
    override suspend fun fetchRecommendations(itemId: String): List<Content> = SampleCatalog.trending
    override suspend fun toggleItemFavorite(itemId: String) = true
    override suspend fun setItemWatched(itemId: String, watched: Boolean) = Unit
    override suspend fun fetchCanEditMetadata() = false
    override suspend fun refreshItemMetadata(itemId: String) = Unit
    override suspend fun fetchIdentifyCandidates(itemId: String, query: String?, year: Int?) = emptyList<IdentifyCandidate>()
    override suspend fun identifyItem(itemId: String, externalId: String) = Unit
    override suspend fun searchItems(query: String, limit: Int, offset: Int): List<Content> = SampleCatalog.movies
}
