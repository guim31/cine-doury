package fr.guim.cinedoury.mobile

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.di.dataModule
import fr.guim.cinedoury.core.data.security.TokenCipher
import fr.guim.cinedoury.core.designsystem.mobile.CineMobileTheme
import fr.guim.cinedoury.core.model.ServerInfo
import fr.guim.cinedoury.core.playback.VideoPlayerFactory
import fr.guim.cinedoury.core.playback.playbackModule
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.ServerDiscovery
import fr.guim.cinedoury.core.source.SourceConnector
import fr.guim.cinedoury.core.source.fake.InMemorySource
import fr.guim.cinedoury.core.ui.mobile.MobileTags
import fr.guim.cinedoury.feature.auth.authModule
import fr.guim.cinedoury.feature.auth.mobile.MobileAuthTags
import fr.guim.cinedoury.feature.details.detailsModule
import fr.guim.cinedoury.feature.home.homeModule
import fr.guim.cinedoury.feature.home.mobile.MobileHomeTags
import fr.guim.cinedoury.feature.library.libraryModule
import fr.guim.cinedoury.feature.manage.manageModule
import fr.guim.cinedoury.feature.player.playerModule
import fr.guim.cinedoury.feature.search.searchModule
import fr.guim.cinedoury.feature.settings.settingsModule
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * V: the phone away from home, its server saved without a remote address. The failure at start says a
 * remote address is needed and offers to add one; once added (the same server, checked), the app starts
 * again on it, the profile still signed in. An address where another server answers is refused.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = PHONE)
class MobileRemoteAddressTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val home = ServerInfo(id = "home", name = "Maison", version = "12.3.0")
    private val remote = ServerAddress.of("https://example.com")
    private val elsewhere = ServerAddress.of("https://other.example.com")

    private val source =
        InMemorySource(
            server = home,
            accounts = mapOf("admin" to "secret"),
            libraries = listOf(MobileSamples.films),
            contents = mapOf(MobileSamples.films.id to MobileSamples.movies.take(4)),
            serverAt = {
                if (it ==
                    elsewhere
                ) {
                    ServerInfo(id = "other", name = "Voisins", version = "12.3.0")
                } else {
                    home
                }
            },
        )
    private val players = FakeVideoPlayers()

    private object PlainCipher : TokenCipher {
        override fun encrypt(token: String) = token

        override fun decrypt(encrypted: String) = encrypted
    }

    private val sessions get() = GlobalContext.get().get<SessionManager>()

    @Before
    fun koin() {
        stopKoin()
        startKoin {
            allowOverride(true)
            androidContext(RuntimeEnvironment.getApplication())
            modules(
                dataModule,
                module {
                    single<SourceConnector> { source }
                    single<ServerDiscovery> { ServerDiscovery { emptyFlow() } }
                    single<TokenCipher> { PlainCipher }
                },
                authModule,
                homeModule,
                libraryModule,
                manageModule,
                detailsModule,
                searchModule,
                settingsModule,
                playbackModule,
                playerModule,
                module { single<VideoPlayerFactory> { players } },
            )
        }
        // Signed in at home, no remote address; then away: the local address is silent.
        runBlocking {
            sessions.addServer(MobileSamples.local)
            sessions.signIn(Credentials.Password("admin", "secret"))
        }
        source.unreachable += MobileSamples.local
    }

    @After
    fun stop() = stopKoin()

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntil(TIMEOUT) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tap(tag: String) {
        waitFor(hasTestTag(tag))
        compose.onAllNodesWithTag(tag, useUnmergedTree = true)[0].performClick()
        compose.waitForIdle()
    }

    /** The app starts: the server is silent at its only address. */
    private fun startAway() {
        compose.setContent { CineMobileTheme { MobileApp(version = "1.1.0") } }
        waitFor(hasText("Hors de la maison", substring = true))
        waitFor(hasTestTag(MobileAuthTags.REMOTE_ADDRESS))
        waitFor(hasText("Ajouter une adresse à distance"))
    }

    private fun typeRemote(address: ServerAddress) {
        tap(MobileAuthTags.REMOTE_ADDRESS)
        waitFor(hasTestTag(MobileAuthTags.ADDRESS))
        compose.onNodeWithTag(MobileAuthTags.ADDRESS).performTextInput(address.url)
        tap(MobileAuthTags.ADDRESS_SUBMIT)
    }

    @Test
    fun `away without a remote address, one is added and the signed-in profile opens with it`() {
        startAway()
        typeRemote(remote)
        // The app starts again on the remote address: « Qui regarde ? », the profile still signed in.
        tap(MobileAuthTags.profile("admin"))
        waitFor(hasTestTag(MobileHomeTags.SCREEN))
        assertEquals(remote, sessions.connection.value?.address)
        assertEquals(
            "admin",
            sessions.active.value
                ?.profile
                ?.user
                ?.name,
        )
    }

    @Test
    fun `an address where another server answers is refused, and nothing is kept`() {
        startAway()
        typeRemote(elsewhere)
        waitFor(hasTestTag(MobileTags.ERROR))
        assertNull(sessions.unreachable.value?.remoteAddress)
        assertNull(sessions.connection.value)
    }

    @Test
    fun `with a remote address already saved, neither answers: the screen says so and offers to change it`() {
        runBlocking { sessions.setAddresses(MobileSamples.local, remote) }
        source.unreachable += remote
        compose.setContent { CineMobileTheme { MobileApp(version = "1.1.0") } }
        waitFor(hasText("ni à son adresse à distance", substring = true))
        waitFor(hasText("Modifier l'adresse à distance"))
        assertTrue(compose.onAllNodes(hasText("Hors de la maison", substring = true)).fetchSemanticsNodes().isEmpty())
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
