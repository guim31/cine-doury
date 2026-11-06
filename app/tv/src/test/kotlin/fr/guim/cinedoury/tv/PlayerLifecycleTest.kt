package fr.guim.cinedoury.tv

import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.di.dataModule
import fr.guim.cinedoury.core.data.home.ProfileKey
import fr.guim.cinedoury.core.data.security.TokenCipher
import fr.guim.cinedoury.core.data.tracks.SeriesTracksRepository
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.MediaSource
import fr.guim.cinedoury.core.model.MediaStream
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.model.ServerInfo
import fr.guim.cinedoury.core.model.StreamType
import fr.guim.cinedoury.core.model.TrackSelection
import fr.guim.cinedoury.core.model.TrackTraits
import fr.guim.cinedoury.core.playback.DeviceCapabilitiesReader
import fr.guim.cinedoury.core.playback.PlaybackScope
import fr.guim.cinedoury.core.playback.VideoPlayerFactory
import fr.guim.cinedoury.core.playback.playbackModule
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.ServerDiscovery
import fr.guim.cinedoury.core.source.SourceConnector
import fr.guim.cinedoury.core.source.fake.InMemorySource
import fr.guim.cinedoury.feature.details.DetailsViewModel
import fr.guim.cinedoury.feature.player.LivePlayers
import fr.guim.cinedoury.feature.player.PlayerViewModel
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * No sound without the screen of the player (Y, `SPEC.md` § 4.32), and the tracks of a series from an
 * episode to the next (W), through the view model of the player, the source in memory and fake players.
 * Each race the 1.0.5 could lose: « Retour » or « Accueil » while the stream loads, the next episode
 * then « Retour », another track then « Retour », the screen gone in the middle of a load.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class PlayerLifecycleTest {
    /** Episodes whose tracks are the same languages at other indices, from one file to the next (W). */
    private val episodes =
        J3Samples.mareesPlayable.mapIndexed { number, episode ->
            episode.copy(mediaSources = listOf(version("src-${episode.id.value}", shifted = number % 2 == 1)))
        }
    private val film =
        J3Samples.mareesPlayable[0].copy(
            id = ItemId("film"),
            kind = ItemKind.Movie,
            episode = null,
            mediaSources = listOf(version("src-film", shifted = false)),
        )

    private val source =
        InMemorySource(
            server = ServerInfo(id = "home", name = "Maison", version = "12.3.0"),
            accounts = mapOf("admin" to "secret"),
            libraries = listOf(J2Samples.series),
            contents = mapOf(J2Samples.series.id to listOf(J3Samples.marees)),
            extras = episodes + J3Samples.mareesSeasons + film,
        )

    private val players = FakeVideoPlayers(fresh = true)
    private val log = mutableListOf<String>()
    private lateinit var model: PlayerViewModel

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
        val sessions = getKoin().get<SessionManager>()
        runBlocking {
            sessions.addServer(ServerAddress.of("http://192.0.2.10:8096"))
            sessions.signIn(Credentials.Password("admin", "secret"))
        }
        LivePlayers.forgetAll()
        LivePlayers.sink = { log += it }
    }

    @After
    fun stop() {
        LivePlayers.sink = {}
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
            tracks = koin.get(),
        )
    }

    /** A screen started on [item], playing once loaded. */
    private fun play(item: MediaItem) {
        model = newModel(PlaybackRequest(item)).also { it.onScreenStarted() }
        loaded(item)
    }

    private fun loaded(item: MediaItem) =
        waitUntil("${item.title} playing") {
            val state = model.state.value
            state.item.id == item.id && state.stream != null && !state.loading && players.sounding.isNotEmpty()
        }

    // Y. No sound without a screen.

    @Test
    fun homeWhileLoadingPlaysNothingUntilTheScreenComesBack() {
        source.playback.gate = CompletableDeferred()
        model = newModel(PlaybackRequest(film)).also { it.onScreenStarted() }
        waitUntil("the stream asked") { source.playback.requests.isNotEmpty() }
        // « Accueil » while the server answers: the screen stops.
        model.onScreenStopped()
        source.playback.gate?.complete(Unit)
        settle()
        assertEquals(emptyList(), players.made, "a player made with no screen")
        // Back to the app: the film starts, from where it was asked.
        model.onScreenStarted()
        waitUntil("the film playing") { players.sounding.size == 1 }
        assertEquals(1, LivePlayers.alive)
    }

    @Test
    fun retourWhileLoadingMakesNoPlayer() {
        source.playback.gate = CompletableDeferred()
        model = newModel(PlaybackRequest(film)).also { it.onScreenStarted() }
        waitUntil("the stream asked") { source.playback.requests.isNotEmpty() }
        model.close()
        model.onScreenStopped()
        source.playback.gate?.complete(Unit)
        settle()
        assertEquals(emptyList(), players.made)
        assertEquals(0, LivePlayers.alive)
    }

    @Test
    fun theScreenGoneWhileLoadingMakesNoPlayer() {
        source.playback.gate = CompletableDeferred()
        val store = ViewModelStore()
        model = inStore(store, PlaybackRequest(film)).also { it.onScreenStarted() }
        waitUntil("the stream asked") { source.playback.requests.isNotEmpty() }
        // The screen leaves the stack in the middle of the load: its view models are cleared.
        model.onScreenStopped()
        store.clear()
        source.playback.gate?.complete(Unit)
        settle()
        assertEquals(emptyList(), players.made)
        assertEquals(0, LivePlayers.alive)
    }

    @Test
    fun theScreenGoneWhilePlayingReleasesThePlayer() {
        val store = ViewModelStore()
        model = inStore(store, PlaybackRequest(film)).also { it.onScreenStarted() }
        loaded(film)
        store.clear()
        assertEquals(emptyList(), players.sounding)
        assertTrue(players.made.single().released)
        assertEquals(0, LivePlayers.alive)
        assertTrue(log.any { it.endsWith("released: screen gone") }, "$log")
    }

    @Test
    fun nextEpisodeThenRetourLeavesNoSound() {
        play(episodes[1])
        source.playback.gate = CompletableDeferred()
        model.playNext()
        waitUntil("the next episode asked") { source.playback.requests.any { it.item == episodes[2].id } }
        // « Retour » while the next episode loads.
        model.close()
        model.onScreenStopped()
        source.playback.gate?.complete(Unit)
        settle()
        assertEquals(emptyList(), players.sounding)
        assertEquals(1, players.made.size, "one player for both episodes")
        assertTrue(
            players.made.none { load ->
                load.loads.any { it.stream.source.id == "src-${episodes[2].id.value}" }
            },
        )
        assertEquals(0, LivePlayers.alive)
    }

    @Test
    fun anotherTrackThenRetourLeavesNoSound() {
        play(film)
        val fake = players.made.single()
        fake.tracksInStream = false
        source.playback.gate = CompletableDeferred()
        model.selectAudio(2)
        waitUntil("the new stream asked") { source.playback.requests.size == 2 }
        model.close()
        model.onScreenStopped()
        source.playback.gate?.complete(Unit)
        settle()
        assertEquals(1, fake.loads.size, "the stream of the other track played after « Retour »")
        assertTrue(fake.released)
        assertEquals(emptyList(), players.sounding)
    }

    @Test
    fun aStreamAskedWhileStoppedStaysPaused() {
        play(film)
        val fake = players.made.single()
        model.onScreenStopped()
        assertTrue(!fake.playing)
        fake.tracksInStream = false
        model.selectAudio(2)
        settle()
        assertEquals(2, fake.loads.size)
        assertTrue(!fake.playing, "a new stream played with the screen stopped")
    }

    @Test
    fun onePlayerAtATime() {
        play(film)
        val first = players.made.single()
        // Another player screen (a second « Lire »): the first player goes.
        val other = newModel(PlaybackRequest(episodes[0])).also { it.onScreenStarted() }
        waitUntil("the other playing") { players.made.size == 2 && players.made[1].playing }
        assertTrue(first.released)
        assertEquals(1, LivePlayers.alive)
        assertEquals(listOf(players.made[1]), players.sounding)
        assertTrue(log.any { it.contains("still alive") }, "$log")
        // Back to the first screen: it starts again where it stood.
        other.close()
        model.onScreenStopped()
        model.onScreenStarted()
        waitUntil("the first again") { players.made.size == 3 && players.made[2].playing }
        assertEquals(film.id, model.state.value.item.id)
    }

    // W. The tracks of a series.

    @Test
    fun theTracksChosenFollowTheSeries() {
        play(episodes[1])
        // Original audio (English) and full French subtitles, chosen by hand.
        val version = episodes[1].mediaSources.single()
        val english = version.audio.single { it.language == "eng" }.index
        val french = version.subtitles.single { it.language == "fra" && !it.isForced }.index
        model.selectAudio(english)
        model.selectSubtitles(french)
        val tracks = getKoin().get<SeriesTracksRepository>()
        val profile = profile()
        waitUntil("the choice kept") {
            runBlocking { tracks.get(profile, ItemId("Les Marées"))?.subtitlesChosen == true }
        }
        // The next episode: the same languages, at other indices.
        model.playNext()
        loaded(episodes[2])
        val next = episodes[2].mediaSources.single()
        val selection = model.state.value
        assertEquals(next.audio.single { it.language == "eng" }.index, selection.audioIndex)
        assertEquals(next.subtitles.single { it.language == "fra" && !it.isForced }.index, selection.subtitleIndex)
        // « Sans sous-titres », then the previous episode: none.
        model.selectSubtitles(null)
        waitUntil("no subtitles kept") {
            runBlocking { tracks.get(profile, ItemId("Les Marées"))?.subtitles == null }
        }
        model.playPrevious()
        loaded(episodes[1])
        assertNull(model.state.value.subtitleIndex)
        assertEquals(english, model.state.value.audioIndex)
    }

    @Test
    fun aChoiceOfTheDetailsComesFirstAndFilmsKeepNothing() {
        val tracks = getKoin().get<SeriesTracksRepository>()
        val profile = profile()
        runBlocking { tracks.rememberAudio(profile, ItemId("Les Marées"), TrackTraits("eng")) }
        // The details asked French: French it is, for this playback.
        val version = episodes[3].mediaSources.single()
        val french = version.audio.single { it.language == "fra" }.index
        model =
            newModel(
                PlaybackRequest(episodes[3], TrackSelection(version.id, french, null)),
            ).also { it.onScreenStarted() }
        loaded(episodes[3])
        assertEquals(french, model.state.value.audioIndex)
        model.close()
        // A film: nothing remembered, nothing applied.
        play(film)
        model.selectAudio(
            film.mediaSources
                .single()
                .audio
                .last()
                .index,
        )
        settle()
        assertEquals("eng", runBlocking { tracks.get(profile, ItemId("Les Marées")) }?.audio?.language)
        assertNull(runBlocking { tracks.get(profile, film.id) })
    }

    private fun profile(): ProfileKey {
        val active = checkNotNull(getKoin().get<SessionManager>().active.value)
        return ProfileKey(active.profile.serverId, active.profile.user.id.value)
    }

    @Test
    fun theDetailsOfAnEpisodeShowAndKeepTheChoiceOfTheSeries() {
        val koin = getKoin()
        val first = DetailsViewModel(episodes[1], koin.get(), koin.get(), tracks = koin.get())
        waitUntil("the details") { first.state.value.loaded && first.state.value.selection != null }
        val version = episodes[1].mediaSources.single()
        val english = version.audio.single { it.language == "eng" }.index
        first.selectAudio(english)
        first.selectSubtitles(null)
        val tracks = koin.get<SeriesTracksRepository>()
        waitUntil("the choice kept") {
            runBlocking { tracks.get(profile(), ItemId("Les Marées")) }?.subtitlesChosen == true
        }
        // The choice goes to the player as it is, for this playback.
        assertEquals(TrackSelection(version.id, english, null), first.playRequest()?.selection)
        // The details of another episode: the same languages, at its own indices.
        val second = DetailsViewModel(episodes[2], koin.get(), koin.get(), tracks = koin.get())
        waitUntil("the other details") { second.state.value.loaded && second.state.value.selection != null }
        val next = episodes[2].mediaSources.single()
        assertEquals(
            TrackSelection(next.id, next.audio.single { it.language == "eng" }.index, null),
            second.state.value.selection,
        )
    }

    /** The view model in [store], as a screen of the app makes it. */
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

    /** Lets every coroutine that can run, run. */
    private fun settle() {
        repeat(SETTLE) {
            shadowOf(Looper.getMainLooper()).idleFor(TICK.toJavaDuration())
            Thread.sleep(POLL)
        }
    }

    private fun waitUntil(
        what: String,
        condition: () -> Boolean,
    ) {
        val end = System.currentTimeMillis() + TIMEOUT
        while (System.currentTimeMillis() < end) {
            // DataStore answers on its thread, then on the main looper.
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(POLL)
        }
        val state = if (::model.isInitialized) model.state.value.toString() else "no player"
        throw AssertionError("Still waiting for $what: $state, players ${players.made.size}, $log")
    }

    private companion object {
        val TICK: Duration = PlayerViewModel.TICK
        const val TIMEOUT = 10_000L
        const val POLL = 10L
        const val SETTLE = 20

        /** French and English audio, French forced and full subtitles, English ones; [shifted] in another order. */
        fun version(
            id: String,
            shifted: Boolean,
        ): MediaSource {
            val audio =
                listOf(
                    MediaStream(0, StreamType.Audio, "eac3", language = "fra", isDefault = true, channels = 6),
                    MediaStream(0, StreamType.Audio, "eac3", language = "eng", channels = 6),
                ).let { if (shifted) it.reversed() else it }
            val forced = MediaStream(0, StreamType.Subtitle, "subrip", language = "fra", isForced = true)
            val full = MediaStream(0, StreamType.Subtitle, "subrip", language = "fra")
            val english = MediaStream(0, StreamType.Subtitle, "subrip", language = "eng")
            val subtitles = if (shifted) listOf(english, forced, full) else listOf(forced, full, english)
            val streams = listOf(MediaStream(0, StreamType.Video, "h264", width = 1920)) + audio + subtitles
            return MediaSource(
                id = id,
                name = null,
                container = "mkv",
                sizeBytes = 1_000_000_000,
                streams = streams.mapIndexed { index, stream -> stream.copy(index = index) },
            )
        }
    }
}
