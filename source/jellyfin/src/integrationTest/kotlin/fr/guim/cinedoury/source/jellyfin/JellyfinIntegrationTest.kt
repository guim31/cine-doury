package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.LibraryKind
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.model.VideoRange
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.LibrarySource
import fr.guim.cinedoury.core.source.QuickConnectRequest
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.Session
import fr.guim.cinedoury.core.source.SourceException
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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
    private val child = System.getenv("JELLYFIN_CHILD_USER") ?: "enfant"
    private val http = HttpClient.newHttpClient()
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
            val kinds = libraries.associate { it.name to it.kind }
            assertEquals(LibraryKind.Movies, kinds["Films"])
            assertEquals(LibraryKind.Shows, kinds["Séries"])
        }

    @Test
    fun `pages through the films, collection members included`() =
        runTest {
            val source = library()
            val films = source.films()

            val first = source.items(films.id, PageRequest(limit = 3))
            assertEquals(FILMS, first.totalCount)
            assertEquals(listOf("2 Soleils", "Après la pluie", "Bleu acier"), first.items.map { it.title })
            val second =
                source.items(
                    films.id,
                    PageRequest(startIndex = assertNotNull(first.nextStartIndex), limit = 3),
                )
            assertEquals(listOf("Chambre noire", "Double jeu", "Échappée belle"), second.items.map { it.title })
            assertTrue((first.items + second.items).all { it.kind == ItemKind.Movie })
        }

    @Test
    fun `reads a film with its tracks`() =
        runTest {
            val source = library()
            val films = source.films()
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
            val films = source.films()
            val summary = source.items(films.id).items.single { it.title == "Double jeu" }

            val film = source.item(summary.id)
            assertEquals(2, film.mediaSources.size)
            assertEquals(setOf(1080, 720), film.mediaSources.mapNotNull { it.video?.height }.toSet())
        }

    @Test
    fun `reads the series`() =
        runTest {
            val source = library()
            val shows = source.libraries().single { it.name == "Séries" }
            val series = source.items(shows.id).items
            assertEquals(listOf("Brume", "Les Marées", "Une saison"), series.map { it.title })
            assertTrue(series.all { it.kind == ItemKind.Series })
            assertEquals(2, series.single { it.title == "Les Marées" }.childCount)
        }

    @Test
    fun `lists the public users, the administrator with a picture`() =
        runTest {
            val users = connector.publicUsers(address)
            assertEquals(setOf(username, child), users.map { it.name }.toSet())
            val admin = users.single { it.name == username }
            val picture = assertNotNull(connector.userImageUrl(address, admin))
            assertEquals(200, get(picture).statusCode())
            assertEquals(null, connector.userImageUrl(address, users.single { it.name == child }))
        }

    @Test
    fun `signs in with Quick Connect, approved by the administrator as the phone would`() =
        runTest {
            assertTrue(connector.quickConnectEnabled(address))
            val request = connector.startQuickConnect(address)
            assertEquals(6, request.code.length)
            assertEquals(false, connector.isQuickConnectApproved(address, request))

            // The phone: the administrator approves the code for the child profile.
            val admin = signIn()
            val childId =
                connector
                    .publicUsers(address)
                    .single { it.name == child }
                    .id.value
            val approval =
                post(
                    "/QuickConnect/Authorize?code=${request.code}&userId=$childId",
                    token = admin.accessToken,
                )
            assertEquals(200, approval.statusCode(), approval.body())

            assertTrue(connector.isQuickConnectApproved(address, request))
            val session = connector.signIn(address, Credentials.QuickConnect(request))
            assertEquals(child, session.user.name)
            assertTrue(connector.open(session).libraries().isNotEmpty())
        }

    @Test
    fun `an unknown Quick Connect request has expired`() =
        runTest {
            assertFailsWith<SourceException.QuickConnectExpired> {
                connector.isQuickConnectApproved(address, QuickConnectRequest("000000", "0".repeat(64)))
            }
        }

    @Test
    fun `the phone approves the Quick Connect code of a TV`() =
        runTest {
            // « Connecter un appareil » (M1): the TV, another device, asks for a code; the phone,
            // signed in as the administrator, approves it; the TV signs in as the administrator.
            val tv =
                JellyfinConnector(
                    ClientIdentity(
                        appVersion = "0.0.0-test",
                        deviceId = "cine-doury-integration-tv",
                        deviceName = "TV",
                    ),
                )
            val request = tv.startQuickConnect(address)
            assertEquals(false, tv.isQuickConnectApproved(address, request))
            val phone = library()
            phone.authorizeQuickConnect(request.code)
            assertTrue(tv.isQuickConnectApproved(address, request))
            val session = tv.signIn(address, Credentials.QuickConnect(request))
            assertEquals(username, session.user.name)
            tv.signOut(session)
        }

    @Test
    fun `an unknown Quick Connect code cannot be approved`() =
        runTest {
            assertFailsWith<SourceException.QuickConnectExpired> { library().authorizeQuickConnect("000000") }
        }

    @Test
    fun `a session follows the server to another address`() =
        runTest {
            // The phone left the Wi-Fi (M1): the session opened at an address that no longer answers
            // (192.0.2.1, documentation only) moves to the one that does.
            val session = signIn()
            val library = connector.open(session.copy(server = ServerAddress.of("http://192.0.2.1:8096")))
            library.moveTo(address)
            assertTrue(library.libraries().isNotEmpty())
        }

    @Test
    fun `two profiles stay signed in side by side, and signing out ends only one`() =
        runTest {
            val admin = signIn()
            val kid = connector.signIn(address, Credentials.Password(child, ""))
            assertTrue(admin.accessToken != kid.accessToken)
            assertTrue(connector.open(admin).libraries().isNotEmpty())
            assertTrue(connector.open(kid).libraries().isNotEmpty())

            connector.signOut(kid)

            assertFailsWith<SourceException.Unauthorized> { connector.open(kid).libraries() }
            assertTrue(connector.open(admin).libraries().isNotEmpty())
        }

    @Test
    fun `serves a poster at the asked size`() =
        runTest {
            val source = library()
            val films = source.films()
            val film = source.items(films.id).items.single { it.title == "Nuit polaire" }
            val poster = assertNotNull(film.images.primary)
            assertNotNull(poster.blurHash, "Jellyfin gives a BlurHash for each image")

            val response = get(connector.imageUrl(address, poster, 150, 225))
            assertEquals(200, response.statusCode())
            assertTrue(
                response
                    .headers()
                    .firstValue("Content-Type")
                    .orElse("")
                    .startsWith("image/"),
            )
        }

    private suspend fun LibrarySource.films() = libraries().single { it.name == "Films" }

    private fun get(url: String): HttpResponse<ByteArray> =
        http.send(HttpRequest.newBuilder(URI(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray())

    private fun post(
        path: String,
        token: String,
    ): HttpResponse<String> =
        http.send(
            HttpRequest
                .newBuilder(URI("$url$path"))
                .header(
                    "Authorization",
                    "MediaBrowser Client=\"Cine Doury tests\", Device=\"CI\", DeviceId=\"cine-doury-phone\", " +
                        "Version=\"0.0.0\", Token=\"$token\"",
                ).POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private companion object {
        /** The films of generate-media.sh in « Films ». */
        const val FILMS = 45
    }
}
