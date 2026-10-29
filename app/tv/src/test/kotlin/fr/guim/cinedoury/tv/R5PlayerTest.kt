package fr.guim.cinedoury.tv

import android.os.Looper
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.di.dataModule
import fr.guim.cinedoury.core.data.security.TokenCipher
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.MediaSegment
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.model.SegmentType
import fr.guim.cinedoury.core.model.ServerInfo
import fr.guim.cinedoury.core.playback.DeviceCapabilitiesReader
import fr.guim.cinedoury.core.playback.PlaybackScope
import fr.guim.cinedoury.core.playback.PlayerEvent
import fr.guim.cinedoury.core.playback.VideoPlayerFactory
import fr.guim.cinedoury.core.playback.playbackModule
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.ServerDiscovery
import fr.guim.cinedoury.core.source.SourceConnector
import fr.guim.cinedoury.core.source.fake.InMemorySource
import fr.guim.cinedoury.feature.player.PlayerViewModel
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * The player of the essai réel 5 (`SPEC.md` § 4.23) through its view model, the source in memory and
 * a fake player: the countdown of « Épisode suivant » (A), the previous and next episodes (B).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class R5PlayerTest {
    private val episodes = J3Samples.mareesPlayable
    private val runtime = 42.minutes

    /** S01E03 has its credits marked from 40 min; the others have none. */
    private val credits = MediaSegment(SegmentType.Outro, 40.minutes, runtime)

    private val source =
        InMemorySource(
            server = ServerInfo(id = "home", name = "Maison", version = "12.3.0"),
            accounts = mapOf("admin" to "secret"),
            libraries = listOf(J2Samples.series),
            contents = mapOf(J2Samples.series.id to listOf(J3Samples.marees)),
            extras = episodes + J3Samples.mareesSeasons,
        ).apply { playback.segments = mapOf(episodes[2].id to listOf(credits)) }

    private val players = FakeVideoPlayers()
    private val fake get() = players.player
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
    }

    @After
    fun stop() {
        stopKoin()
    }

    private fun play(item: MediaItem) {
        val koin = getKoin()
        model =
            PlayerViewModel(
                request = PlaybackRequest(item),
                sessions = koin.get(),
                settingsStore = koin.get(),
                readCapabilities = koin.get<DeviceCapabilitiesReader>()::read,
                players = players,
                home = koin.get(),
                updates = koin.get(),
                reportScope = koin.get<PlaybackScope>().scope,
            )
        loaded(item)
    }

    /** The item playing, its stream and its neighbours known. */
    private fun loaded(item: MediaItem) {
        waitUntil("${item.title} playing") {
            val state = model.state.value
            state.item.id == item.id && state.stream != null && !state.loading
        }
        // The neighbours and the segments arrive on their own.
        advance(1.seconds)
        fake.duration = runtime
    }

    /** Runs the main looper, where the view model lives, for [time] of its clock. */
    private fun advance(time: Duration) = shadowOf(Looper.getMainLooper()).idleFor(time.toJavaDuration())

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
        throw AssertionError("Still waiting for $what: ${model.state.value}")
    }

    /** Playing at [position], for [time]: the fake player stays there, as when paused on a frame. */
    private fun at(
        position: Duration,
        time: Duration = TICK,
    ) {
        fake.position = position
        advance(time)
    }

    @Test
    fun withoutCreditsTheNextEpisodeNeverStartsBeforeTheEnd() {
        play(episodes[1])
        assertEquals(
            episodes[2].id,
            model.state.value.next
                ?.id,
        )
        // The card shows for the last 30 s, without a countdown yet.
        at(runtime - 30.seconds)
        assertTrue(model.state.value.nextShown, "the card shows 30 s before the end")
        assertNull(model.state.value.nextSeconds, "no countdown 30 s before the end")
        // Exactly 10 s left: the countdown begins, and follows what is left of the episode.
        at(runtime - 10.seconds)
        assertEquals(10, model.state.value.nextSeconds)
        at(runtime - 4.seconds)
        assertEquals(4, model.state.value.nextSeconds)
        // 20 s with the card shown, still 4 s before the end: the episode goes on (it started the next
        // one 20 s before the end in 0.9.1).
        at(runtime - 4.seconds, time = 20.seconds)
        assertEquals(episodes[1].id, model.state.value.item.id, "the next episode started before the end")
        // The end of the episode starts the next one.
        fake.position = runtime
        fake.events.tryEmit(PlayerEvent.Ended)
        loaded(episodes[2])
    }

    @Test
    fun withCreditsTheCountdownStartsTheNextEpisode() {
        play(episodes[2])
        waitUntil("the credits") {
            model.state.value.segments
                .isNotEmpty()
        }
        at(39.minutes)
        assertTrue(!model.state.value.nextShown, "no card before the credits")
        // From the start of the credits, 10 s of playing time, then the next episode.
        at(40.minutes)
        assertTrue(model.state.value.nextShown, "the card shows with the credits")
        assertEquals(10, model.state.value.nextSeconds)
        at(40.minutes + 5.seconds, time = 5.seconds)
        assertEquals(episodes[2].id, model.state.value.item.id)
        at(40.minutes + 11.seconds, time = 6.seconds)
        loaded(episodes[3])
    }

    @Test
    fun okOnTheCardStartsTheNextEpisodeAtOnce() {
        play(episodes[1])
        at(runtime - 25.seconds)
        assertTrue(model.state.value.nextShown)
        model.playNext()
        loaded(episodes[2])
    }

    @Test
    fun theNeighboursFollowTheSeriesFromASeasonToTheNext() {
        play(episodes[3])
        assertEquals(
            episodes[2].id,
            model.state.value.previous
                ?.id,
            "S01E04 after S01E03",
        )
        assertEquals(
            episodes[4].id,
            model.state.value.next
                ?.id,
            "S02E01 after S01E04",
        )
    }

    @Test
    fun noPreviousForTheFirstEpisodeNoNextForTheLast() {
        play(episodes[0])
        assertNull(model.state.value.previous)
        assertEquals(
            episodes[1].id,
            model.state.value.next
                ?.id,
        )
        play(episodes.last())
        assertNull(model.state.value.next)
        assertEquals(
            episodes[4].id,
            model.state.value.previous
                ?.id,
        )
    }

    @Test
    fun previousEpisodeLeavesThisOneWhereItStands() {
        play(episodes[4])
        at(10.minutes)
        model.playPrevious()
        loaded(episodes[3])
        // The episode left is reported where it stood, as on « Retour ».
        waitUntil("the stop of S02E01") {
            source.playback.reports.any { (kind, report) ->
                kind == "stopped" && report.item == episodes[4].id && report.position == 10.minutes
            }
        }
        // And back to the next one, from the button.
        model.playNext()
        loaded(episodes[4])
    }

    private companion object {
        val TICK = PlayerViewModel.TICK
        const val TIMEOUT = 10_000L
        const val POLL = 10L
    }
}
