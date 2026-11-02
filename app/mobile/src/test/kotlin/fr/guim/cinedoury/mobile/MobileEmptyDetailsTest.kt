package fr.guim.cinedoury.mobile

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.di.dataModule
import fr.guim.cinedoury.core.data.security.TokenCipher
import fr.guim.cinedoury.core.designsystem.mobile.CineMobileTheme
import fr.guim.cinedoury.core.model.EpisodeInfo
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
import fr.guim.cinedoury.core.source.ServerDiscovery
import fr.guim.cinedoury.core.source.SourceConnector
import fr.guim.cinedoury.core.source.fake.InMemorySource
import fr.guim.cinedoury.feature.auth.authModule
import fr.guim.cinedoury.feature.details.detailsModule
import fr.guim.cinedoury.feature.details.mobile.MobileDetailsTags
import fr.guim.cinedoury.feature.home.homeModule
import fr.guim.cinedoury.feature.library.libraryModule
import fr.guim.cinedoury.feature.manage.manageModule
import fr.guim.cinedoury.feature.player.mobile.MobilePlayerTags
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
import kotlin.test.assertIs

/**
 * Fiches vides on the phone, through the real app: a season opens its series on it, a folder of one
 * video opens that video, a folder of several shows them in a grid, an empty folder says why.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = PHONE)
class MobileEmptyDetailsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val homeVideos = Library(LibraryId("home-videos"), "Vidéos", LibraryKind.HomeVideos)

    private fun video(title: String) =
        MobileSamples.movies.first().copy(id = ItemId("video-$title"), kind = ItemKind.Video, title = title)

    private fun folder(title: String) = MediaItem(ItemId("folder-$title"), ItemKind.Folder, title)

    private val alone = video("Anniversaire")
    private val many = listOf(video("Plage, matin"), video("Plage, soir"))
    private val oneFolder = folder("Anniversaire")
    private val manyFolder = folder("Plage")
    private val emptyFolder = folder("Vide")

    /** As `/Items/Latest` gives it: the season of new episodes, its series named. */
    private val season =
        MobileSamples.seasons[1].copy(
            episode = EpisodeInfo(MobileSamples.show.id, MobileSamples.show.title, null, 2, null),
        )

    private val source =
        InMemorySource(
            server = ServerInfo(id = "home", name = "Maison", version = "12.3.0"),
            accounts = mapOf("admin" to "secret"),
            libraries = listOf(MobileSamples.series, homeVideos),
            contents =
                mapOf(
                    MobileSamples.series.id to listOf(MobileSamples.show),
                    homeVideos.id to listOf(oneFolder, manyFolder, emptyFolder, alone) + many,
                ),
            extras =
                MobileSamples.episodes +
                    MobileSamples.seasons.mapIndexed { index, it ->
                        it.copy(
                            episode =
                                EpisodeInfo(
                                    MobileSamples.show.id,
                                    MobileSamples.show.title,
                                    null,
                                    index + 1,
                                    null,
                                ),
                        )
                    },
            folders =
                mapOf(
                    oneFolder.id to listOf(alone.id),
                    manyFolder.id to many.map { it.id },
                    emptyFolder.id to emptyList(),
                ),
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
                searchModule,
                settingsModule,
                playbackModule,
                playerModule,
                module { single<VideoPlayerFactory> { players } },
            )
        }
        runBlocking {
            val sessions = GlobalContext.get().get<SessionManager>()
            sessions.addServer(MobileSamples.local)
            sessions.signIn(Credentials.Password("admin", "secret"))
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

    private fun count(matcher: SemanticsMatcher) =
        compose
            .onAllNodes(
                matcher,
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            .size

    /** The libraries, then the details of [item] over them, as a card opens them. */
    private fun open(item: MediaItem): Navigator {
        val navigator = Navigator(Route.Libraries)
        navigator.push(Route.Details(item))
        compose.setContent { CineMobileTheme { MobileApp(version = "1.1.0", navigator = navigator) } }
        return navigator
    }

    private fun back() {
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
    }

    @Test
    fun `a season opens its series on it, and Back leaves at once`() {
        val navigator = open(season)
        // « Reprendre » of the series, then, down the page, the episodes of season 2, not those of season 1.
        waitFor(hasTestTag(MobileDetailsTags.PLAY))
        compose.waitUntil(TIMEOUT) {
            shadowOf(Looper.getMainLooper()).idle()
            runCatching {
                compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("Brume", substring = true))
            }.isSuccess
        }
        assertEquals(0, count(hasText("La grande marée", substring = true)))
        assertEquals(0, count(hasTestTag(MobileDetailsTags.NOTHING)))
        back()
        assertIs<Route.Libraries>(navigator.current.route)
    }

    @Test
    fun `a folder of one video opens that video, ready to play`() {
        open(oneFolder)
        tap(MobileDetailsTags.PLAY)
        waitFor(hasTestTag(MobilePlayerTags.SCREEN))
        compose.waitUntil(TIMEOUT) {
            shadowOf(Looper.getMainLooper()).idle()
            source.playback.requests.isNotEmpty()
        }
        assertEquals(
            alone.id,
            source.playback.requests
                .last()
                .item,
        )
    }

    @Test
    fun `a folder of several videos shows them in a grid, each opening its details`() {
        val navigator = open(manyFolder)
        tap(MobileDetailsTags.child(1))
        val opened = assertIs<Route.Details>(navigator.current.route)
        assertEquals(many[1].id, opened.item.id)
        waitFor(hasTestTag(MobileDetailsTags.PLAY))
        back()
        waitFor(hasTestTag(MobileDetailsTags.child(0)))
        assertEquals(0, count(hasTestTag(MobileDetailsTags.NOTHING)))
    }

    @Test
    fun `an empty folder says there is nothing to play, and why`() {
        open(emptyFolder)
        waitFor(hasTestTag(MobileDetailsTags.NOTHING))
        waitFor(hasText("Rien à lire ici pour le moment"))
        waitFor(hasText("Dossier · 0 élément"))
        assertEquals(0, count(hasTestTag(MobileDetailsTags.PLAY)))
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
