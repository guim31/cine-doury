package fr.guim.cinedoury.mobile

import android.app.ActivityManager
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.ItemQuery
import fr.guim.cinedoury.core.model.MarginStatus
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.model.PlayMethod
import fr.guim.cinedoury.core.model.PlaybackInfoRequest
import fr.guim.cinedoury.core.model.PlaybackProfiles
import fr.guim.cinedoury.core.model.ProfileOptions
import fr.guim.cinedoury.core.playback.AndroidNetworkMonitor
import fr.guim.cinedoury.core.playback.DeviceCapabilitiesReader
import fr.guim.cinedoury.core.playback.ExoVideoPlayer
import fr.guim.cinedoury.core.playback.Nomad
import fr.guim.cinedoury.core.playback.PlaybackCache
import fr.guim.cinedoury.core.playback.PlayerBuffer
import fr.guim.cinedoury.core.playback.PlayerEvent
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.source.jellyfin.ClientIdentity
import fr.guim.cinedoury.source.jellyfin.JellyfinConnector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The margin of the phone on the phone emulator of the full CI (M2, `SPEC.md` § 4.27), against the
 * Jellyfin of the CI, with the film « Autoroute » that `ci/emulator-margin.sh` adds (20 min of a
 * moving pattern): a film transcoded by the server, then the same film played as it is. Each time the
 * margin must go past [READY] (beyond the 3 min of `ThrottleDelaySeconds`, the throttling being on), then
 * the network is cut (airplane mode) for [OFFLINE]: the position must go on without the player ever
 * waiting; the network back, the margin must grow again. Every second goes to logcat (« margin … »),
 * which the script keeps as an artifact. Skipped without the `marginFilm` argument.
 */
