package fr.guim.cinedoury.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import fr.guim.cinedoury.core.data.db.CineDatabase
import fr.guim.cinedoury.core.data.prefs.AppPreferences
import fr.guim.cinedoury.core.data.security.TokenCipher
import fr.guim.cinedoury.core.data.server.AddressResolver
import fr.guim.cinedoury.core.model.ServerInfo
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.source.fake.InMemorySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The connection and profile logic of J1, on an in-memory Room and an in-memory source. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionManagerTest {
    /** Stands in for the Keystore, which Robolectric does not have. */
    private class ReversingCipher : TokenCipher {
        override fun encrypt(token: String) = "enc:" + token.reversed()

        override fun decrypt(encrypted: String) =
            encrypted.removePrefix("enc:").reversed().takeIf {
                encrypted.startsWith("enc:")
            }
    }

    private val address = ServerAddress.of("http://192.0.2.10:8096")
    private val source =
        InMemorySource(
            server = ServerInfo(id = "home", name = "Maison", version = "12.3.0"),
            accounts = mapOf("admin" to "secret", "enfant" to "", "invite" to "x"),
            hiddenUsers = setOf("invite"),
        )
    private val database =
        Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), CineDatabase::class.java).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferencesFile = File(createTempDirectory().toFile(), "settings.preferences_pb")

    /** One DataStore per file and process: every manager of a test shares it, as restarts would. */
    private val preferences = AppPreferences(PreferenceDataStoreFactory.create(scope = scope) { preferencesFile })

    private fun manager(): SessionManager =
        SessionManager(
            connector = source,
            servers = ServerRepository(database.servers()),
            profiles = ProfileRepository(database.profiles(), ReversingCipher()),
            preferences = preferences,
            resolver = AddressResolver(source),
        )

    private val sessions by lazy { manager() }

    @AfterTest
    fun close() {
        database.close()
        scope.cancel()
    }

    @Test
    fun `first launch has no server`() =
        runTest {
            assertEquals(StartupTarget.NoServer, sessions.start())
        }

    @Test
    fun `public users become profiles, a hidden user joins once signed in`() =
        runTest {
            sessions.addServer(address)
            sessions.refreshProfiles()
            assertEquals(listOf("admin", "enfant"), sessions.profiles().first().map { it.user.name })

            sessions.signIn(Credentials.Password("invite", "x"))
            val profiles = sessions.profiles().first()
            assertEquals(listOf("admin", "enfant", "invite"), profiles.map { it.user.name })
            assertTrue(profiles.single { it.user.name == "invite" }.isSignedIn)
        }

    @Test
    fun `two profiles stay signed in, each with its own token, encrypted`() =
        runTest {
            sessions.addServer(address)
            sessions.signIn(Credentials.Password("admin", "secret"))
            val adminToken = assertNotNull(sessions.active.value).session.accessToken
            sessions.signIn(Credentials.Password("enfant", ""))
            val childToken = assertNotNull(sessions.active.value).session.accessToken

            assertTrue(adminToken != childToken)
            val stored = database.profiles().all("home")
            assertTrue(stored.none { it.encryptedToken == adminToken || it.encryptedToken == childToken })
            assertEquals(2, stored.count { it.encryptedToken != null })

            val admin = sessions.profiles().first().single { it.user.name == "admin" }
            assertTrue(sessions.open(admin))
            assertEquals(
                adminToken,
                sessions.active.value
                    ?.session
                    ?.accessToken,
            )
        }

    @Test
    fun `the last profile opens directly when asked, behind its PIN`() =
        runTest {
            sessions.addServer(address)
            val admin = sessions.signIn(Credentials.Password("admin", "secret"))

            // Default: always ask.
            assertEquals(StartupTarget.Profiles, manager().start())

            sessions.setStartupMode(StartupMode.LastProfile)
            val restarted = manager()
            assertEquals(StartupTarget.Home, restarted.start())
            assertEquals(
                "admin",
                restarted.active.value
                    ?.profile
                    ?.user
                    ?.name,
            )

            sessions.setPin(admin, "2580")
            val locked = manager()
            val target = assertIs<StartupTarget.Pin>(locked.start())
            assertNull(locked.active.value)
            assertFalse(locked.checkPin(target.profile, "0000"))
            assertTrue(locked.checkPin(target.profile, "2580"))
            assertTrue(locked.open(target.profile))
        }

    @Test
    fun `signing out ends the token on the server and keeps the other profiles`() =
        runTest {
            sessions.addServer(address)
            sessions.signIn(Credentials.Password("admin", "secret"))
            val child = sessions.signIn(Credentials.Password("enfant", ""))
            val childToken = assertNotNull(sessions.active.value).session.accessToken

            sessions.signOut(child)

            assertTrue(childToken in source.revokedTokens)
            assertNull(sessions.active.value)
            val profiles = sessions.profiles().first()
            assertFalse(profiles.single { it.user.name == "enfant" }.isSignedIn)
            assertTrue(profiles.single { it.user.name == "admin" }.isSignedIn)
        }

    @Test
    fun `forgetting the server signs everyone out and starts over`() =
        runTest {
            sessions.addServer(address)
            sessions.signIn(Credentials.Password("admin", "secret"))

            sessions.forgetServer()

            assertEquals(1, source.revokedTokens.size)
            assertTrue(database.profiles().all("home").isEmpty())
            assertNull(sessions.connection.value)
            assertEquals(StartupTarget.NoServer, manager().start())
        }

    @Test
    fun `remote address when the local one is silent, then an error when both are`() =
        runTest {
            sessions.addServer(address)
            val remote = ServerAddress.of("https://example.com")
            sessions.setAddresses(address, remote)
            source.unreachable += address

            val restarted = manager()
            restarted.start()
            assertEquals(ConnectionMode.Remote, restarted.connection.value?.mode)

            source.unreachable += remote
            val target = assertIs<StartupTarget.Unreachable>(manager().start())
            assertIs<SourceException.Unreachable>(target.error)
        }

    @Test
    fun `Quick Connect signs in whoever approves`() =
        runTest {
            sessions.addServer(address)
            val request = sessions.startQuickConnect()
            assertFalse(sessions.isQuickConnectApproved(request))
            source.approveQuickConnect(request.code, "enfant")
            assertTrue(sessions.isQuickConnectApproved(request))

            val profile = sessions.signIn(Credentials.QuickConnect(request))

            assertEquals("enfant", profile.user.name)
            assertEquals(
                "enfant",
                sessions.active.value
                    ?.profile
                    ?.user
                    ?.name,
            )
        }
}
