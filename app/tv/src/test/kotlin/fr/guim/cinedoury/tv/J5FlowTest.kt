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
import fr.guim.cinedoury.core.data.home.ProfileKey
import fr.guim.cinedoury.core.data.search.RequestServer
import fr.guim.cinedoury.core.data.search.RequestSettings
import fr.guim.cinedoury.core.data.search.SearchRepository
import fr.guim.cinedoury.core.data.security.TokenCipher
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.RequestCandidate
import fr.guim.cinedoury.core.model.RequestKind
import fr.guim.cinedoury.core.model.RequestSeason
import fr.guim.cinedoury.core.model.RequestStatus
import fr.guim.cinedoury.core.model.SearchGroup
import fr.guim.cinedoury.core.model.ServerInfo
import fr.guim.cinedoury.core.playback.playbackModule
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.MediaRequests
import fr.guim.cinedoury.core.source.RequestConnector
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.ServerDiscovery
import fr.guim.cinedoury.core.source.SourceConnector
import fr.guim.cinedoury.core.source.fake.InMemorySource
import fr.guim.cinedoury.feature.auth.authModule
import fr.guim.cinedoury.feature.details.DetailsPreview
import fr.guim.cinedoury.feature.details.detailsModule
import fr.guim.cinedoury.feature.home.homeModule
import fr.guim.cinedoury.feature.library.libraryModule
import fr.guim.cinedoury.feature.player.playerModule
import fr.guim.cinedoury.feature.search.KeyboardTags
import fr.guim.cinedoury.feature.search.SearchPreview
import fr.guim.cinedoury.feature.search.SearchRows
import fr.guim.cinedoury.feature.search.searchModule
import fr.guim.cinedoury.feature.settings.settingsModule
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
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The search on the whole app (`SPEC.md` § 4.12), on an in-memory source with the real view models,
 * Room and DataStore: typed at the remote, a result opened, « Retour » back to the query, the results
 * and the card; « Demander » to a fake Seerr.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class J5FlowTest {
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

    /** A Seerr that finds one film whatever the query, and records the requests. */
    private class FakeSeerr : MediaRequests {
        val requested = CopyOnWriteArrayList<RequestCandidate>()
        val searched = CopyOnWriteArrayList<String>()

        override suspend fun check() = "2.7.3"

        override suspend fun search(
            query: String,
            language: String,
        ): List<RequestCandidate> {
            searched += query
            return listOf(RequestCandidate(120, RequestKind.Movie, "La Communauté de l'anneau", 2001))
        }

        override suspend fun seasons(
            tmdbId: Int,
            language: String,
        ) = emptyList<RequestSeason>()

        override suspend fun request(
            candidate: RequestCandidate,
            seasons: List<Int>,
        ): RequestStatus {
            requested += candidate
            return RequestStatus.Requested
        }
    }

    private val seerr = FakeSeerr()

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
                    single<RequestConnector> {
                        object : RequestConnector {
                            override fun open(
                                address: String,
                                apiKey: String,
                                mediaUserId: String?,
                            ): MediaRequests = seerr
                        }
                    }
                },
                authModule,
                homeModule,
                libraryModule,
                detailsModule,
                playbackModule,
                playerModule,
                searchModule,
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

    private fun queryIs(text: String) = hasText(text) and hasAnyAncestor(hasTestTag("query"))

    private fun showSearch() {
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark) {
                CineApp(
                    ThemeVariant.Dark,
                    onToggleVariant = {},
                    showCatalog = false,
                    navigator = Navigator(Route.Search()),
                )
            }
        }
        waitFor(hasTestTag("keyboard"))
        focusable(KeyboardTags.key("a")).assertIsFocused()
    }

    /** « pha » at the remote: p (third row), h (second), a (first). */
    private fun typePha() {
        press(Key.DirectionDown, times = 2)
        press(Key.DirectionRight)
        focusable(KeyboardTags.key("p")).assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionUp)
        press(Key.DirectionLeft)
        focusable(KeyboardTags.key("h")).assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        waitFor(queryIs("pha"))
    }

    @Test
    fun `typed at the remote, opened, then back to the same query, results and card`() {
        showSearch()
        typePha()
        val movies = SearchRows.of(SearchGroup.Movies)
        waitFor(hasTestTag(SearchPreview.card(movies, 0)))
        waitFor(hasText("Le Phare : la saga"))
        press(Key.DirectionRight, times = 7)
        focusable(SearchPreview.card(movies, 0)).assertIsFocused()
        press(Key.DirectionRight)
        focusable(SearchPreview.card(movies, 1)).assertIsFocused()
        press(Key.DirectionCenter)
        waitFor(hasTestTag(DetailsPreview.PLAY))

        compose.pressBack()
        waitFor(hasTestTag("keyboard"))
        compose.onNode(queryIs("pha")).assertExists()
        focusable(SearchPreview.card(movies, 1)).assertIsFocused()

        // The search that led somewhere is a recent search of the profile.
        val active = getKoin().get<SessionManager>().active.value!!
        val profile = ProfileKey(active.profile.serverId, active.profile.user.id.value)
        compose.waitUntil(TIMEOUT) {
            runBlocking { getKoin().get<SearchRepository>().recent(profile).first() == listOf("pha") }
        }
    }

    @Test
    fun `a title Seerr has, requested after its confirmation`() {
        val serverId =
            getKoin()
                .get<SessionManager>()
                .active.value!!
                .profile.serverId
        runBlocking {
            getKoin().get<RequestSettings>().save(serverId, RequestServer("http://192.0.2.20:5055", "key"))
        }
        val saved = runBlocking { getKoin().get<RequestSettings>().server(serverId).first() }
        assertEquals("http://192.0.2.20:5055", saved?.address)
        showSearch()
        typePha()
        try {
            compose.waitUntil(TIMEOUT) { "pha" in seerr.searched }
        } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
            val rows = compose.onAllNodes(hasTestTag("results")).fetchSemanticsNodes().size
            throw AssertionError("Seerr asked for ${seerr.searched}, results shown: $rows", timeout)
        }
        waitFor(hasTestTag(SearchPreview.card(SearchRows.of(SearchGroup.Collections), 0)))
        // Right into the results, then down to the last row (composed once in view): « Demander ».
        press(Key.DirectionRight, times = 7)
        press(Key.DirectionDown, times = 2)
        waitFor(hasTestTag(SearchPreview.card(SearchRows.REQUESTS, 0)))
        focusable(SearchPreview.card(SearchRows.REQUESTS, 0)).assertIsFocused()
        press(Key.DirectionCenter)
        waitFor(hasTestTag("request-dialog"))
        // « Annuler » first: no request leaves by mistake.
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        compose.waitUntil(TIMEOUT) { seerr.requested.isNotEmpty() }
        assertEquals(120, seerr.requested.single().tmdbId)
        waitFor(hasText("2001 · Demandé"))
        assertTrue(compose.onAllNodes(hasTestTag("request-dialog")).fetchSemanticsNodes().isEmpty())
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
