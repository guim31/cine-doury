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
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.LibraryKind
import fr.guim.cinedoury.core.model.MediaItem
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
import fr.guim.cinedoury.feature.details.panelKey
import fr.guim.cinedoury.feature.home.homeModule
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
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Fiches vides, through the real app (view model, navigation, source): a season of « Ajouts récents »
 * opens its series on it; a folder of one video opens that video, ready to play; a folder of several
 * shows them in a grid; an empty folder says there is nothing to play, and why in « ⋯ ».
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class EmptyDetailsFlowTest {
    @get:Rule
    val compose = createTvComposeRule()

    private val source =
        InMemorySource(
            server = ServerInfo(id = "home", name = "Maison", version = "12.3.0"),
            accounts = mapOf("admin" to "secret"),
            libraries = listOf(J2Samples.series, EmptyDetailsSamples.homeVideos),
            contents =
                mapOf(
                    J2Samples.series.id to listOf(J3Samples.marees),
                    EmptyDetailsSamples.homeVideos.id to EmptyDetailsSamples.all,
                ),
            extras = J3Samples.mareesPlayable + J3Samples.mareesSeasons,
            folders = EmptyDetailsSamples.folders,
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
                searchModule,
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
        compose.waitUntil(TIMEOUT) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }

    /** The app on the search, then the details of [item] over it, as a card opens them. */
    private fun open(item: MediaItem): Navigator {
        val navigator = Navigator(Route.Search())
        navigator.push(Route.Details(item))
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark) {
                CineApp(ThemeVariant.Dark, onToggleVariant = {}, showCatalog = false, navigator = navigator)
            }
        }
        return navigator
    }

    @Test
    fun `a season of the new additions opens its series on it, and Back leaves at once`() {
        // As `/Items/Latest` gives it: the season, its series named, no episodes of its own.
        val navigator = open(J3Samples.mareesSeasons[1])
        // « Reprendre » of the series: its episode in progress, in season 1.
        waitFor(hasTestTag(DetailsPreview.season(1)))
        focusable(DetailsPreview.PLAY).assertIsFocused()
        assertEquals(0, compose.onAllNodes(hasTestTag(DetailsPreview.NOTHING)).fetchSemanticsNodes().size)
        // Down lands on the tab shown, season 2, then on its first episode.
        press(Key.DirectionDown)
        focusable(DetailsPreview.season(1)).assertIsFocused()
        press(Key.DirectionDown)
        waitFor(hasText("Le retour", substring = true))
        assertEquals(0, compose.onAllNodes(hasText("Grande marée", substring = true)).fetchSemanticsNodes().size)
        press(Key.DirectionUp, times = 2)
        compose.pressBack()
        compose.waitForIdle()
        assertIs<Route.Search>(navigator.current.route)
    }

    @Test
    fun `a folder of one video opens that video, ready to play`() {
        open(EmptyDetailsSamples.oneFolder)
        // « Audio et sous-titres »: the screen is the video's (its title shows as its logo).
        waitFor(hasTestTag(DetailsPreview.TRACKS))
        focusable(DetailsPreview.PLAY).assertIsFocused()
        press(Key.DirectionCenter)
        waitFor(hasTestTag("player"))
        compose.waitUntil(TIMEOUT) {
            shadowOf(Looper.getMainLooper()).idle()
            source.playback.requests.isNotEmpty()
        }
        assertEquals(
            EmptyDetailsSamples.alone.id,
            source.playback.requests
                .last()
                .item,
        )
    }

    @Test
    fun `a folder of several videos shows them in a grid, each opening its details`() {
        val navigator = open(EmptyDetailsSamples.manyFolder)
        val second = DetailsPreview.card("children", 1)
        waitFor(hasTestTag(second))
        // No « Lire » for a folder: the first card has the focus.
        focusable(DetailsPreview.card("children", 0)).assertIsFocused()
        press(Key.DirectionRight)
        focusable(second).assertIsFocused()
        press(Key.DirectionCenter)
        compose.waitForIdle()
        val opened = assertIs<Route.Details>(navigator.current.route)
        assertEquals(EmptyDetailsSamples.many[1].id, opened.item.id)
        waitFor(hasTestTag(DetailsPreview.TRACKS))
        compose.pressBack()
        waitFor(hasTestTag(second))
        focusable(second).assertIsFocused()
        assertEquals(0, compose.onAllNodes(hasTestTag(DetailsPreview.NOTHING)).fetchSemanticsNodes().size)
    }

    @Test
    fun `an empty folder says there is nothing to play, and why in the more panel`() {
        open(EmptyDetailsSamples.emptyFolder)
        waitFor(hasTestTag(DetailsPreview.NOTHING))
        waitFor(hasText("Rien à lire ici pour le moment"))
        // No « Lire »: the focus on « Favori », « ⋯ » beside it.
        focusable(DetailsPreview.FAVORITE).assertIsFocused()
        press(Key.DirectionRight)
        focusable(DetailsPreview.MORE).assertIsFocused()
        press(Key.DirectionCenter)
        waitFor(hasTestTag(panelKey("why")))
        waitFor(hasText("Dossier · 0 élément"))
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}

/** A library of home videos (fiches vides): each video in its folder, a folder of several, an empty one. */
internal object EmptyDetailsSamples {
    val homeVideos = Library(LibraryId("home-videos"), "Vidéos", LibraryKind.HomeVideos)

    private fun video(title: String) =
        J3Samples.phare.copy(
            id = ItemId("video-$title"),
            kind = ItemKind.Video,
            title = title,
            people = emptyList(),
            mediaSources = J3Samples.phare.mediaSources.map { it.copy(id = "src-$title") },
        )

    private fun folder(
        title: String,
        count: Int,
    ) = MediaItem(id = ItemId("folder-$title"), kind = ItemKind.Folder, title = title, childCount = count)

    val alone = video("Anniversaire")
    val many = listOf(video("Plage, matin"), video("Plage, soir"), video("Plage, nuit"))

    val oneFolder = folder("Anniversaire", 1)
    val manyFolder = folder("Plage", many.size)
    val emptyFolder = folder("Vide", 0)

    val folders: Map<ItemId, List<ItemId>> =
        mapOf(
            oneFolder.id to listOf(alone.id),
            manyFolder.id to many.map { it.id },
            emptyFolder.id to emptyList(),
        )

    val all: List<MediaItem> = listOf(oneFolder, manyFolder, emptyFolder, alone) + many
}
