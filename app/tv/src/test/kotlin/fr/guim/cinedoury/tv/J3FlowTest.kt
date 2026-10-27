package fr.guim.cinedoury.tv

import android.os.Looper
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
import fr.guim.cinedoury.feature.manage.manageModule
import fr.guim.cinedoury.feature.player.playerModule
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The details screen of J3 through the real app (view model, source, index): the card opens at once,
 * the server's details arrive, the tracks chosen in the panel go to the player, « Retour » comes back
 * to the button left, and « Marquer comme vu » reaches the server.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class J3FlowTest {
    @get:Rule
    val compose = createTvComposeRule()

    private val source =
        InMemorySource(
            server = ServerInfo(id = "home", name = "Maison", version = "12.3.0"),
            accounts = mapOf("admin" to "secret"),
            libraries = listOf(J2Samples.films),
            contents = mapOf(J2Samples.films.id to listOf(J3Samples.phare, J3Samples.retour)),
            extras = listOf(J3Samples.saga),
            collections = mapOf(J3Samples.saga.id to listOf(J3Samples.phare.id, J3Samples.retour.id)),
        )

    private val players = FakeVideoPlayers()

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
                module { single<VideoPlayerFactory> { players } },
                settingsModule,
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

    private fun waitFor(matcher: SemanticsMatcher) =
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `tracks chosen on the details screen go to the player, then watched reaches the server`() {
        // The card of the home, without its media sources: the screen shows it at once.
        val card = J3Samples.phare.copy(mediaSources = emptyList(), people = emptyList())
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark) {
                CineApp(
                    ThemeVariant.Dark,
                    onToggleVariant = {},
                    showCatalog = false,
                    navigator = Navigator(Route.Details(card)),
                )
            }
        }
        waitFor(hasTestTag(DetailsPreview.TRACKS))
        // The saga arrives with the details.
        waitFor(hasText("Le Phare : la saga"))
        focusable(DetailsPreview.PLAY).assertIsFocused()
        press(Key.DirectionRight, times = 3)
        focusable(DetailsPreview.TRACKS).assertIsFocused()
        press(Key.DirectionCenter)
        focusable(panelLine(2)).assertIsFocused()
        // The English track, then « Lire avec ces choix » at the top.
        press(Key.DirectionDown, times = 2)
        press(Key.DirectionCenter)
        press(Key.DirectionUp, times = 3)
        focusable(panelLine(0)).assertIsFocused()
        press(Key.DirectionCenter)
        waitFor(hasTestTag("player"))
        assertPlayerAskedForEnglish()

        compose.pressBack()
        waitFor(hasTestTag(DetailsPreview.TRACKS))
        focusable(DetailsPreview.TRACKS).assertIsFocused()
        press(Key.DirectionRight)
        focusable(DetailsPreview.WATCHED).assertIsFocused()
        press(Key.DirectionCenter)
        compose.waitUntil(TIMEOUT) {
            shadowOf(Looper.getMainLooper()).idle()
            runBlocking { source.calls["setPlayed"] == 1 }
        }
        val active = getKoin().get<SessionManager>().active.value!!
        assertTrue(
            runBlocking {
                active.library
                    .item(J3Samples.phare.id)
                    .userData!!
                    .played
            },
        )
    }

    /** The player asks the server for the English track, from the saved position. */
    private fun assertPlayerAskedForEnglish() {
        // The player reads its settings from DataStore, which answers on the main looper: run it while
        // waiting, or the answer can arrive only after the timeout.
        compose.waitUntil(TIMEOUT) {
            shadowOf(Looper.getMainLooper()).idle()
            players.player.loads.isNotEmpty()
        }
        val english =
            J3Samples.phare.mediaSources
                .first()
                .audio
                .first { it.language == "eng" }
                .index
        val asked = source.playback.requests.last()
        assertEquals(english, asked.audioIndex)
        assertEquals(40, asked.start.inWholeMinutes.toInt())
        assertEquals(
            english,
            players.player.loads
                .last()
                .audioIndex,
        )
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
