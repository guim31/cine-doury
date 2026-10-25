package fr.guim.cinedoury.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.di.dataModule
import fr.guim.cinedoury.core.data.security.TokenCipher
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.ServerInfo
import fr.guim.cinedoury.core.playback.VideoPlayerFactory
import fr.guim.cinedoury.core.playback.playbackModule
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.ServerDiscovery
import fr.guim.cinedoury.core.source.SourceConnector
import fr.guim.cinedoury.core.source.fake.InMemorySource
import fr.guim.cinedoury.feature.auth.authModule
import fr.guim.cinedoury.feature.details.DetailsPreview
import fr.guim.cinedoury.feature.details.detailsModule
import fr.guim.cinedoury.feature.details.panelLine
import fr.guim.cinedoury.feature.home.homeModule
import fr.guim.cinedoury.feature.library.libraryModule
import fr.guim.cinedoury.feature.manage.ManagePreview
import fr.guim.cinedoury.feature.manage.manageModule
import fr.guim.cinedoury.feature.player.playerModule
import fr.guim.cinedoury.feature.settings.settingsModule
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Management through the real app (view models, source, cache): from the details of a film, « ⋯ »
 * asks a refresh, then deletes it after the confirmation; the details close and the server has
 * deleted it, nothing before the last « Supprimer ».
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class J6FlowTest {
    @get:Rule
    val compose = createTvComposeRule()

    private val film = J3Samples.phare.copy(canDelete = true, path = "/media/films/Le Phare (2001)")

    private val source =
        InMemorySource(
            server = ServerInfo(id = "home", name = "Maison", version = "12.3.0"),
            accounts = mapOf("admin" to "secret"),
            libraries = listOf(J2Samples.films),
            contents = mapOf(J2Samples.films.id to listOf(film, J3Samples.retour)),
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
                playbackModule,
                playerModule,
                module { single<VideoPlayerFactory> { FakeVideoPlayers() } },
                settingsModule,
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

    private fun waitFor(matcher: SemanticsMatcher) =
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `refresh, then delete a film from its details, the details close`() {
        val navigator = Navigator(Route.Home).apply { push(Route.Details(film)) }
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark) {
                CineApp(ThemeVariant.Dark, onToggleVariant = {}, showCatalog = false, navigator = navigator)
            }
        }
        waitFor(hasTestTag(DetailsPreview.TRACKS))
        focusable(DetailsPreview.PLAY).assertIsFocused()
        press(Key.DirectionRight, times = 6)
        focusable(DetailsPreview.MORE).assertIsFocused()

        // « Compléter ce qui manque »: asked of the server, said on screen.
        press(Key.DirectionCenter)
        waitFor(hasText("Gestion"))
        press(Key.DirectionDown, times = 2)
        focusable(panelLine(3)).assertIsFocused()
        press(Key.DirectionCenter)
        compose.waitUntil(TIMEOUT) { source.manage.log.any { it == "refresh ${film.id.value} FillMissing" } }
        waitFor(hasTestTag(ManagePreview.NOTICE))

        // « Supprimer »: the confirmation first, on « Annuler »; nothing deleted before its « Supprimer ».
        focusable(DetailsPreview.MORE).assertIsFocused()
        press(Key.DirectionCenter)
        waitFor(hasText("Gestion"))
        press(Key.DirectionDown, times = 7)
        focusable(panelLine(8)).assertIsFocused()
        press(Key.DirectionCenter)
        waitFor(hasTestTag(ManagePreview.DIALOG))
        waitFor(hasText("Supprimer « Le Phare » ?"))
        compose
            .onNode(hasClickAction() and hasText("Annuler") and hasAnyAncestor(hasTestTag(ManagePreview.DIALOG)))
            .assertIsFocused()
        assertTrue(source.manage.deleted.isEmpty())
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        compose.waitUntil(TIMEOUT) { film.id in source.manage.deleted }
        compose.waitUntil(TIMEOUT) { navigator.current.route == Route.Home }
        assertEquals(listOf("delete ${film.id.value}"), source.manage.log.filter { it.startsWith("delete") })
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
