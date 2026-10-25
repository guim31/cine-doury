package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.model.SearchHit
import fr.guim.cinedoury.core.model.SearchMatcher
import fr.guim.cinedoury.core.model.SearchQuery
import fr.guim.cinedoury.core.model.SearchRanking
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.LibrarySource
import fr.guim.cinedoury.core.source.ServerAddress
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The search of J5 against the Jellyfin 12.3 of `ci/jellyfin` (`SPEC.md` § 4.12): the titles of the
 * local index with the matcher of the app (accents, typos), and what only the server finds (people,
 * collections, episodes). Skipped when JELLYFIN_URL is not set.
 */
class JellyfinSearchIntegrationTest {
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

    /** Every title of the index, as the app reads them. */
    private suspend fun LibrarySource.allTitles(): List<MediaItem> =
        libraries().flatMap { library ->
            runCatching {
                titles(
                    library.id,
                    changedSince = null,
                    PageRequest(limit = 500),
                ).items
            }.getOrDefault(emptyList())
        }

    /** What the local index finds for [typed], the best first. */
    private fun List<MediaItem>.search(typed: String): List<String> {
        val query = SearchQuery.of(typed)
        val hits =
            mapNotNull { item ->
                SearchMatcher.best(query, SearchRanking.titles(item))?.let { SearchHit(item, it) }
            }
        return SearchRanking.rows(hits).flatMap { row -> row.items.map { it.title } }
    }

    @Test
    fun `the titles of the index, films and series, with their sort titles and posters`() =
        runTest {
            val titles = source().allTitles()
            assertTrue(titles.any { it.kind == ItemKind.Series && it.title == "Les Marées" }, titles.toString())
            val film = titles.first { it.title == "L'Éclipse" }
            assertEquals(ItemKind.Movie, film.kind)
            assertTrue(!film.sortTitle.isNullOrBlank())
            assertTrue(film.images.primary != null)
            // The films of a collection one by one.
            assertTrue(
                titles.count { it.title.startsWith("Les Étoiles filantes") } == 3,
                titles.map { it.title }.toString(),
            )
        }

    @Test
    fun `accented titles without their accents, and with typos`() =
        runTest {
            val titles = source().allTitles()
            assertEquals("Échappée belle", titles.search("echappee").first())
            assertEquals("L'Éclipse", titles.search("eclipse").first())
            assertEquals("L'Éclipse", titles.search("eclipce").first())
            assertEquals("Zéphyr", titles.search("zephir").first())
            assertTrue("Les Marées" in titles.search("marees"))
            assertTrue("Fréquences" in titles.search("frequ"))
        }

    @Test
    fun `the server finds people without their accents`() =
        runTest {
            val people = source().search("ines", setOf(ItemKind.Person), limit = 10)
            assertEquals(listOf("Inès Garnier"), people.map { it.title })
            assertEquals(ItemKind.Person, people.single().kind)
            val lefevre = source().search("lefevre", setOf(ItemKind.Person), limit = 10)
            assertEquals(listOf("Hugo Lefèvre"), lefevre.map { it.title })
        }

    @Test
    fun `the server finds collections and episodes`() =
        runTest {
            val source = source()
            val collections = source.search("phare", setOf(ItemKind.Collection), limit = 10)
            assertEquals(listOf("Le Phare : la saga"), collections.map { it.title })
            assertEquals(ItemKind.Collection, collections.single().kind)
            val episodes = source.search("maree", setOf(ItemKind.Episode), limit = 10)
            val episode = episodes.single()
            assertEquals("Grande marée", episode.title)
            assertEquals("Les Marées", episode.episode?.seriesTitle)
            assertEquals("Le dégel", source.search("degel", setOf(ItemKind.Episode), limit = 10).single().title)
        }

    @Test
    fun `the server search keeps to the kinds asked for, with the cards`() =
        runTest {
            val found = source().search("phare", setOf(ItemKind.Movie, ItemKind.Series), limit = 20)
            assertTrue(found.isNotEmpty())
            assertTrue(found.all { it.kind == ItemKind.Movie || it.kind == ItemKind.Series }, found.toString())
            assertTrue(found.any { it.title == "Le Phare" && it.images.primary != null && it.userData != null })
        }
}
