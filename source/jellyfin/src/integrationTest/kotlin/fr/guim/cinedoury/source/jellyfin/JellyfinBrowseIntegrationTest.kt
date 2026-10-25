package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.model.ItemFilters
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.ItemQuery
import fr.guim.cinedoury.core.model.ItemSort
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.model.Resolution
import fr.guim.cinedoury.core.model.SortField
import fr.guim.cinedoury.core.model.WatchStatus
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.LibrarySource
import fr.guim.cinedoury.core.source.ServerAddress
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The home rows and the library browsing of J2 against the Jellyfin 12.3 of `ci/jellyfin`: the 45
 * films of generate-media.sh, the series, the « Spectacles » library excluded from the home, and the
 * watch history set by bootstrap.sh. Skipped when JELLYFIN_URL is not set.
 */
class JellyfinBrowseIntegrationTest {
    private val url = System.getenv("JELLYFIN_URL").orEmpty()
    private val connector =
        JellyfinConnector(
            ClientIdentity(appVersion = "0.0.0-test", deviceId = "cine-doury-integration-test", deviceName = "CI"),
        )

    @BeforeTest
    fun requireServer() {
        assumeTrue("JELLYFIN_URL is not set", url.isNotBlank())
    }

    private suspend fun source(): LibrarySource {
        val username = System.getenv("JELLYFIN_ADMIN_USER") ?: "admin"
        val password = System.getenv("JELLYFIN_ADMIN_PASSWORD") ?: "cinedoury-test"
        return connector.open(connector.signIn(ServerAddress.of(url), Credentials.Password(username, password)))
    }

    private suspend fun LibrarySource.library(name: String): Library = libraries().single { it.name == name }

    private suspend fun LibrarySource.films(
        filters: ItemFilters = ItemFilters(),
        sort: ItemSort = ItemSort(),
    ): List<String> {
        val query = ItemQuery(library("Films").id, setOf(ItemKind.Movie), sort, filters)
        return items(query, PageRequest(limit = 100)).items.map { it.title }
    }

    @Test
    fun `lists every library in the order of the user, hidden ones included`() =
        runTest {
            val names = source().libraries().map { it.name }
            assertEquals(listOf("Séries", "Films", "Spectacles", "Collections"), names)
        }

    @Test
    fun `reads the libraries left out of the home`() =
        runTest {
            val source = source()
            val shows = source.library("Spectacles").id
            val exclusions = source.homeExclusions()
            assertEquals(setOf(shows), exclusions.fromHome)
            assertEquals(setOf(shows), exclusions.fromLatest)
        }

    @Test
    fun `resume lists the started films and episodes, last played first`() =
        runTest {
            val source = source()
            val all = source.resume(library = null, limit = 10)
            assertEquals(listOf("Fréquences", "Mortes-eaux", "Marée basse"), all.map { it.title })
            val episode = all[1]
            assertEquals(ItemKind.Episode, episode.kind)
            assertEquals("Les Marées", episode.episode?.seriesTitle)
            assertEquals(2, episode.episode?.episodeNumber)
            all.forEach { item ->
                val progress = assertNotNull(item.progress, item.title)
                assertTrue(progress > 0f && progress < 1f, "${item.title}: $progress")
                assertNotNull(item.userData?.lastPlayed)
            }
            val films = source.resume(source.library("Films").id, limit = 10)
            assertEquals(listOf("Fréquences", "Marée basse"), films.map { it.title })
        }

    @Test
    fun `next up gives the next episode of each series, within the window`() =
        runTest {
            val source = source()
            val all = source.nextUp(library = null, since = null, limit = 10)
            assertEquals(listOf("Été", "Le refuge"), all.map { it.title })
            assertEquals(listOf("Une saison", "Brume"), all.map { it.episode?.seriesTitle })
            val twoWeeks = Instant.now().minus(14, ChronoUnit.DAYS)
            assertEquals(listOf("Été"), source.nextUp(source.library("Séries").id, twoWeeks, 10).map { it.title })
        }

