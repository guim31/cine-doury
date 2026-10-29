package fr.guim.cinedoury.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.di.dataModule
import fr.guim.cinedoury.core.data.security.TokenCipher
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.LayoutSize
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.ServerInfo
import fr.guim.cinedoury.core.playback.playbackModule
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.ServerDiscovery
import fr.guim.cinedoury.core.source.SourceConnector
import fr.guim.cinedoury.core.source.fake.InMemorySource
import fr.guim.cinedoury.feature.auth.authModule
import fr.guim.cinedoury.feature.details.detailsModule
import fr.guim.cinedoury.feature.home.HomePreview
import fr.guim.cinedoury.feature.home.homeModule
import fr.guim.cinedoury.feature.library.LibraryPreview
import fr.guim.cinedoury.feature.library.libraryModule
import fr.guim.cinedoury.feature.manage.manageModule
import fr.guim.cinedoury.feature.settings.settingsModule
import fr.guim.cinedoury.feature.update.updateModule
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent.getKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The J2 path through the real app (view models, Room, cache) on an in-memory source: « Retour »
 * from a details screen brings back exactly the card left, on the home and in a library
 * (`UI.md` principle 3).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class J2FlowTest {
    @get:Rule
    val compose = createTvComposeRule()

    private val films =
        J2Samples.movies.mapIndexed { index, film -> film.copy(dateAdded = Instant.ofEpochSecond(1_000_000L - index)) }
    private val source =
        InMemorySource(
            server = ServerInfo(id = "home", name = "Maison", version = "12.3.0"),
            accounts = mapOf("admin" to "secret"),
            libraries = listOf(J2Samples.films),
            contents = mapOf(J2Samples.films.id to films),
        )

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
                    single<ServerDiscovery> { ServerDiscovery { emptyFlow() } }
                    single<TokenCipher> { PlainCipher }
                },
                authModule,
                homeModule,
                libraryModule,
                manageModule,
                detailsModule,
                settingsModule,
                playbackModule,
                updateModule,
            )
        }
        val sessions = getKoin().get<SessionManager>()
        runBlocking {
            sessions.addServer(ServerAddress.of("http://192.0.2.10:8096"))
            sessions.signIn(Credentials.Password("admin", "secret"))
        }
    }

    @After
    fun stop() {
        stopKoin()
    }

    private fun focusable(tag: String) =
        compose.onNode(hasClickAction() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))))

    private fun press(
        key: Key,
        times: Int = 1,
    ) = repeat(times) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun waitForTag(tag: String) =
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `back from a details screen lands on the card left, on the home and in a library`() {
        compose.setTvContent {
            // Five columns in the grid of the library, in « Normale ».
            CineTheme(ThemeVariant.Dark, layoutSize = LayoutSize.Normal) {
                CineApp(ThemeVariant.Dark, onToggleVariant = {}, showCatalog = false, navigator = Navigator(Route.Home))
            }
        }
        val card = HomePreview.card("latest:${J2Samples.films.id.value}", 2)
        // The rows below the first one are composed once scrolled to.
        waitForTag(HomePreview.card("resume", 0))
        focusable(HomePreview.PLAY).assertIsFocused()
        // « Reprendre » (« Fréquences » is started), then « Ajouts récents dans Films ».
        press(Key.DirectionDown, times = 2)
        press(Key.DirectionRight, times = 2)
        focusable(card).assertIsFocused()
        press(Key.DirectionCenter)
        waitForDetails()
        compose.pressBack()
        waitForTag(card)
        focusable(card).assertIsFocused()

        // Back along the row into the rail (it opens on « Accueil »), then « Films ».
        press(Key.DirectionLeft, times = 3)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        waitForTag(LibraryPreview.item(0))
        focusable(LibraryPreview.item(0)).assertIsFocused()
        press(Key.DirectionRight, times = 2)
        press(Key.DirectionDown)
        val item = focusable(LibraryPreview.item(7))
        item.assertIsFocused()
        press(Key.DirectionCenter)
        waitForDetails()
        compose.pressBack()
        waitForTag(LibraryPreview.item(7))
        focusable(LibraryPreview.item(7)).assertIsFocused()
    }

    @Test
    fun `back from a library goes home, and from the rail of the home closes the app at once`() {
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark) {
                CineApp(ThemeVariant.Dark, onToggleVariant = {}, showCatalog = false, navigator = Navigator(Route.Home))
            }
        }
        waitForTag(HomePreview.card("resume", 0))
        focusable(HomePreview.PLAY).assertIsFocused()
        // « Films », from the rail: a screen of the first level.
        press(Key.DirectionLeft)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        waitForTag(LibraryPreview.item(0))
        compose.pressBack()
        waitForTag(HomePreview.PLAY)
        focusable(HomePreview.PLAY).assertIsFocused()
        assertFalse(compose.activity.isFinishing)
        // « Retour » on the home: the rail, then out of the app, without « Quitter Ciné Doury ? » (essai
        // réel 4).
        compose.pressBack()
        compose.onNode(isFocused() and hasAnyAncestor(hasTestTag("rail"))).assertExists()
        assertFalse(compose.activity.isFinishing)
        compose.pressBack()
        assertTrue(compose.activity.isFinishing)
    }

    /** The details screen (J3). */
    private fun waitForDetails() = waitForTag("details")

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
