package fr.guim.cinedoury.mobile

import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.di.dataModule
import fr.guim.cinedoury.core.data.security.TokenCipher
import fr.guim.cinedoury.core.model.MarginStatus
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.MediaSource
import fr.guim.cinedoury.core.model.MediaStream
import fr.guim.cinedoury.core.model.NetworkKind
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.model.ServerInfo
import fr.guim.cinedoury.core.model.StreamType
import fr.guim.cinedoury.core.playback.DeviceCapabilitiesReader
import fr.guim.cinedoury.core.playback.NetworkMonitor
import fr.guim.cinedoury.core.playback.NetworkState
import fr.guim.cinedoury.core.playback.PlaybackScope
import fr.guim.cinedoury.core.playback.VideoPlayerFactory
import fr.guim.cinedoury.core.playback.playbackModule
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.ServerDiscovery
import fr.guim.cinedoury.core.source.SourceConnector
import fr.guim.cinedoury.core.source.fake.InMemorySource
import fr.guim.cinedoury.feature.player.LivePlayers
import fr.guim.cinedoury.feature.player.PlayerViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * The phone's player and its screen (Y, `SPEC.md` § 4.32): no sound before the screen is started nor
 * after it goes, even in the middle of a load, one player at a time, and a new stream asked in the
 * background (another network) that stays paused. The tracks chosen follow the series (W).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = PHONE)
class MobilePlayerLifecycleTest {
    private val film = MobileSamples.movies[0].copy(mediaSources = listOf(version("film", shifted = false)))
    private val episodes =
        MobileSamples.episodes.mapIndexed { index, episode ->
            episode.copy(mediaSources = listOf(version(episode.id.value, shifted = index % 2 == 1)))
        }
    private val source =
        InMemorySource(
            server = ServerInfo(id = "home", name = "Maison", version = "12.3.0"),
            accounts = mapOf("admin" to "secret"),
            libraries = listOf(MobileSamples.films, MobileSamples.series),
            contents =
                mapOf(
                    MobileSamples.films.id to listOf(film),
                    MobileSamples.series.id to listOf(MobileSamples.show),
                ),
            extras = episodes + MobileSamples.seasons,
        )
    private val players = FakeVideoPlayers(fresh = true)
    private val network = FakeNetwork()
    private lateinit var model: PlayerViewModel

    private class FakeNetwork : NetworkMonitor {
        override val state = MutableStateFlow(NetworkState(online = true, kind = NetworkKind.Wifi))