    @Test
    fun `latest additions of a library, played films hidden as the user asked`() =
        runTest {
            val source = source()
            val films = source.latest(source.library("Films").id, limit = 6).map { it.title }
            assertTrue("Rivages" in films, films.toString())
            assertTrue("Le Phare" !in films, films.toString())
            val shows = source.latest(source.library("Séries").id, limit = 6)
            assertTrue(shows.isNotEmpty() && shows.all { it.kind == ItemKind.Series }, shows.toString())
        }

    @Test
    fun `sorts the films`() =
        runTest {
            val source = source()

            fun sort(field: SortField) = ItemSort(field)
            assertEquals("L'Odyssée des sables", source.films(sort = sort(SortField.Runtime)).first())
            assertEquals("L'Odyssée des sables", source.films(sort = sort(SortField.Rating)).first())
            assertEquals("Rivages", source.films(sort = sort(SortField.ReleaseDate)).first())
            assertEquals(
                "L'Odyssée des sables",
                source.films(sort = ItemSort(SortField.DateAdded, ascending = true)).first(),
            )
            assertEquals("Le Cerf-volant", source.films(sort = sort(SortField.PlayCount)).first())
            assertEquals("Fréquences", source.films(sort = sort(SortField.LastPlayed)).first())
            assertEquals("Zone blanche", source.films(sort = ItemSort(SortField.Title, ascending = false)).first())
            assertEquals(FILMS, source.films(sort = sort(SortField.Random)).size)
        }

    @Test
    fun `filters the films on the server`() =
        runTest {
            val source = source()

            suspend fun count(filters: ItemFilters) = source.films(filters).size
            assertEquals(
                listOf("Bleu acier", "Givre", "Le Cerf-volant", "Le Phare", "Orage"),
                source.films(ItemFilters(watch = WatchStatus.Watched)),
            )
            assertEquals(listOf("Fréquences", "Marée basse"), source.films(ItemFilters(watch = WatchStatus.InProgress)))
            assertEquals(FILMS - 5, count(ItemFilters(watch = WatchStatus.Unwatched)))
            assertEquals(listOf("L'Odyssée des sables", "Zéphyr"), source.films(ItemFilters(favorite = true)))
            assertEquals(8, count(ItemFilters(genres = setOf("Comédie"))))
            assertEquals(21, count(ItemFilters(genres = setOf("Comédie", "Drame"))))
            assertEquals(7, count(ItemFilters(decades = setOf(1980))))
            assertEquals(listOf("L'Odyssée des sables"), source.films(ItemFilters(minRating = 8)))
            assertEquals(
                listOf("Fréquences", "Rivages", "Zéphyr"),
                source.films(ItemFilters(resolution = Resolution.Uhd)),
            )
            assertEquals(11, count(ItemFilters(resolution = Resolution.FullHd)))
            assertEquals(15, count(ItemFilters(resolution = Resolution.Sd)))
            assertEquals(7, count(ItemFilters(audioLanguages = setOf("eng"))))
            assertEquals(8, count(ItemFilters(subtitleLanguages = setOf("fra"))))
            assertEquals(5, count(ItemFilters(officialRatings = setOf("-16"))))
            assertEquals(8, count(ItemFilters(tags = setOf("Classique"))))
            assertEquals(
                listOf("Jours tranquilles", "Week-end à la mer"),
                source.films(
                    ItemFilters(watch = WatchStatus.Unwatched, genres = setOf("Comédie"), decades = setOf(1950, 1960)),
                ),
            )
        }

