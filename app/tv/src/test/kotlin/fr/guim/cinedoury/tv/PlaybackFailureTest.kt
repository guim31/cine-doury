package fr.guim.cinedoury.tv

import android.os.Looper
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.di.dataModule
import fr.guim.cinedoury.core.data.security.TokenCipher
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PlayMethod
import fr.guim.cinedoury.core.model.PlaybackFailure
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.model.ServerInfo
import fr.guim.cinedoury.core.model.TranscodeReason
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
import fr.guim.cinedoury.feature.player.PlayerError
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * A failure of the stream through the view model (`SPEC.md` § 4.11, « fausse erreur de lecture »): the
 * error shown stops the player, « Réessayer » starts again where it stopped, and a direct play without
 * decoder still falls back to the transcode.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class PlaybackFailureTest {
    private val episodes = J3Samples.mareesPlayable
    private val runtime = 42.minutes

    private val source =
        InMemorySource(
            server = ServerInfo(id = "home", name = "Maison", version = "12.3.0"),
            accounts = mapOf("admin" to "secret"),
            libraries = listOf(J2Samples.series),
            contents = mapOf(J2Samples.series.id to listOf(J3Samples.marees)),
            extras = episodes + J3Samples.mareesSeasons,
        )

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

    private fun fail(failure: PlaybackFailure, detail: String) {
        fake.events.tryEmit(PlayerEvent.Failed(failure, detail))
        advance(TICK)
    }

    @Test
    fun anErrorShownStopsThePlayerAndRetryStartsWhereItStopped() {
        val episode = episodes[0]
        // Transcoded by the server: no fallback is left after a failure.
        source.playback.transcode = mapOf(episode.id to setOf(TranscodeReason.VideoCodecNotSupported))
        play(episode)
        assertEquals(PlayMethod.Transcode, model.state.value.method)
        fake.position = 12.minutes
        advance(TICK)
        fail(PlaybackFailure.Decoder, "No decoder for audio audio/mp4a-latm")
        val state = model.state.value
        assertIs<PlayerError.Playback>(state.error)
        assertEquals(1, fake.stops, "the player stops behind the error")
        assertFalse(fake.playing, "no sound behind the error")
        assertEquals(
            "stopped",
            source.playback.reports
                .last()
                .first,
        )
        // « Réessayer »: the same episode, from where it stopped.
        val loads = fake.loads.size
        model.retry()
        waitUntil("a new load") { fake.loads.size > loads && model.state.value.error == null }
        assertEquals(12.minutes, fake.loads.last().start)
        assertEquals(1, fake.stops)
    }

    @Test
    fun aDirectPlayWithoutDecoderStillFallsBackToTheTranscode() {
        val episode = episodes[0]
        play(episode)
        assertEquals(PlayMethod.DirectPlay, model.state.value.method)
        fake.position = 3.minutes
        advance(TICK)
        val loads = fake.loads.size
        fail(PlaybackFailure.Decoder, "No decoder for video av01")
        waitUntil("the transcode") { fake.loads.size > loads }
        assertEquals(
            PlayMethod.Transcode,
            fake.loads
                .last()
                .stream.method,
        )
        assertEquals(3.minutes, fake.loads.last().start)
        assertEquals(null, model.state.value.error)
        assertEquals(0, fake.stops, "a fallback does not stop the player")
        assertFalse(
            source.playback.requests
                .last()
                .allowDirect,
        )
    }

    private companion object {
        val TICK = PlayerViewModel.TICK
        const val TIMEOUT = 10_000L
        const val POLL = 10L
    }
}
