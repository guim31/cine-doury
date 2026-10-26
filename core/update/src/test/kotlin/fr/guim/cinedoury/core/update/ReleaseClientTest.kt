package fr.guim.cinedoury.core.update

import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The client of the releases against a fake GitHub API (`SPEC.md` § 4.17): the answers of
 * `/repos/{repo}/releases` as GitHub gives them, trimmed to what the app reads.
 */
class ReleaseClientTest {
    private val server = MockWebServer()
    private var answer: MockResponse = ok("[]")
    private val apk = "fake apk content".toByteArray()

    private val github =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.url.encodedPath) {
                    "/repos/guim31/cine-doury/releases" -> answer
                    "/download/cine-doury-1.2.0.apk" -> MockResponse.Builder().body(okio.Buffer().write(apk)).build()
                    "/download/cine-doury-1.2.0.apk.sha256" -> ok("${Checksums.sha256(apk)}  cine-doury-1.2.0.apk\n")
                    else -> MockResponse.Builder().code(404).build()
                }
        }

    private lateinit var client: ReleaseClient

    @BeforeTest
    fun start() {
        server.dispatcher = github
        server.start()
        client = ReleaseClient(api = server.url("/").toString())
    }

    @AfterTest
    fun stop() = server.close()

    private fun ok(body: String) = MockResponse.Builder().body(body).build()

    private fun release(
        tag: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
        apk: Boolean = true,
    ): String {
        val name = "cine-doury-${tag.removePrefix("v")}.apk"
        val assets =
            if (apk) {
                """[{"name":"$name","browser_download_url":"${server.url("/download/$name")}","size":16},
                   {"name":"$name.sha256","browser_download_url":"${server.url(
                    "/download/$name.sha256",
                )}","size":90}]"""
            } else {
                "[]"
            }
        return """{"tag_name":"$tag","name":"Ciné Doury ${tag.removePrefix(
            "v",
        )}","draft":$draft,"prerelease":$prerelease,
            "published_at":"2026-10-26T10:00:00Z","body":"## Nouveautés\n- Réglages","html_url":"https://example.com/$tag",
            "assets":$assets}"""
    }

    @Test
    fun `up to date`() =
        runTest {
            answer = ok("[${release("v1.1.0")}, ${release("v1.0.0")}]")
            val status = UpdatePolicy.status(AppVersion(1, 1, 0), client.releases(), ignoredVersion = null)
            assertEquals(UpdateStatus.UpToDate(AppVersion(1, 1, 0)), status)
        }

    @Test
    fun `a new version, with its notes, date, APK and SHA-256`() =
        runTest {
            answer = ok("[${release("v1.0.0")}, ${release("v1.2.0")}, ${release("v1.1.0")}]")
            val status = UpdatePolicy.status(AppVersion(1, 0, 0), client.releases(), ignoredVersion = null)
            assertIs<UpdateStatus.Available>(status)
            val release = status.release
            assertEquals(AppVersion(1, 2, 0), release.version)
            assertEquals("Ciné Doury 1.2.0", release.title)
            assertEquals(Instant.parse("2026-10-26T10:00:00Z"), release.publishedAt)
            assertEquals("cine-doury-1.2.0.apk", release.apk.name)
            assertEquals("cine-doury-1.2.0.apk.sha256", release.checksum?.name)
            assertFalse(status.ignored)
            assertTrue(UpdatePolicy.offersAtStart(status))
        }

    @Test
    fun `an ignored version is not offered at start, but shows in the settings`() =
        runTest {
            answer = ok("[${release("v1.2.0")}]")
            val status = UpdatePolicy.status(AppVersion(1, 0, 0), client.releases(), ignoredVersion = "1.2.0")
            assertIs<UpdateStatus.Available>(status)
            assertTrue(status.ignored)
            assertFalse(UpdatePolicy.offersAtStart(status))
            // A newer one than the ignored version is offered again.
            answer = ok("[${release("v1.2.0")}, ${release("v1.3.0")}]")
            val next = UpdatePolicy.status(AppVersion(1, 0, 0), client.releases(), ignoredVersion = "1.2.0")
            assertTrue(UpdatePolicy.offersAtStart(next))
        }

    @Test
    fun `drafts, prereleases, other tags and releases without an APK are left out`() =
        runTest {
            answer =
                ok(
                    "[${release("v2.0.0", draft = true)}, ${release("v1.9.0", prerelease = true)}, " +
                        "${release("nightly")}, ${release("v1.5.0", apk = false)}, ${release("v1.1.0")}]",
                )
            assertEquals(listOf(AppVersion(1, 1, 0)), client.releases().map { it.version })
        }

    @Test
    fun `a development build is offered nothing`() =
        runTest {
            answer = ok("[${release("v9.0.0")}]")
            assertEquals(
                UpdateStatus.Development,
                UpdatePolicy.status(AppVersion.parse("dev"), client.releases(), null),
            )
        }

    @Test
    fun `an invalid answer`() =
        runTest {
            answer = ok("<html>Rate limited</html>")
            assertFailsWith<UpdateException.Invalid> { client.releases() }
            answer = ok("""{"message":"Not Found"}""")
            assertFailsWith<UpdateException.Invalid> { client.releases() }
        }

    @Test
    fun `the rate limit of GitHub, 403 or 429, with the time to retry`() =
        runTest {
            answer =
                MockResponse
                    .Builder()
                    .code(403)
                    .addHeader("X-RateLimit-Remaining", "0")
                    .addHeader("X-RateLimit-Reset", "1793000000")
                    .body("""{"message":"API rate limit exceeded"}""")
                    .build()
            val limited = assertFailsWith<UpdateException.RateLimited> { client.releases() }
            assertEquals(Instant.ofEpochSecond(1_793_000_000), limited.retryAt)
            answer = MockResponse.Builder().code(429).build()
            assertNull(assertFailsWith<UpdateException.RateLimited> { client.releases() }.retryAt)
            answer = MockResponse.Builder().code(500).build()
            assertEquals(500, assertFailsWith<UpdateException.Http> { client.releases() }.status)
        }

    @Test
    fun `GitHub unreachable`() =
        runTest {
            server.close()
            assertFailsWith<UpdateException.Unreachable> { client.releases() }
        }

    @Test
    fun `the APK downloads with its progress and matches its SHA-256`() =
        runTest {
            answer = ok("[${release("v1.2.0")}]")
            val release = client.releases().single()
            val target = File(Files.createTempDirectory("update").toFile(), "update.apk")
            val seen = mutableListOf<Pair<Long, Long>>()
            client.download(release.apk, target) { read, total -> seen += read to total }
            assertEquals(apk.size.toLong(), seen.last().first)
            val expected = Checksums.parse(client.text(checkNotNull(release.checksum)), release.apk.name)
            Checksums.verify(target, checkNotNull(expected))
        }
}
