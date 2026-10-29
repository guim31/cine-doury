package fr.guim.cinedoury.tv

import android.os.Looper
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.di.dataModule
import fr.guim.cinedoury.core.data.security.TokenCipher
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.QuickOption
import fr.guim.cinedoury.core.model.ServerInfo
import fr.guim.cinedoury.core.playback.playbackModule
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.ServerDiscovery
import fr.guim.cinedoury.core.source.SourceConnector
import fr.guim.cinedoury.core.source.fake.InMemorySource
import fr.guim.cinedoury.feature.auth.authModule
import fr.guim.cinedoury.feature.details.detailsModule
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * Essai réel 5, C, through the real app (view models, source, cache): « Marquer comme vu » from the
 * menu of a card of a library reaches the server, and the card changes where it is, without reading
 * the library again; the menu then offers « Marquer comme non vu ».
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class R5QuickMenuFlowTest {
    @get:Rule
    val compose = createTvComposeRule()

    private val source =
        InMemorySource(
            server = ServerInfo(id = "home", name = "Maison", version = "12.3.0"),
            accounts = mapOf("admin" to "secret"),
            libraries = listOf(J2Samples.films),
            contents = mapOf(J2Samples.films.id to J2Samples.movies),
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

    private fun waitFor(
        what: String,
        condition: () -> Boolean,
    ) = compose.waitUntil(what, TIMEOUT) {
        shadowOf(Looper.getMainLooper()).idle()
        runCatching(condition).getOrDefault(false).also { if (!it) Thread.sleep(POLL) }
    }

    private fun description(tag: String): String =
        compose
            .onNode(hasClickAction() and hasAnyAncestor(hasTestTag(tag)))
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString()
            .orEmpty()

    private fun shown(option: QuickOption) =
        compose.onAllNodes(hasTestTag(quickLine(option))).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun markedWatchedFromTheMenuTheCardChangesWhereItIs() {
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark) {
                CineApp(
                    ThemeVariant.Dark,
                    onToggleVariant = {},
                    showCatalog = false,
                    navigator = Navigator(Route.Library(J2Samples.films.id)),
                )
            }
        }
        // « Fréquences », started at 40 min: the seventh title.
        val card = LibraryPreview.item(FREQUENCES)
        waitFor("the card") { compose.onAllNodes(hasTestTag(card)).fetchSemanticsNodes().isNotEmpty() }
        waitFor("the focus on the card") {
            focusable(card).performSemanticsAction(SemanticsActions.RequestFocus)
            compose.waitForIdle()
            runCatching { focusable(card).assertIsFocused() }.isSuccess
        }
        assertTrue("Reprendre" in description(card), description(card))
        val reads = source.calls["items"] ?: 0

        compose.holdOk()
        waitFor("the menu") { shown(QuickOption.MarkWatched) }
        // « Reprendre », « Lire depuis le début », « Ouvrir la fiche », then « Marquer comme vu ».
        press(Key.DirectionDown, times = 3)
        focusable(quickLine(QuickOption.MarkWatched)).assertIsFocused()
        press(Key.DirectionCenter)

        waitFor("« vu » on the server") { source.calls["setPlayed"] == 1 }
        waitFor("the card without its position") { "Reprendre" !in description(card) }
        focusable(card).assertIsFocused()
        assertTrue((source.calls["items"] ?: 0) == reads, "the library was read again")

        compose.holdOk()
        waitFor("the menu again") { shown(QuickOption.MarkUnwatched) }
        assertTrue(!shown(QuickOption.MarkWatched))
    }

    private companion object {
        const val FREQUENCES = 6
        const val TIMEOUT = 10_000L
        const val POLL = 10L
    }
}
