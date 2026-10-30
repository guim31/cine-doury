package fr.guim.cinedoury.mobile

import android.os.Looper
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.di.dataModule
import fr.guim.cinedoury.core.data.security.TokenCipher
import fr.guim.cinedoury.core.model.MarginStatus
import fr.guim.cinedoury.core.model.NetworkKind
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.model.ServerInfo
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
import fr.guim.cinedoury.feature.player.PlayerViewModel
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * The quality of the phone through its view model (M2, `SPEC.md` § 4.27), on a source in memory and a
 * fake player that says what margin it has: the default of the mobile network, one step down when the
 * margin melts (with its notice), nothing during an outage, nothing once the user chose a quality,
 * and the message of a network cut.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = PHONE)
class M2PlayerTest {
    private val film = MobileSamples.movies[0]
    private val source =
        InMemorySource(
            server = ServerInfo(id = "home", name = "Maison", version = "12.3.0"),
            accounts = mapOf("admin" to "secret"),
            libraries = listOf(MobileSamples.films),
            contents = mapOf(MobileSamples.films.id to listOf(film)),
        )
    private val players = FakeVideoPlayers()
    private val fake get() = players.player
    private val network = FakeNetwork()
    private lateinit var model: PlayerViewModel

    private class FakeNetwork : NetworkMonitor {
        override val state = MutableStateFlow(NetworkState(online = true, kind = NetworkKind.Mobile))

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
    }

    /** The player opens on [kind] of network. */
    private fun start(kind: NetworkKind = NetworkKind.Mobile) {
        network.state.value = NetworkState(online = true, kind = kind)
        val koin = getKoin()
        model =
            PlayerViewModel(
                request = PlaybackRequest(film),
                sessions = koin.get(),
                settingsStore = koin.get(),
                readCapabilities = koin.get<DeviceCapabilitiesReader>()::read,
                players = players,
                home = koin.get(),
                updates = koin.get(),
                reportScope = koin.get<PlaybackScope>().scope,
                network = network,
            )
        waitUntil("playing") { model.state.value.stream != null && !model.state.value.loading }
        fake.duration = 100.minutes
    }

    @After
    fun stop() {
        stopKoin()
    }

    private fun advance(time: Duration) = shadowOf(Looper.getMainLooper()).idleFor(time.toJavaDuration())

    private fun waitUntil(
        what: String,
        condition: () -> Boolean,
    ) {
        repeat(200) {
            if (condition()) return
            advance(PlayerViewModel.TICK)
            Thread.sleep(5)
        }
        throw AssertionError("never $what")
    }

    private fun margin(
        ahead: Duration,
        offline: Boolean = false,
    ) = MarginStatus(fake.position, fake.position + ahead, fake.position + 30.seconds, 15.minutes, offline = offline)

    /** [seconds] of playback, the margin given by [ahead] each second. */
    private fun play(
        seconds: Int,
        offline: Boolean = false,
        ahead: (Int) -> Duration,
    ) {
        repeat(seconds) { second ->
            fake.position += 1.seconds
            fake.margin = margin(ahead(second), offline)
            advance(1.seconds)
        }
    }

    private val requests get() = source.playback.requests

    @Test
    fun `on a mobile network the stream is asked at 4 Mbps, and a melting margin takes it to 2 Mbps`() {
        start()
        assertEquals(4_000_000, requests.single().maxBitrate)
        var second = 0
        while (requests.size < 2 && second < 90) {
            play(1) { (170 - second).seconds }
            second++
        }
        assertEquals(2_000_000, requests.last().maxBitrate)
        assertTrue(requests.last().start >= 60.seconds, "from where the film is, not from the start")
        assertTrue(model.state.value.qualityReduced, "« Qualité réduite : réseau faible »")
        advance(6.seconds)
        assertTrue(!model.state.value.qualityReduced)
    }

    @Test
    fun `an outage is not a weak network, and the screen says the network is down`() {
        start()
        play(150, offline = true) { (170 - it).seconds }
        assertEquals(1, requests.size)
        assertTrue(model.state.value.offline)
    }

    @Test
    fun `a quality chosen by the user is never changed`() {
        start()
        model.selectQuality(8_000_000)
        waitUntil("asked again") { requests.size == 2 }
        play(150) { (170 - it).seconds }
        assertEquals(2, requests.size)
        assertEquals(8_000_000, requests.last().maxBitrate)
    }

    @Test
    fun `from the Wi-Fi to the mobile network, the quality goes to the mobile one`() {
        start(NetworkKind.Wifi)
        assertEquals(null, requests.single().maxBitrate, "Automatique on the Wi-Fi")
        network.state.value = NetworkState(online = true, kind = NetworkKind.Mobile)
        play(3) { 15.minutes }
        waitUntil("asked again") { requests.size == 2 }
        assertEquals(4_000_000, requests.last().maxBitrate)
    }
}
