package fr.guim.cinedoury.tv

import android.os.Looper
import android.util.Log
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.printToString
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.di.dataModule
import fr.guim.cinedoury.core.data.search.RequestSettings
import fr.guim.cinedoury.core.data.security.TokenCipher
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.ServerInfo
import fr.guim.cinedoury.core.playback.playbackModule
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.GestureLog
import fr.guim.cinedoury.core.source.MediaRequests
import fr.guim.cinedoury.core.source.RequestConnector
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.ServerDiscovery
import fr.guim.cinedoury.core.source.SourceConnector
import fr.guim.cinedoury.core.source.fake.InMemorySource
import fr.guim.cinedoury.core.ui.RailTags
import fr.guim.cinedoury.feature.auth.authModule
import fr.guim.cinedoury.feature.details.detailsModule
import fr.guim.cinedoury.feature.home.HomePreview
import fr.guim.cinedoury.feature.home.homeModule
import fr.guim.cinedoury.feature.library.LibraryPreview
import fr.guim.cinedoury.feature.library.libraryModule
import fr.guim.cinedoury.feature.manage.manageModule
import fr.guim.cinedoury.feature.player.playerModule
import fr.guim.cinedoury.feature.search.searchModule
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
import org.robolectric.shadows.ShadowLog
import java.io.File
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * « Focus toujours visible » (`FONCTIONNALITES.md` § 13, J8): on the whole app over an in-memory
 * source, every screen of the first level has an element focused once loaded (the home, each entry
 * of the rail, a details screen, « Qui regarde ? »), and the focus comes back after a dialog, a
 * panel and « Retour ». A remote without focus does nothing: a screen without one is a dead end.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class J8FocusTest {
    @get:Rule
    val compose = createTvComposeRule()

    private val source =
        InMemorySource(
            server = ServerInfo(id = "home", name = "Maison", version = "12.3.0"),
            accounts = mapOf("admin" to "secret"),
            libraries = listOf(J2Samples.films, J2Samples.series),
            contents = mapOf(J2Samples.films.id to J2Samples.movies, J2Samples.series.id to J2Samples.shows),
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
                    // A file of its own per test: one DataStore per file and per process.
                    single {
                        RequestSettings(
                            PreferenceDataStoreFactory.create {
                                File(
                                    RuntimeEnvironment.getApplication().filesDir,
                                    "requests-${UUID.randomUUID()}.preferences_pb",
                                )
                            },
                            PlainCipher,
                        )
                    }
                    single<RequestConnector> {
                        object : RequestConnector {
                            override fun open(
                                address: String,
                                apiKey: String,
                                mediaUserId: String?,
                            ): MediaRequests = error("no Seerr in this test")
                        }
                    }
                },
                authModule,
                homeModule,
                libraryModule,
                detailsModule,
                manageModule,
                playbackModule,
                playerModule,
                searchModule,
                settingsModule,
                updateModule,
            )
        }
        val sessions = getKoin().get<SessionManager>()
        runBlocking {
            sessions.addServer(ServerAddress.of("http://192.0.2.10:8096"))
            sessions.signIn(Credentials.Password("admin", "secret"))
        }
        // The paths of the banner (essai réel 4); the mosaic « Ce soir » has its own (R5TonightFlowTest).
        withoutTonightMosaic()
    }

    @After
    fun stop() {
        GestureLog.sink = GestureLog.Sink { }
        stopKoin()
    }

    /**
     * 1.0.0, I and J: the rail opened at launch, and after OK on a library, then closed at once: the
     * screen that loads had nothing to focus but its rail. It opens and closes once per gesture now,
     * never on its own (the « rail open » and « rail closed » lines of `GestureLog`).
     */
    @Test
    fun theRailOpensOnlyOnAGesture() {
        val rail = mutableListOf<String>()
        GestureLog.sink = GestureLog.Sink { if (it.startsWith("rail ")) synchronized(rail) { rail += it } }
        start()
        assertFocusOn("the home", HomePreview.PLAY)
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.waitForIdle()
        assertEquals(emptyList(), rail.toList(), "at launch")

        // ← from « Lire »: the rail opens, once.
        press(Key.DirectionLeft)
        assertFocusOn("the rail", RailTags.HOME)
        assertEquals(listOf("rail open"), rail.toList(), "←")

        // ↓ to « Films », OK: the library, its rail closed, and never open on the way.
        rail.clear()
        press(Key.DirectionDown)
        assertFocusOn("« Films »", RailTags.library(J2Samples.films.id.value))
        press(Key.DirectionCenter)
        waitForTag(LibraryPreview.item(0))
        assertSomethingFocused("the library")
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.waitForIdle()
        assertTrue("rail open" !in rail, "the rail opened again after OK: $rail")
        assertTrue(rail.size <= 1, "the rail changed more than once after OK: $rail")
        check(compose.onAllNodes(isFocused() and hasAnyAncestor(hasTestTag("rail"))).fetchSemanticsNodes().isEmpty()) {
            "The focus stayed on the rail"
        }
    }

    @Test
    fun everyScreenOfTheFirstLevelHasTheFocusOnceLoaded() {
        start()
        assertFocusOn("the home", HomePreview.PLAY)

        // Each entry of the rail, opened from the home; « Retour » brings the home back focused.
        for (entry in listOf(RailTags.SEARCH, RailTags.library(J2Samples.films.id.value), RailTags.SETTINGS)) {
            open(entry)
            assertSomethingFocused("the screen of $entry")
            back()
            assertSomethingFocused("the home, back from $entry")
        }

        // « Qui regarde ? »: the profile entry of the rail.
        open(RailTags.PROFILES)
        assertSomethingFocused("« Qui regarde ? »")
    }

    @Test
    fun theFocusComesBackAfterADialogAPanelAndBack() {
        start()
        assertFocusOn("the home", HomePreview.PLAY)

        // « Fiche » of the banner: a details screen, focused; « Retour » gives the home its focus back.
        press(Key.DirectionRight)
        assertFocusOn("« Fiche »", HomePreview.DETAILS)
        press(Key.DirectionCenter)
        waitForTag("details")
        assertSomethingFocused("the details screen")
        back()
        // The banner takes the focus back on « Lire », its first button.
        assertFocusOn("the home, back from the details", HomePreview.PLAY)

        // « Retour » from « Lire »: the rail (one more would close the app, essai réel 4).
        back()
        assertSomethingFocused("the rail")

        // A panel of a library (« Trier »): closed by « Retour », the focus is back on its button.
        open(RailTags.library(J2Samples.films.id.value))
        // The grid loads its first page and may take the focus then: once it has, the focus on
        // « Trier » as the remote would put it there, then OK.
        waitForTag(LibraryPreview.item(0))
        val sort =
            hasClickAction() and (hasTestTag(LibraryPreview.SORT) or hasAnyAncestor(hasTestTag(LibraryPreview.SORT)))
        compose.waitUntil(TIMEOUT) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onNode(sort).performSemanticsAction(SemanticsActions.RequestFocus)
            compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
            compose.onAllNodes(sort and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        press(Key.DirectionCenter)
        waitForTag("panel")
        assertSomethingFocused("the sort panel")
        back()
        assertFocusOn("the library, the panel closed", LibraryPreview.SORT)
    }

    /**
     * `ci/couch-gestures.sh` waits for these lines of the log (`GestureLog`), written only once
     * `log.tag.CineDoury` is DEBUG: a screen drawn, a key received.
     */
    @Test
    fun theAppLogsWhatTheCouchGesturesWaitFor() {
        ShadowLog.setLoggable(LOG_TAG, Log.DEBUG)
        start()
        press(Key.DirectionDown)
        val lines = ShadowLog.getLogsForTag(LOG_TAG).map { it.msg }
        check("gesture screen Home" in lines) { "No « gesture screen Home » in $lines" }
        check(lines.any { it.startsWith("gesture key ") }) { "No key in $lines" }
    }

    private fun start() {
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark) {
                CineApp(ThemeVariant.Dark, onToggleVariant = {}, showCatalog = false, navigator = Navigator(Route.Home))
            }
        }
        waitForTag(HomePreview.card("resume", 0))
    }

    private fun open(entry: String) {
        waitForTag(entry)
        compose.onNode(hasTestTag(entry)).performClick()
        compose.waitForIdle()
    }

    private fun back() = compose.pressBack()

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun waitForTag(tag: String) =
        compose.waitUntil(TIMEOUT) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }

    /** Some element has the focus, within [TIMEOUT]: a screen takes it once laid out. */
    private fun assertSomethingFocused(where: String) {
        val focused =
            runCatching {
                compose.waitUntil(TIMEOUT) {
                    shadowOf(Looper.getMainLooper()).idle()
                    compose.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty()
                }
            }.isSuccess
        check(focused) { "Nothing has the focus on $where\n${compose.onRoot().printToString()}" }
    }

    private fun assertFocusOn(
        where: String,
        tag: String,
    ) {
        val focused =
            runCatching {
                compose.waitUntil(TIMEOUT) {
                    shadowOf(Looper.getMainLooper()).idle()
                    compose
                        .onAllNodes(isFocused() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))))
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
            }.isSuccess
        check(focused) { "The focus is not on $tag on $where\n${compose.onRoot().printToString()}" }
    }

    private companion object {
        const val TIMEOUT = 10_000L
        const val SETTLE_MILLIS = 500L
    }
}
