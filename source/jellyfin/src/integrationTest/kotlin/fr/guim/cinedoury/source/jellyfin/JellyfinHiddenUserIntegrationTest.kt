package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.ServerAddress
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assume.assumeTrue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The hidden user of `ci/jellyfin/bootstrap.sh`, as the administrator of a real server is (Jellyfin
 * hides the user made by the startup wizard): a picture, a full policy and preferences in every
 * answer. The real-device trial of 25/10/2026 failed on such a server. Calls come from one thread,
 * as from the main thread of the app. Skipped when JELLYFIN_URL is not set.
 */
class JellyfinHiddenUserIntegrationTest {
    private val url = System.getenv("JELLYFIN_URL").orEmpty()
    private val admin = System.getenv("JELLYFIN_ADMIN_USER") ?: "admin"
    private val adminPassword = System.getenv("JELLYFIN_ADMIN_PASSWORD") ?: "cinedoury-test"
    private val hidden = System.getenv("JELLYFIN_HIDDEN_USER") ?: "parent"
    private val hiddenPassword = System.getenv("JELLYFIN_HIDDEN_PASSWORD") ?: "cinedoury-parent"
    private val executor = Executors.newSingleThreadExecutor()
    private val main = executor.asCoroutineDispatcher()
    private val connector =
        JellyfinConnector(
            ClientIdentity(appVersion = "0.1.0", deviceId = "cine-doury-hidden-user-test", deviceName = "Chambre"),
        )
    private val address get() = ServerAddress.of(url)

    @BeforeTest
    fun requireServer() {
        assumeTrue("JELLYFIN_URL is not set", url.isNotBlank())
    }

    @AfterTest
    fun stop() {
        executor.shutdownNow()
    }

    private fun onMain(block: suspend () -> Unit) = runBlocking { withContext(main) { block() } }

    @Test
    fun `the hidden user is not public`() =
        onMain {
            val names = connector.publicUsers(address).map { it.name }
            assertFalse(hidden in names, "$hidden is hidden: $names")
        }

    @Test
    fun `the hidden user signs in with its password, picture included`() =
        onMain {
            val session = connector.signIn(address, Credentials.Password(hidden, hiddenPassword))

            assertEquals(hidden, session.user.name)
            assertFalse(session.user.isAdministrator)
            assertNotNull(session.user.image, "the picture comes with the user")
            // Its policy opens « Films » and « Séries » only.
            assertEquals(
                setOf("Films", "Séries"),
                connector
                    .open(session)
                    .libraries()
                    .map { it.name }
                    .toSet(),
            )
        }

    @Test
    fun `the hidden user signs in with Quick Connect, approved from another device`() =
        onMain {
            assertTrue(connector.quickConnectEnabled(address))
            val request = connector.startQuickConnect(address)
            assertFalse(connector.isQuickConnectApproved(address, request))

            // The phone, signed in as the hidden user, approves the code.
            val phone = connector.signIn(address, Credentials.Password(hidden, hiddenPassword))
            val approval = post("/QuickConnect/Authorize?code=${request.code}", phone.accessToken)
            assertEquals(200, approval.statusCode(), approval.body())

            assertTrue(connector.isQuickConnectApproved(address, request))
            val session = connector.signIn(address, Credentials.QuickConnect(request))
            assertEquals(hidden, session.user.name)
            assertTrue(session.accessToken != phone.accessToken)
        }

    @Test
    fun `the administrator still signs in beside the hidden user`() =
        onMain {
            val session = connector.signIn(address, Credentials.Password(admin, adminPassword))
            assertTrue(session.user.isAdministrator)
        }

    private fun post(
        path: String,
        token: String,
    ): HttpResponse<String> =
        HttpClient.newHttpClient().send(
            HttpRequest
                .newBuilder(URI("$url$path"))
                .header(
                    "Authorization",
                    "MediaBrowser Client=\"Cine Doury tests\", Device=\"Phone\", DeviceId=\"cine-doury-phone\", " +
                        "Version=\"0.0.0\", Token=\"$token\"",
                ).POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
}
