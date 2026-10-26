package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.model.PlaybackPreferences
import fr.guim.cinedoury.core.model.SubtitleMode
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.Session
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The settings of J7 that live on the server (`SPEC.md` § 4.16) and the diagnostic report, against
 * the test Jellyfin: the preferences of the child profile (put back at the end), the name of the
 * device, `POST /ClientLog/Document`. Each test signs out (the hidden user `parent` is not used:
 * it may have 4 sessions at most). Skipped when JELLYFIN_URL is not set.
 */
class JellyfinSettingsIntegrationTest {
    private val url = System.getenv("JELLYFIN_URL").orEmpty()
    private val admin = System.getenv("JELLYFIN_ADMIN_USER") ?: "admin"
    private val adminPassword = System.getenv("JELLYFIN_ADMIN_PASSWORD") ?: "cinedoury-test"
    private val child = System.getenv("JELLYFIN_CHILD_USER") ?: "enfant"
    private val address get() = ServerAddress.of(url)
    private val http = HttpClient.newHttpClient()

    @BeforeTest
    fun requireServer() {
        assumeTrue("JELLYFIN_URL is not set", url.isNotBlank())
    }

    private fun connector(
        deviceId: String,
        name: String = "Salon",
    ) = JellyfinConnector(ClientIdentity(appVersion = "0.1.0", deviceId = deviceId, deviceName = name))

    @Test
    fun `the languages and the mode of the subtitles follow the person, on the server`() =
        runBlocking {
            val connector = connector("cine-doury-settings-test")
            val library = connector.open(connector.signIn(address, Credentials.Password(child, "")))
            val before = library.playbackPreferences()
            try {
                val chosen =
                    PlaybackPreferences(
                        audioLanguage = "jpn",
                        subtitleLanguage = "fre",
                        subtitleMode = SubtitleMode.Smart,
                        playDefaultAudio = false,
                    )
                library.setPlaybackPreferences(chosen)
                // Another device of the same person reads them.
                val elsewhere = connector("cine-doury-settings-test-2")
                val other = elsewhere.open(elsewhere.signIn(address, Credentials.Password(child, "")))
                assertEquals(chosen, other.playbackPreferences())
                // The rest of the configuration is untouched: the same libraries, in the same order.
                assertEquals(library.libraries(), other.libraries())

                library.setPlaybackPreferences(chosen.copy(audioLanguage = null, subtitleMode = SubtitleMode.None))
                assertEquals(null, library.playbackPreferences().audioLanguage)
                assertEquals(SubtitleMode.None, library.playbackPreferences().subtitleMode)
                elsewhere.signOut(other.session)
            } finally {
                library.setPlaybackPreferences(before)
                connector.signOut(library.session)
            }
        }

    @Test
    fun `the order of the libraries is the order of the profile`() =
        runBlocking {
            val connector = connector("cine-doury-settings-order")
            val library = connector.open(connector.signIn(address, Credentials.Password(child, "")))
            val before = library.libraries().map { it.id }
            try {
                library.setLibraryOrder(before.reversed())
                assertEquals(before.reversed(), library.libraries().map { it.id })
                library.setLibraryOrder(listOf(before.first()))
                assertEquals(before.first(), library.libraries().first().id)
            } finally {
                library.setLibraryOrder(before)
            }
            assertEquals(before, library.libraries().map { it.id })
            connector.signOut(library.session)
        }

    @Test
    fun `the name of the device changes on the server at the next request`() =
        runBlocking {
            val deviceId = "cine-doury-settings-name"
            val connector = connector(deviceId, name = "Salon")
            val library = connector.open(connector.signIn(address, Credentials.Password(child, "")))
            try {
                library.libraries()
                assertEquals("Salon", deviceNameOnServer(deviceId))
                connector.setDeviceName("Chambre bleue")
                library.libraries()
                // The devices of the server (its dashboard) take the new name at once; a session already
                // open keeps the name it began with until it ends.
                assertEquals("Chambre bleue", deviceNameOnServer(deviceId))
                // The streams of the player introduce the device by its new name too.
                assertTrue("Device=\"Chambre" in library.playback.streamHeaders.getValue("Authorization"))
            } finally {
                connector.signOut(library.session)
            }
        }

    @Test
    fun `a diagnostic report reaches the logs of the server`() =
        runBlocking {
            val connector = connector("cine-doury-settings-report")
            val library = connector.open(connector.signIn(address, Credentials.Password(child, "")))
            val report = "Ciné Doury — rapport de diagnostic\nVersion : 0.1.0 (debug)\nÉcran : 1920 × 1080\n"
            val name =
                try {
                    library.sendDiagnostic(report)
                } finally {
                    connector.signOut(library.session)
                }
            assertTrue(name.isNotBlank(), "the server names the file")

            val token = adminSession().accessToken
            val logs = get("/System/Logs", token)
            assertTrue(name in logs, "$name in $logs")
            val content = get("/System/Logs/Log?name=${URLEncoder.encode(name, "UTF-8")}", token)
            assertTrue("rapport de diagnostic" in content && "1920 × 1080" in content, content)
        }

    private suspend fun adminSession(): Session =
        connector("cine-doury-settings-admin").signIn(address, Credentials.Password(admin, adminPassword))

    /** The name of the device in the devices of the server (`GET /Devices`, as its dashboard shows them). */
    private suspend fun deviceNameOnServer(deviceId: String): String {
        val devices = get("/Devices", adminSession().accessToken)
        // « Name » comes first in each device, before « Id ».
        val device = Regex("\"Name\":\"([^\"]*)\"[^{}]*?\"Id\":\"$deviceId\"").find(devices)
        return device?.groupValues?.get(1).orEmpty()
    }

    private fun get(
        path: String,
        token: String,
    ): String {
        val request =
            HttpRequest
                .newBuilder(URI.create(url.trimEnd('/') + path))
                .header(
                    "Authorization",
                    "MediaBrowser Client=\"Cine Doury\", Device=\"Test\", DeviceId=\"cine-doury-settings-admin\", Version=\"0.1.0\", Token=\"$token\"",
                ).build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode(), "$path: ${response.body()}")
        return response.body()
    }
}