        override fun onChange(action: () -> Unit) = AutoCloseable { }
    }

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
                playbackModule,
                module { single<VideoPlayerFactory> { players } },
            )
        }
        runBlocking {
            val sessions = getKoin().get<SessionManager>()
            sessions.addServer(ServerAddress.of("http://192.0.2.10:8096"))
            sessions.signIn(Credentials.Password("admin", "secret"))
        }
        LivePlayers.forgetAll()
    }

    @After
    fun stop() {
        stopKoin()
    }

    private fun newModel(request: PlaybackRequest): PlayerViewModel {
        val koin = getKoin()
        return PlayerViewModel(
            request = request,
            sessions = koin.get(),
            settingsStore = koin.get(),
            readCapabilities = koin.get<DeviceCapabilitiesReader>()::read,
            players = players,
            home = koin.get(),
            updates = koin.get(),
            reportScope = koin.get<PlaybackScope>().scope,
            network = network,
            tracks = koin.get(),
        )
    }

    private fun play(item: MediaItem) {
        model = newModel(PlaybackRequest(item)).also { it.onScreenStarted() }
        waitUntil("${item.title} playing") {
            model.state.value.item.id == item.id && !model.state.value.loading && players.sounding.isNotEmpty()
        }
    }

    private fun slowServer(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { source.playback.gate = it }

    @Test
    fun backgroundWhileLoadingPlaysOnlyOnceBack() {
        val gate = slowServer()
        model = newModel(PlaybackRequest(film)).also { it.onScreenStarted() }
        waitUntil("the stream asked") { source.playback.requests.isNotEmpty() }
        model.onScreenStopped()
        gate.complete(Unit)
        settle()
        assertEquals(emptyList(), players.made)
        model.onScreenStarted()
        waitUntil("playing once back") { players.sounding.size == 1 }
    }

    @Test
    fun backWhileLoadingOrTheScreenGoneMakesNoPlayer() {
        val gate = slowServer()
        model = newModel(PlaybackRequest(film)).also { it.onScreenStarted() }
        val store = ViewModelStore()
        val gone = inStore(store, PlaybackRequest(episodes[0])).also { it.onScreenStarted() }
        waitUntil("both streams asked") { source.playback.requests.size == 2 }
        model.close()
        model.onScreenStopped()
        gone.onScreenStopped()
        store.clear()
        gate.complete(Unit)
        settle()
        assertEquals(emptyList(), players.made)
        assertEquals(0, LivePlayers.alive)
    }

    @Test
    fun nextEpisodeOrAnotherTrackThenBackLeavesNoSound() {
        play(episodes[0])
        val gate = slowServer()
        model.playNext()
        waitUntil("the next episode asked") { source.playback.requests.size == 2 }
        model.close()
        model.onScreenStopped()
        gate.complete(Unit)
        settle()
        assertEquals(emptyList(), players.sounding)
        source.playback.gate = null
        play(film)
        val fake = players.made.last()
        fake.tracksInStream = false
        val second = slowServer()
        model.selectAudio(2)
        waitUntil("the other track asked") { source.playback.requests.size == 4 }
        model.close()
        model.onScreenStopped()
        second.complete(Unit)
        settle()
        assertEquals(1, fake.loads.size)
        assertEquals(emptyList(), players.sounding)
        assertEquals(0, LivePlayers.alive)
    }

    @Test
    fun anotherNetworkInTheBackgroundStaysPaused() {
        play(film)
        val fake = players.made.single()
        fake.margin = MarginStatus(fake.position, fake.position + 5.minutes, fake.position, 15.minutes)
        model.onScreenStopped()
        network.state.value = NetworkState(online = true, kind = NetworkKind.Mobile)
        waitUntil("asked again for the mobile network") { source.playback.requests.size == 2 }
        settle()
        assertEquals(2, fake.loads.size)
        assertTrue(!fake.playing, "a stream played with the app in the background")
    }

    @Test
    fun theTracksChosenFollowTheSeries() {
        play(episodes[0])
        val english =
            episodes[0]
                .mediaSources
                .single()
                .audio
                .single { it.language == "eng" }
                .index
        model.selectAudio(english)
        model.selectSubtitles(null)
        settle()
        model.playNext()
        waitUntil("the next episode") { model.state.value.item.id == episodes[1].id && !model.state.value.loading }
        val next = episodes[1].mediaSources.single()
        assertEquals(next.audio.single { it.language == "eng" }.index, model.state.value.audioIndex)
        assertEquals(null, model.state.value.subtitleIndex)
    }

    private fun inStore(
        store: ViewModelStore,
        request: PlaybackRequest,
    ): PlayerViewModel =
        ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = newModel(request) as T
            },
        )[PlayerViewModel::class.java]

    private fun settle() {
        repeat(SETTLE) {
            shadowOf(Looper.getMainLooper()).idleFor(PlayerViewModel.TICK.toJavaDuration())
            Thread.sleep(POLL)
        }
    }

    private fun waitUntil(
        what: String,
        condition: () -> Boolean,
    ) {
        val end = System.currentTimeMillis() + TIMEOUT
        while (System.currentTimeMillis() < end) {
            // The ticker of the player (the network looked at once a second) runs on the clock of the looper.
            shadowOf(Looper.getMainLooper()).idleFor(PlayerViewModel.TICK.toJavaDuration())
            if (condition()) return
            Thread.sleep(POLL)
        }
        throw AssertionError("never $what: ${model.state.value}")
    }

    private companion object {
        const val TIMEOUT = 10_000L
        const val POLL = 10L
        const val SETTLE = 20

        /** French and English audio, French and English subtitles; [shifted] in the other order. */
        fun version(
            id: String,
            shifted: Boolean,
        ): MediaSource {
            val audio =
                listOf(
                    MediaStream(0, StreamType.Audio, "aac", language = "fra", isDefault = true),
                    MediaStream(0, StreamType.Audio, "aac", language = "eng"),
                ).let { if (shifted) it.reversed() else it }
            val subtitles =
                listOf(
                    MediaStream(0, StreamType.Subtitle, "subrip", language = "fra", isDefault = true),
                    MediaStream(0, StreamType.Subtitle, "subrip", language = "eng"),
                ).let { if (shifted) it.reversed() else it }
            val streams = listOf(MediaStream(0, StreamType.Video, "h264", width = 1920)) + audio + subtitles
            return MediaSource(
                id = "src-$id",
                name = null,
                container = "mkv",
                sizeBytes = 1_000_000_000,
                streams = streams.mapIndexed { index, stream -> stream.copy(index = index) },
            )
        }
    }
}
