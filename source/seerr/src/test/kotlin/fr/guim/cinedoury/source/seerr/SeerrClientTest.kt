package fr.guim.cinedoury.source.seerr

import fr.guim.cinedoury.core.model.RequestKind
import fr.guim.cinedoury.core.model.RequestStatus
import fr.guim.cinedoury.core.source.SourceException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Seerr client against a fake Seerr (`SPEC.md` § 4.12): the answers of `/api/v1` as Jellyseerr
 * gives them, trimmed to what the app reads. No real Seerr in the CI.
 */
class SeerrClientTest {
    private val server = MockWebServer()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val connector = SeerrConnector()
    private val jellyfinUser = "0123456789abcdef0123456789abcdef"

    /** The fake: answers by path, refuses any other key than [KEY]. */
    private val seerr =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.url.encodedPath
                if (path == "/api/v1/status") return ok("""{"version":"2.7.3","commitTag":"local"}""")
                if (request.headers["X-Api-Key"] != KEY) {
                    return MockResponse
                        .Builder()
                        .code(403)
                        .body("{}")
                        .build()
                }
                return when {
                    path == "/api/v1/auth/me" -> {
                        ok("""{"id":1,"displayName":"admin"}""")
                    }

                    path == "/api/v1/user" -> {
                        ok(USERS)
                    }

                    path == "/api/v1/search" -> {
                        ok(SEARCH)
                    }

                    path == "/api/v1/tv/4001" -> {
                        ok(SHOW)
                    }

                    path == "/api/v1/request" && request.method == "POST" -> {
                        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
                        if (body["mediaId"]!!.jsonPrimitive.int == 2002) {
                            MockResponse
                                .Builder()
                                .code(
                                    409,
                                ).body("""{"message":"Request for this media already exists."}""")
                                .build()
                        } else {
                            MockResponse
                                .Builder()
                                .code(201)
                                .body("""{"id":10,"status":1}""")
                                .build()
                        }
                    }

                    else -> {
                        MockResponse
                            .Builder()
                            .code(404)
                            .body("{}")
                            .build()
                    }
                }
            }
        }

    private fun ok(body: String) =
        MockResponse
            .Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()

    @BeforeTest
    fun start() {
        server.dispatcher = seerr
        server.start()
    }

    @AfterTest
    fun stop() {
        server.close()
    }

    private val address get() = server.url("/").toString()

    private fun client(
        key: String = KEY,
        user: String? = jellyfinUser,
    ) = connector.open(address, key, user)

    @Test
    fun `check gives the version, refuses a wrong key and another server`() =
        runTest {
            assertEquals("2.7.3", client().check())
            assertFailsWith<SourceException.Unauthorized> { client(key = "wrong").check() }
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(
                        request: RecordedRequest,
                    ) = MockResponse
                        .Builder()
                        .code(404)
                        .body("Not found")
                        .build()
                }
            assertFailsWith<SourceException.NotAServer> { client().check() }
        }

    @Test
    fun `an unreachable server`() =
        runTest {
            val closed = address
            server.close()
            assertFailsWith<SourceException.Unreachable> { connector.open(closed, KEY, null).check() }
        }

    @Test
    fun `search - films and series, people left out, statuses, posters through the proxy`() =
        runTest {
            val found = client().search("le seigneur des anneaux", "fr")
            assertEquals(listOf(1001, 2002, 4001, 5005), found.map { it.tmdbId })
            val film = found[0]
            assertEquals(RequestKind.Movie, film.kind)
            assertEquals("Le Seigneur des anneaux : La Communauté de l'anneau", film.title)
            assertEquals(2001, film.year)
            assertEquals(RequestStatus.None, film.status)
            assertEquals("${address}imageproxy/tmdb/t/p/w300_and_h450_face/poster1.jpg", film.posterUrl)
            assertNull(film.libraryItemId)
            assertEquals(RequestStatus.Requested, found[1].status)
            val series = found[2]
            assertEquals(RequestKind.Series, series.kind)
            assertEquals("Les Anneaux de pouvoir", series.title)
            assertEquals(2022, series.year)
            assertEquals(RequestStatus.PartlyAvailable, series.status)
            assertEquals(RequestStatus.Available, found[3].status)
            assertEquals("aaaabbbbccccddddeeeeffff00001111", found[3].libraryItemId)
            // The query fully encoded, spaces as %20; the language asked for.
            val search = requests.last { it.url.encodedPath == "/api/v1/search" }
            assertTrue(
                "query=le%20seigneur%20des%20anneaux" in search.url.encodedQuery.orEmpty(),
                search.url.toString(),
            )
            assertEquals("fr", search.url.queryParameter("language"))
        }

    @Test
    fun `requests are made in the name of the Seerr user of the profile`() =
        runTest {
            client().search("anneaux", "fr")
            val search = requests.last { it.url.encodedPath == "/api/v1/search" }
            assertEquals(KEY, search.headers["X-Api-Key"])
            assertEquals("7", search.headers["X-Api-User"])
            // Unknown to Seerr: in the name of the key.
            requests.clear()
            client(user = "ffffffffffffffffffffffffffffffff").search("anneaux", "fr")
            assertNull(requests.last { it.url.encodedPath == "/api/v1/search" }.headers["X-Api-User"])
            // Looked up once per client.
            requests.clear()
            val once = client()
            once.search("a", "fr")
            once.search("b", "fr")
            assertEquals(1, requests.count { it.url.encodedPath == "/api/v1/user" })
        }

    @Test
    fun `seasons - specials aside, with their statuses`() =
        runTest {
            val seasons = client().seasons(4001, "fr")
            assertEquals(listOf(1, 2, 3), seasons.map { it.number })
            assertEquals(listOf(8, 8, 6), seasons.map { it.episodes })
            assertEquals(
                listOf(RequestStatus.Available, RequestStatus.Requested, RequestStatus.None),
                seasons.map { it.status },
            )
            assertEquals("Saison 3", seasons[2].name)
        }

    @Test
    fun `request a film, a series with its seasons, and one already asked for`() =
        runTest {
            val found = client().search("anneaux", "fr")
            assertEquals(RequestStatus.Requested, client().request(found[0]))
            val film = Json.parseToJsonElement(requests.last { it.method == "POST" }.body!!.utf8()).jsonObject
            assertEquals("movie", film["mediaType"]!!.jsonPrimitive.content)
            assertEquals(1001, film["mediaId"]!!.jsonPrimitive.int)
            assertTrue("seasons" !in film)

            assertEquals(RequestStatus.Requested, client().request(found[2], seasons = listOf(3, 2)))
            val show = Json.parseToJsonElement(requests.last { it.method == "POST" }.body!!.utf8()).jsonObject
            assertEquals("tv", show["mediaType"]!!.jsonPrimitive.content)
            assertEquals(listOf(2, 3), show["seasons"]!!.jsonArray.map { it.jsonPrimitive.int })
            assertEquals("7", requests.last { it.method == "POST" }.headers["X-Api-User"])

            // 409: already requested.
            assertEquals(RequestStatus.Requested, client().request(found[1]))
            assertFailsWith<SourceException.Unauthorized> { client(key = "wrong").request(found[0]) }
        }

    @Test
    fun `addresses without a scheme get http`() {
        assertEquals("http://192.0.2.10:5055", normalizeAddress(" 192.0.2.10:5055/ "))
        assertEquals("https://seerr.example.com/base", normalizeAddress("https://seerr.example.com/base/"))
    }

    private companion object {
        const val KEY = "fake-api-key"

        val USERS =
            """
            {"pageInfo":{"pages":1,"results":2},"results":[
              {"id":1,"displayName":"admin","jellyfinUserId":null},
              {"id":7,"displayName":"Guilhem","jellyfinUserId":"01234567-89ab-cdef-0123-456789ABCDEF"}
            ]}
            """.trimIndent()

        val SEARCH =
            """
            {"page":1,"totalPages":1,"totalResults":6,"results":[
              {"id":1001,"mediaType":"movie","title":"Le Seigneur des anneaux : La Communauté de l'anneau",
               "releaseDate":"2001-12-18","posterPath":"/poster1.jpg","overview":"Un hobbit."},
              {"id":2002,"mediaType":"movie","title":"Le Seigneur des anneaux : Les Deux Tours",
               "releaseDate":"2002-12-18","posterPath":"/poster2.jpg","mediaInfo":{"status":2}},
              {"id":3003,"mediaType":"person","name":"Elijah Wood"},
              {"id":4001,"mediaType":"tv","name":"Les Anneaux de pouvoir","firstAirDate":"2022-09-01",
               "posterPath":null,"mediaInfo":{"status":4}},
              {"id":5005,"mediaType":"movie","title":"Le Retour du roi","releaseDate":"2003-12-17",
               "mediaInfo":{"status":5,"jellyfinMediaId":"aaaabbbbccccddddeeeeffff00001111"}},
              {"id":6006,"mediaType":"movie","title":"Bloqué","mediaInfo":{"status":6}}
            ]}
            """.trimIndent()

        val SHOW =
            """
            {"id":4001,"name":"Les Anneaux de pouvoir","seasons":[
              {"seasonNumber":0,"name":"Épisodes spéciaux","episodeCount":2},
              {"seasonNumber":1,"name":"Saison 1","episodeCount":8},
              {"seasonNumber":2,"name":"Saison 2","episodeCount":8},
              {"seasonNumber":3,"name":"Saison 3","episodeCount":6}
            ],"mediaInfo":{"status":4,"seasons":[{"seasonNumber":1,"status":5},{"seasonNumber":2,"status":3}]}}
            """.trimIndent()
    }
}
