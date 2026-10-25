package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.model.ImageRef
import fr.guim.cinedoury.core.model.ImageType
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.LibraryKind
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.model.StreamType
import fr.guim.cinedoury.core.model.VideoRange
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.QuickConnectRequest
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.SourceException
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** The Jellyfin source against recorded answers of the CI server (`src/test/resources/fixtures`). */
class JellyfinConnectorTest {
    private val server = MockWebServer()
    private val connector =
        JellyfinConnector(
            ClientIdentity(appVersion = "0.1.0", deviceId = "device-1", deviceName = "Salon"),
            requestTimeout = 5.seconds,
        )
    private val address by lazy { ServerAddress.of(server.url("/").toString()) }

    @BeforeTest
    fun start() {
        server.start()
    }

    @AfterTest
    fun stop() {
        server.close()
    }

    @Test
    fun `sign in sends the MediaBrowser authorization header`() =
        runTest {
            server.enqueue(json("system-info-public.json"))
            server.enqueue(json("authenticate.json"))

            val session = connector.signIn(address, Credentials.Password("admin", "secret"))

            assertEquals("admin", session.user.name)
            assertTrue(session.user.isAdministrator)
            assertEquals("0123456789abcdef0123456789abcdef", session.accessToken)
            server.takeRequest()
            val authentication = server.takeRequest()
            assertEquals("/Users/AuthenticateByName", authentication.url.encodedPath)
            val header = authentication.headers["Authorization"].orEmpty()
            assertTrue(header.startsWith("MediaBrowser "), header)
            assertTrue("Client=\"Cine+Doury\"" in header, header)
            assertTrue("DeviceId=\"device-1\"" in header, header)
            assertTrue("Token=" !in header, "no token before sign-in: $header")
            assertEquals(null, authentication.headers["X-Emby-Token"])
        }

    @Test
    fun `wrong password is InvalidCredentials`() =
        runTest {
            server.enqueue(json("system-info-public.json"))
            server.enqueue(MockResponse.Builder().code(401).build())

            assertFailsWith<SourceException.InvalidCredentials> {
                connector.signIn(address, Credentials.Password("admin", "wrong"))
            }
        }

    @Test
    fun `older server is Incompatible`() =
        runTest {
            val body = fixture("system-info-public.json").replace("\"12.3.0\"", "\"10.11.2\"")
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body(body)
                    .build(),
            )