    @Test
    fun `filter values come from the server, and filter on studio, person and collection`() =
        runTest {
            val source = source()
            val films = source.library("Films").id
            val options = source.filterOptions(films, setOf(ItemKind.Movie))
            assertEquals(11, options.genres.size)
            assertEquals(listOf("eng", "fra", "jpn"), options.audioLanguages)
            assertEquals(listOf("eng", "fra"), options.subtitleLanguages)
            assertEquals(listOf("-10", "-12", "-16", "Tous publics"), options.officialRatings.sorted())
            assertEquals(listOf("Classique", "Coup de cœur", "Noir et blanc"), options.tags)
            assertEquals(listOf(1950, 1960, 1970, 1980, 1990, 2000, 2010, 2020), options.decades)
            assertEquals(4, options.studios.size)
            assertTrue(options.people.any { it.name == "Paul Mercier" }, options.people.toString())
            assertEquals(
                listOf("Le Phare : la saga", "Les Étoiles filantes : la trilogie"),
                options.collections.map { it.name },
            )

            val studio = options.studios.single { it.name == "Films du Phare" }
            assertEquals(9, source.films(ItemFilters(studios = setOf(studio))).size)
            val director = options.people.single { it.name == "Paul Mercier" }
            assertEquals(21, source.films(ItemFilters(people = setOf(director))).size)
            val saga = options.collections.single { it.name.startsWith("Les Étoiles") }
            assertEquals(
                listOf("Les Étoiles filantes", "Les Étoiles filantes 2", "Les Étoiles filantes 3"),
                source.films(ItemFilters(collection = saga)),
            )
        }

    @Test
    fun `pages through the films without gap nor duplicate`() =
        runTest {
            val source = source()
            val query = ItemQuery(source.library("Films").id, setOf(ItemKind.Movie))
            val pages = (0 until FILMS step PAGE).map { start -> source.items(query, PageRequest(start, PAGE)) }
            assertEquals(listOf(PAGE, PAGE, FILMS - 2 * PAGE), pages.map { it.items.size })
            assertTrue(pages.all { it.totalCount == FILMS })
            val ids = pages.flatMap { page -> page.items.map { it.id } }
            assertEquals(FILMS, ids.toSet().size)
            assertEquals(null, pages.last().nextStartIndex)
        }

    @Test
    fun `the A to Z jump counts the titles before a letter`() =
        runTest {
            val source = source()
            val query = ItemQuery(source.library("Films").id, setOf(ItemKind.Movie))
            val index = source.count(query.copy(titleBelow = "M"))
            assertEquals(28, index)
            assertEquals(
                "Marée basse",
                source
                    .items(query, PageRequest(index, 1))
                    .items
                    .single()
                    .title,
            )
            // « 2 Soleils » sorts as « 0000000002 soleils »: the titles with a digit come before A (« # »).
            assertEquals(1, source.count(query.copy(titleBelow = "A")))
        }

    @Test
    fun `groups the films of a collection, and lists the collections`() =
        runTest {
            val source = source()
            val films = source.library("Films").id
            val grouped =
                source.items(
                    ItemQuery(films, setOf(ItemKind.Movie), groupCollections = true),
                    PageRequest(limit = 100),
                )
            assertEquals(FILMS - 5 + 2, grouped.totalCount)
            assertEquals(2, grouped.items.count { it.kind == ItemKind.Collection })
            val collections = source.items(ItemQuery(films, setOf(ItemKind.Collection)), PageRequest())
            assertEquals(2, collections.totalCount)
        }

    @Test
    fun `recently released films, nothing after a date`() =
        runTest {
            val source = source()
            val query =
                ItemQuery(
                    source.library("Films").id,
                    setOf(ItemKind.Movie),
                    ItemSort(SortField.ReleaseDate),
                    releasedBefore = LocalDate.of(2023, 1, 1),
                )
            assertEquals(
                "Zéphyr",
                source
                    .items(query, PageRequest(limit = 3))
                    .items
                    .first()
                    .title,
            )
        }

    @Test
    fun `a series carries its unwatched episodes`() =
        runTest {
            val source = source()
            val series =
                source
                    .items(
                        ItemQuery(source.library("Séries").id, setOf(ItemKind.Series)),
                        PageRequest(),
                    ).items
            assertEquals(3, series.single { it.title == "Les Marées" }.unwatchedEpisodes)
            assertEquals(2, series.single { it.title == "Brume" }.unwatchedEpisodes)
        }

    private companion object {
        const val FILMS = 45
        const val PAGE = 20
    }
}
