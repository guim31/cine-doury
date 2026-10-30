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
import fr.guim.cinedoury.core.source.DiscoveredServer
import fr.guim.cinedoury.core.source.ServerDiscovery
import fr.guim.cinedoury.core.source.SourceConnector
import fr.guim.cinedoury.core.source.fake.InMemorySource
import fr.guim.cinedoury.feature.auth.authModule
import fr.guim.cinedoury.feature.auth.mobile.MobileAuthTags
import fr.guim.cinedoury.feature.details.detailsModule
import fr.guim.cinedoury.feature.details.mobile.MobileDetailsTags
import fr.guim.cinedoury.feature.home.homeModule
import fr.guim.cinedoury.feature.home.mobile.MobileHomeTags
import fr.guim.cinedoury.feature.library.libraryModule
import fr.guim.cinedoury.feature.library.mobile.MobileLibraryTags
import fr.guim.cinedoury.feature.manage.manageModule
import fr.guim.cinedoury.feature.player.mobile.MobilePlayerTags
import fr.guim.cinedoury.feature.player.playerModule
import fr.guim.cinedoury.feature.search.searchModule
import fr.guim.cinedoury.feature.settings.mobile.MobileProfileTags
import fr.guim.cinedoury.feature.settings.settingsModule
import kotlinx.coroutines.flow.flowOf
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
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The phone (M1) through the real app on an in-memory server: the view models, Room and DataStore of
 * the TV under the screens of the phone. The first launch to the home by Quick Connect, a details
 * screen and « Lire », the tabs and « Retour », and a TV connected from the profile.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = PHONE)
class MobileFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val source =
        InMemorySource(
            server = ServerInfo(id = "home", name = "Maison", version = "12.3.0"),
            accounts = mapOf("admin" to "secret", "enfant" to ""),
            libraries = listOf(MobileSamples.films),
            contents = mapOf(MobileSamples.films.id to MobileSamples.movies.take(4)),
        )
    private val players = FakeVideoPlayers()

    /** Robolectric has no Android Keystore. */
    private object PlainCipher : TokenCipher {
        override fun encrypt(token: String) = token

        override fun decrypt(encrypted: String) = encrypted
    }

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
                    single<ServerDiscovery> {
                        ServerDiscovery {
                            flowOf(
                                DiscoveredServer("home", "Maison", MobileSamples.local),
                            )
                        }
                    }
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

    private fun app() {
        compose.setContent { CineMobileTheme { MobileApp(version = "1.1.0") } }
    }

    /**
     * The app of a profile already signed in: it opens on « Qui regarde ? » (the default « Au
     * démarrage »), where the profile opens at once.
     */
    private fun signedInApp() {
        runBlocking {
            val sessions = GlobalContext.get().get<SessionManager>()
            sessions.addServer(MobileSamples.local)
            sessions.signIn(Credentials.Password("admin", "secret"))
        }
        app()
        tap(MobileAuthTags.profile("admin"))
        waitFor(hasTestTag(MobileHomeTags.SCREEN))
    }

    @Test
    fun `from the first launch to the home, by Quick Connect`() {
        app()
        tap(MobileAuthTags.START)
        tap(MobileAuthTags.server(0))
        tap(MobileAuthTags.profile("enfant"))
        // The code of the in-memory server, approved from another device.
        waitFor(hasTestTag(MobileAuthTags.CODE))
        source.approveQuickConnect("482913", "enfant")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        waitFor(hasTestTag(MobileHomeTags.SCREEN))
        // The bar of the tabs is there, once a profile is open.
        waitFor(hasTestTag(MobileAppTags.tab(Tab.Profile)))
    }

    @Test
    fun `a card, its details, « Lire », then « Retour » back to the details`() {
        signedInApp()
        tap(MobileAppTags.tab(Tab.Libraries))
        tap(MobileLibraryTags.library(MobileSamples.films.id.value))
        tap(MobileLibraryTags.card(0))
        tap(MobileDetailsTags.PLAY)
        waitFor(hasTestTag(MobilePlayerTags.SCREEN))
        compose.waitUntil(TIMEOUT) {
            shadowOf(Looper.getMainLooper()).idle()
            players.player.loads.isNotEmpty()
        }
        // The player has no bar of the tabs; « Retour » closes it.
        assertTrue(compose.onAllNodesWithTag(MobileAppTags.tab(Tab.Home)).fetchSemanticsNodes().isEmpty())
        compose.activity.onBackPressedDispatcher.onBackPressed()
        waitFor(hasTestTag(MobileDetailsTags.PLAY))
    }

    @Test
    fun `« Retour » from another tab goes home, then a tab goes back to its first screen`() {
        signedInApp()
        tap(MobileAppTags.tab(Tab.Libraries))
        tap(MobileLibraryTags.library(MobileSamples.films.id.value))
        waitFor(hasTestTag(MobileLibraryTags.GRID))
        tap(MobileAppTags.tab(Tab.Libraries))
        waitFor(hasTestTag(MobileLibraryTags.LIBRARIES))
        compose.activity.onBackPressedDispatcher.onBackPressed()
        waitFor(hasTestTag(MobileHomeTags.SCREEN))
    }

    @Test
    fun `the phone approves the code a TV shows`() {
        signedInApp()
        tap(MobileAppTags.tab(Tab.Profile))
        waitFor(hasText("Connecté à la maison (adresse locale)"))
        tap(MobileProfileTags.CONNECT_DEVICE)
        val code = source.requestFromOtherDevice()
        compose.onNodeWithTag(MobileProfileTags.CODE).performTextInput(code)
        tap(MobileProfileTags.APPROVE)
        waitFor(hasTestTag(MobileProfileTags.DONE))
        assertEquals("admin", source.authorizedCodes[code])
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