            val error = assertFailsWith<SourceException.Incompatible> { connector.serverInfo(address) }
            assertEquals("10.11.2", error.serverVersion)
        }

    @Test
    fun `closed port is Unreachable`() =
        runTest {
            val closed = ServerAddress.of(server.url("/").toString())
            server.close()
            assertFailsWith<SourceException.Unreachable> { connector.serverInfo(closed) }
        }

    @Test
    fun `libraries, a page of films and an item`() =
        runTest {
            val library = connector.open(signedIn())
            server.enqueue(json("user-views.json"))
            server.enqueue(json("items-films-page1.json"))
            server.enqueue(json("item-nuit-polaire.json"))

            val libraries = library.libraries()
            assertEquals(
                listOf(LibraryKind.Collections, LibraryKind.Movies, LibraryKind.Shows),
                libraries.map { it.kind },
            )
            val films = libraries.single { it.kind == LibraryKind.Movies }

            val page = library.items(films.id, PageRequest(limit = 2))
            assertEquals(listOf("Double jeu", "Le Phare"), page.items.map { it.title })
            assertEquals(4, page.totalCount)
            assertEquals(2, page.nextStartIndex)

            val item = library.item(ItemId("00000000000000000000000000000001"))
            assertEquals(ItemKind.Movie, item.kind)
            assertEquals("Nuit polaire", item.title)
            assertEquals(2020, item.year)
            assertEquals(6.021.seconds, item.runtime)
            val source = item.mediaSources.single()
            assertEquals(VideoRange.Hdr10, source.video?.videoRange)
            assertEquals(listOf("fra", "eng", "jpn"), source.audio.map { it.language })
            assertEquals(4, source.streams.count { it.type == StreamType.Subtitle })
            assertTrue(source.subtitles.first().isForced)

            server.takeRequest() // /System/Info/Public
            server.takeRequest() // /Users/AuthenticateByName
            server.takeRequest() // /UserViews
            val itemsRequest = server.takeRequest()
            assertEquals("false", itemsRequest.url.queryParameter("collapseBoxSetItems"))
            assertEquals(films.id.value, itemsRequest.url.queryParameter("parentId")?.replace("-", ""))
            assertTrue("Token=\"0123456789abcdef0123456789abcdef\"" in itemsRequest.headers["Authorization"].orEmpty())
            assertEquals(null, itemsRequest.url.queryParameter("api_key"))
            assertEquals(null, itemsRequest.url.queryParameter("ApiKey"))
        }

    @Test
    fun `revoked token is Unauthorized`() =
        runTest {
            val library = connector.open(signedIn())
            server.enqueue(MockResponse.Builder().code(401).build())

            assertFailsWith<SourceException.Unauthorized> { library.libraries() }
        }

    @Test
    fun `public users with their picture`() =
        runTest {
            server.enqueue(json("users-public.json"))

            val users = connector.publicUsers(address)

            assertEquals(listOf("admin", "enfant"), users.map { it.name })
            val admin = users.first()
            assertEquals("93dc22f066b54845880229ec46225466", admin.id.value)
            assertEquals(
                "$address/UserImage?userId=93dc22f066b54845880229ec46225466&tag=d0568e8dbbafe3805c31c0c1013a1072",
                connector.userImageUrl(address, admin),
            )
            assertEquals(null, connector.userImageUrl(address, users.last()))
            assertEquals("/Users/Public", server.takeRequest().url.encodedPath)
        }

    @Test
    fun `Quick Connect from the code to the session`() =
        runTest {
            server.enqueue(json("quick-connect-initiate.json"))
            server.enqueue(jsonBody(quickConnectState(authenticated = false)))
            server.enqueue(jsonBody(quickConnectState(authenticated = true)))
            server.enqueue(json("system-info-public.json"))
            server.enqueue(json("authenticate.json"))

            val request = connector.startQuickConnect(address)
            assertEquals("361123", request.code)
            assertTrue(request.secret !in request.toString())
            assertEquals(false, connector.isQuickConnectApproved(address, request))
            assertEquals(true, connector.isQuickConnectApproved(address, request))
            val session = connector.signIn(address, Credentials.QuickConnect(request))
            assertEquals("admin", session.user.name)

            val initiate = server.takeRequest()
            assertEquals("POST", initiate.method)
            assertEquals("/QuickConnect/Initiate", initiate.url.encodedPath)
            assertTrue("DeviceId=\"device-1\"" in initiate.headers["Authorization"].orEmpty())
            assertEquals(request.secret, server.takeRequest().url.queryParameter("secret"))
            server.takeRequest()
            server.takeRequest()
            val authenticate = server.takeRequest()
            assertEquals("/Users/AuthenticateWithQuickConnect", authenticate.url.encodedPath)
            assertTrue(request.secret in authenticate.body?.utf8().orEmpty())
        }

    @Test
    fun `forgotten Quick Connect request is QuickConnectExpired`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(404)
                    .body("\"Unknown secret\"")
                    .build(),
            )

            assertFailsWith<SourceException.QuickConnectExpired> {
                connector.isQuickConnectApproved(address, QuickConnectRequest("123456", "secret"))
            }
        }

    @Test
    fun `disabled Quick Connect is QuickConnectDisabled`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(401).build())

            assertFailsWith<SourceException.QuickConnectDisabled> { connector.startQuickConnect(address) }
        }

    @Test
    fun `sign out ends the session with its token`() =
        runTest {
            val session = signedIn()
            server.enqueue(MockResponse.Builder().code(204).build())

            connector.signOut(session)

            server.takeRequest()
            server.takeRequest()
            val logout = server.takeRequest()
            assertEquals("POST", logout.method)
            assertEquals("/Sessions/Logout", logout.url.encodedPath)
            assertTrue("Token=\"${session.accessToken}\"" in logout.headers["Authorization"].orEmpty())
        }

    @Test
    fun `image URL asks for the displayed size`() {
        val poster = ImageRef(ItemId("0123456789abcdef0123456789abcdef"), ImageType.Primary, "abc", blurHash = "x")
        assertEquals(
            "http://192.0.2.10:8096/Items/0123456789abcdef0123456789abcdef/Images/Primary" +
                "?fillWidth=300&fillHeight=450&quality=90&tag=abc",
            connector.imageUrl(ServerAddress.of("http://192.0.2.10:8096"), poster, 300, 450),
        )
        val backdrop = poster.copy(type = ImageType.Backdrop, index = 2)
        assertTrue("/Images/Backdrop/2?" in connector.imageUrl(address, backdrop, 1920, 1080))
    }

    private suspend fun signedIn() =
        run {
            server.enqueue(json("system-info-public.json"))
            server.enqueue(json("authenticate.json"))
            connector.signIn(address, Credentials.Password("admin", "secret"))
        }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/fixtures/$name")) { "missing fixture $name" }.readText()

    private fun json(name: String) = jsonBody(fixture(name))

    private fun quickConnectState(authenticated: Boolean) =
        fixture("quick-connect-initiate.json").replace("\"Authenticated\": false", "\"Authenticated\": $authenticated")

    private fun jsonBody(body: String) =
        MockResponse
            .Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()
}
