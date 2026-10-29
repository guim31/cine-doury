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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.di.dataModule
import fr.guim.cinedoury.core.data.home.CardStyle
import fr.guim.cinedoury.core.data.home.HomeRepository
import fr.guim.cinedoury.core.data.home.ProfileKey
import fr.guim.cinedoury.core.data.home.continues
import fr.guim.cinedoury.core.data.security.TokenCipher
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
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
import kotlinx.coroutines.flow.first
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
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Essai réel 5, D, through the real app (view model, cache, source in memory): the mosaic « Ce soir »
 * is on by default, full, its large tile the film in progress and focused; « Reprendre » leaves the
 * rows once the mosaic holds all of it; « Retour » goes to the rail, then out of the app. F: its
 * customisation, « Continuer ».
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class R5TonightFlowTest {
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

    private fun shown(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(
        what: String,
        condition: () -> Boolean,
    ) = compose.waitUntil(what, TIMEOUT) {
        shadowOf(Looper.getMainLooper()).idle()
        runCatching(condition).getOrDefault(false).also { if (!it) Thread.sleep(POLL) }
    }

    private fun description(tag: String): String =
        compose
            .onNode(hasClickAction() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))))
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString()
            .orEmpty()

    @Test
    fun theMosaicIsFullItsLargeTileFocusedAndBackLeavesTheApp() {
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark) {
                CineApp(ThemeVariant.Dark, onToggleVariant = {}, showCatalog = false, navigator = Navigator(Route.Home))
            }
        }
        waitFor("the mosaic, full") { (0 until TILES).all { shown(HomePreview.tonight(it)) } }
        waitFor(
            "the large tile focused",
        ) { runCatching { focusable(HomePreview.tonight(0)).assertIsFocused() }.isSuccess }
        // « Fréquences », started at 40 min: the large tile, which resumes it.
        val large = description(HomePreview.tonight(0))
        assertTrue("Fréquences" in large && "REPRENDRE" in large, large)
        // The only playback in progress is in the mosaic: no « Continuer », and never its two rows.
        assertFalse(shown("row-continue"), "« Continuer » with nothing left")
        assertFalse(shown("row-resume"), "« Reprendre » left the rows")
        assertFalse(shown("row-next-up"), "« À suivre » left the rows")
        // « Retour »: the rail, then out of the app.
        compose.pressBack()
        compose.onNode(isFocused() and hasAnyAncestor(hasTestTag("rail"))).assertExists()
        compose.pressBack()
        assertTrue(compose.activity.isFinishing)
    }

    /**
     * F: « Personnaliser l'accueil » under the mosaic shows « Continuer » in place of « Reprendre » and
     * « À suivre »; what is chosen for it goes to the two rows stored, which the home without the
     * mosaic shows; removing it removes both.
     */
    @Test
    fun customizingContinueChangesItsTwoRows() {
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark) {
                CineApp(
                    ThemeVariant.Dark,
                    onToggleVariant = {},
                    showCatalog = false,
                    navigator = Navigator(Route.HomeSettings),
                )
            }
        }
        val first = hasTestTag("customize-row-0") or hasAnyAncestor(hasTestTag("customize-row-0"))
        waitFor("« Continuer » first") {
            compose.onAllNodes(hasText("Continuer") and first).fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse(
            compose
                .onAllNodes(
                    hasText("À suivre") and hasAnyAncestor(hasTestTag("customize-rows")),
                ).fetchSemanticsNodes()
                .isNotEmpty(),
            "« À suivre » in the rows",
        )
        val home = getKoin().get<HomeRepository>()
        val active = checkNotNull(getKoin().get<SessionManager>().active.value)
        val profile = ProfileKey(active.profile.serverId, active.profile.user.id.value)

        fun stored() = runBlocking { home.layout(profile).first() }

        click("customize-style")
        waitFor("posters for both rows") {
            stored().filter { it.type.continues }.map { it.style } == listOf(CardStyle.Poster, CardStyle.Poster)
        }
        assertEquals(listOf("resume", "next-up", "latest", "released"), stored().map { it.id }, "in place")
        click("customize-remove")
        waitFor("both rows removed") { stored().none { it.type.continues } }
        assertEquals(listOf("latest", "released"), stored().map { it.id })
    }

    private fun click(tag: String) =
        compose
            .onNode(hasClickAction() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))))
            .performSemanticsAction(SemanticsActions.OnClick)

    private companion object {
        const val TILES = 5
        const val TIMEOUT = 10_000L
        const val POLL = 10L
    }
}
