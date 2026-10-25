package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.LibraryKind
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.model.VideoRange
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.LibrarySource
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.Session
import fr.guim.cinedoury.core.source.SourceException
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Against the Jellyfin 12.3 of `ci/jellyfin` (bootstrap.sh and generate-media.sh). Skipped when
 * JELLYFIN_URL is not set.
 */
class JellyfinIntegrationTest {
    private val url = System.getenv("JELLYFIN_URL").orEmpty()
    private val username = System.getenv("JELLYFIN_ADMIN_USER") ?: "admin"
    private val password = System.getenv("JELLYFIN_ADMIN_PASSWORD") ?: "cinedoury-test"
    private val connector =
        JellyfinConnector(
            ClientIdentity(appVersion = "0.0.0-test", deviceId = "cine-doury-integration-test", deviceName = "CI"),
        )

    @BeforeTest
    fun requireServer() {
        assumeTrue("JELLYFIN_URL is not set", url.isNotBlank())
    }

    private val address get() = ServerAddress.of(url)

    private suspend fun signIn(): Session = connector.signIn(address, Credentials.Password(username, password))

    private suspend fun library(): LibrarySource = connector.open(signIn())

    @Test
    fun `reads the public server information`() =
        runTest {
            val info = connector.serverInfo(address)
            assertTrue(info.version.startsWith("12.3"), info.version)
        }

    @Test
    fun `signs in with credentials`() =
        runTest {
            val session = signIn()
            assertEquals(username, session.user.name)
            assertTrue(session.user.isAdministrator)
            assertTrue(session.accessToken.isNotBlank())
        }

    @Test
    fun `rejects a wrong password`() =
        runTest {
            assertFailsWith<SourceException.InvalidCredentials> {
                connector.signIn(address, Credentials.Password(username, "not-the-password"))
            }
        }

    @Test
    fun `rejects a forged token`() =
        runTest {
            val forged = signIn().copy(accessToken = "00000000000000000000000000000000")
            assertFailsWith<SourceException.Unauthorized> { connector.open(forged).libraries() }
        }

    @Test
    fun `lists the libraries`() =
        runTest {
            val libraries = library().libraries()
            val byKind = libraries.associateBy { it.kind }
            assertEquals("Films", byKind[LibraryKind.Movies]?.name)
            assertEquals("Séries", byKind[LibraryKind.Shows]?.name)
        }

    @Test
    fun `pages through the films, collection members included`() =
        runTest {
            val source = library()
            val films = source.libraries().single { it.kind == LibraryKind.Movies }

            val first = source.items(films.id, PageRequest(limit = 3))
            assertEquals(4, first.totalCount)
            assertEquals(3, first.items.size)
            val rest = source.items(films.id, PageRequest(startIndex = assertNotNull(first.nextStartIndex), limit = 3))
            val titles = (first.items + rest.items).map { it.title }
            assertEquals(listOf("Double jeu", "Le Phare", "Nuit polaire", "Retour au phare"), titles)
            assertTrue((first.items + rest.items).all { it.kind == ItemKind.Movie })
        }

    @Test
    fun `reads a film with its tracks`() =
        runTest {
            val source = library()
            val films = source.libraries().single { it.kind == LibraryKind.Movies }
            val summary = source.items(films.id).items.single { it.title == "Nuit polaire" }

            val film = source.item(summary.id)
            assertEquals(2020, film.year)
            assertEquals(listOf("Science-fiction", "Thriller"), film.genres)
            assertNotNull(film.images.primary)
            val media = film.mediaSources.single()
            assertEquals("hevc", media.video?.codec)
            assertEquals(VideoRange.Hdr10, media.video?.videoRange)
            assertEquals(listOf("eac3", "ac3", "aac"), media.audio.map { it.codec })
            assertEquals(listOf("fra", "eng", "jpn"), media.audio.map { it.language })
            assertEquals(1, media.subtitles.count { it.isForced })
            assertEquals(4, media.subtitles.size)
        }

    @Test
    fun `reads both versions of a film`() =
        runTest {
            val source = library()
            val films = source.libraries().single { it.kind == LibraryKind.Movies }
            val summary = source.items(films.id).items.single { it.title == "Double jeu" }

            val film = source.item(summary.id)
            assertEquals(2, film.mediaSources.size)
            assertEquals(setOf(1080, 720), film.mediaSources.mapNotNull { it.video?.height }.toSet())
        }

    @Test
    fun `reads the series`() =
        runTest {
            val source = library()
            val shows = source.libraries().single { it.kind == LibraryKind.Shows }
            val series = source.items(shows.id).items
            assertEquals(listOf("Les Marées", "Une saison"), series.map { it.title })
            assertTrue(series.all { it.kind == ItemKind.Series })
            assertEquals(2, series.first().childCount)
        }
}