@RunWith(AndroidJUnit4::class)
class MarginOnDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val url = arguments.getString("jellyfinUrl").orEmpty()
    private val title = arguments.getString("marginFilm").orEmpty()

    @After
    fun network() {
        airplane(false)
    }

    @Test
    fun aTranscodedFilmCrossesTwoMinutesWithoutNetwork() = cross(PlayMethod.Transcode)

    @Test
    fun aFilmPlayedAsItIsCrossesTwoMinutesWithoutNetwork() = cross(PlayMethod.DirectPlay)

    private fun cross(method: PlayMethod) =
        runBlocking {
            assumeTrue("marginFilm is not set", url.isNotBlank() && title.isNotBlank())
            val connector = JellyfinConnector(ClientIdentity("0.0.0-test", "cine-doury-margin", "Phone emulator"))
            val session = connector.signIn(ServerAddress.of(url), Credentials.Password("admin", "cinedoury-test"))
            val source = connector.open(session)
            val films = source.libraries().single { it.name == "Films" }.id
            val film =
                source
                    .items(ItemQuery(films, setOf(ItemKind.Movie)), PageRequest(limit = 200))
                    .items
                    .single { it.title == title }
            val device = DeviceCapabilitiesReader(context, allowPassthrough = false).read()
            // Below the bitrate of the file, the server must transcode; without a limit, it is played as it is.
            val options =
                if (method ==
                    PlayMethod.Transcode
                ) {
                    ProfileOptions(maxBitrate = TRANSCODED)
                } else {
                    ProfileOptions()
                }
            val stream =
                source.playback.playbackInfo(
                    PlaybackInfoRequest(
                        film.id,
                        PlaybackProfiles.build(device, options),
                        maxBitrate = options.maxBitrate,
                    ),
                )
            assertEquals(method, stream.method, "${stream.reasons}")
            val memory = context.getSystemService(ActivityManager::class.java).memoryClass
            val network = AndroidNetworkMonitor(context)
            val label = method.name
            withContext(Dispatchers.Main) {
                val player =
                    ExoVideoPlayer(
                        context,
                        device,
                        emptySet(),
                        matchFrameRate = false,
                        buffer = PlayerBuffer.phone(memory),
                        nomad = Nomad(disk(), network, { url }, margin = TARGET),
                    )
                val texture = SurfaceTexture(0)
                val surface = Surface(texture)
                player.player.setVideoSurface(surface)
                var failure: PlayerEvent.Failed? = null
                val watcher =
                    launch { failure = player.events.first { it is PlayerEvent.Failed } as PlayerEvent.Failed }
                val clock = TimeSource.Monotonic.markNow()

                fun look(phase: String): MarginStatus? {
                    val snapshot = player.snapshot()
                    val margin = snapshot.margin
                    Log.i(
                        LOG_TAG,
                        "margin $label t=${clock.elapsedNow().inWholeSeconds}s phase=$phase " +
                            "position=${snapshot.position.inWholeSeconds}s ahead=${margin?.ahead?.inWholeSeconds}s " +
                            "memory=${margin?.inMemory?.inWholeSeconds}s bandwidth=${margin?.bandwidth} " +
                            "offline=${margin?.offline} buffering=${snapshot.buffering} playing=${snapshot.playing}",
                    )
                    return margin
                }
                try {
                    player.load(stream, Duration.ZERO, null, null)
                    // 1. The margin grows past READY while the film plays.
                    val ready =
                        waitUntil(BUILD_TIMEOUT) {
                            val margin = look("build")
                            assertNull(failure, "$label failed: ${failure?.detail}")
                            margin != null && margin.ahead >= READY
                        }
                    assertTrue(ready, "$label: the margin never reached $READY")
                    // 2. No network: the film goes on, the player never waits.
                    val before = player.snapshot().position
                    airplane(true)
                    assertTrue(
                        waitUntil(NETWORK_TIMEOUT) { !network.state.value.online },
                        "$label: the network is still up",
                    )
                    val cut = TimeSource.Monotonic.markNow()
                    while (cut.elapsedNow() < OFFLINE) {
                        val snapshot = player.snapshot()
                        look("offline")
                        assertNull(failure, "$label failed offline: ${failure?.detail}")
                        if (snapshot.buffering) {
                            fail(
                                "$label: the player waited at ${snapshot.position} without network",
                            )
                        }
                        delay(LOOK)
                    }
                    val played = player.snapshot().position - before
                    assertTrue(played >= OFFLINE - SLACK, "$label: only $played played in $OFFLINE without network")
                    // 3. The network back: the margin grows again by itself.
                    val low = player.snapshot().margin?.end ?: Duration.ZERO
                    airplane(false)
                    val grows =
                        waitUntil(RESUME_TIMEOUT) {
                            val margin = look("back")
                            margin != null && !margin.offline && margin.end >= low + GROWTH
                        }
                    assertTrue(grows, "$label: the margin did not grow again once the network came back")
                    assertNull(failure, "$label failed: ${failure?.detail}")
                } finally {
                    watcher.cancel()
                    player.release()
                    surface.release()
                    texture.release()
                }
            }
            connector.signOut(session)
        }

    /** One cache per process, as in the app (Media3 refuses two on a folder). */
    private fun disk(): PlaybackCache = cache ?: PlaybackCache(context).also { cache = it }

    private suspend fun waitUntil(
        timeout: Duration,
        condition: () -> Boolean,
    ): Boolean {
        val start = TimeSource.Monotonic.markNow()
        while (start.elapsedNow() < timeout) {
            if (condition()) return true
            delay(LOOK)
        }
        return false
    }

    /** Airplane mode by the shell of the instrumentation; its output is read whole, then closed. */
    private fun airplane(on: Boolean) {
        val command = "cmd connectivity airplane-mode ${if (on) "enable" else "disable"}"
        instrumentation.uiAutomation.executeShellCommand(command).use { output ->
            android.os.ParcelFileDescriptor
                .AutoCloseInputStream(output)
                .use { it.readBytes() }
        }
        Log.i(LOG_TAG, "margin airplane=$on")
    }

    private companion object {
        var cache: PlaybackCache? = null

        /** N of the task: more than the 3 min the throttling of Jellyfin would allow ahead of the download. */
        val READY = 4.minutes
        val TARGET = 6.minutes
        val OFFLINE = 130.seconds
        val SLACK = 10.seconds
        val GROWTH = 30.seconds
        val BUILD_TIMEOUT = 5.minutes
        val NETWORK_TIMEOUT = 20.seconds
        val RESUME_TIMEOUT = 2.minutes
        val LOOK = 1_000.milliseconds
        const val TRANSCODED = 800_000
    }
}
