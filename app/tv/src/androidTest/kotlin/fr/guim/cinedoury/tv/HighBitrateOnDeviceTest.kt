package fr.guim.cinedoury.tv

import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.ItemQuery
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.model.PlayMethod
import fr.guim.cinedoury.core.model.PlaybackInfoRequest
import fr.guim.cinedoury.core.model.PlaybackProfiles
import fr.guim.cinedoury.core.playback.DeviceCapabilitiesReader
import fr.guim.cinedoury.core.playback.ExoVideoPlayer
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
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * R (`SPEC.md` § 4.29): the buffer of ExoPlayer is on the Java heap. A film at a constant 40 Mb/s,
 * played directly on the emulator with the buffer of the TV (40 % of the heap the process may grow to),
 * must fill its buffer up to the cap, never beyond, keep the heap clear of its limit, and play a
 * minute. The film is added by `ci/emulator-bitrate.sh`. Each 5 s, a line in the log (tag `HighBitrate`).
 */
@RunWith(AndroidJUnit4::class)
class HighBitrateOnDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val url = InstrumentationRegistry.getArguments().getString("jellyfinUrl").orEmpty()

    @Suppress("LongMethod") // One playback, watched second by second.
    @Test
    fun aFilmAt40MbpsPlaysAMinuteUnderTheBufferCap() =
        runBlocking {
            assumeTrue("jellyfinUrl is not set", url.isNotBlank())
            val connector = JellyfinConnector(ClientIdentity("0.0.0-test", "cine-doury-emulator-bitrate", "Emulator"))
            val session = connector.signIn(ServerAddress.of(url), Credentials.Password("admin", "cinedoury-test"))
            val source = connector.open(session)
            val films = source.libraries().single { it.name == "Films" }.id
            val film =
                source.items(ItemQuery(films, setOf(ItemKind.Movie)), PageRequest(limit = 100)).items.single {
                    it.title == TITLE
                }
            val device = DeviceCapabilitiesReader(context).read()
            val stream = source.playback.playbackInfo(PlaybackInfoRequest(film.id, PlaybackProfiles.build(device)))
            assertEquals(PlayMethod.DirectPlay, stream.method, "${stream.reasons}")
            val runtime = Runtime.getRuntime()
            val heap = runtime.maxMemory()
            val cap = PlayerBuffer.tv(heap).targetBytes.toLong()
            Log.i(TAG, "heap ${heap / MB} MB, buffer cap ${cap / MB} MB")
            var most = 0L
            var mostHeap = 0L
            val reached =
                withContext(Dispatchers.Main) {
                    // The default buffer of the player: the TV's, from the heap of this process.
                    val player = ExoVideoPlayer(context, device, emptySet(), matchFrameRate = false)
                    val texture = SurfaceTexture(0)
                    val surface = Surface(texture)
                    player.player.setVideoSurface(surface)
                    var failure: PlayerEvent.Failed? = null
                    val watcher =
                        launch { failure = player.events.first { it is PlayerEvent.Failed } as PlayerEvent.Failed }
                    player.load(stream, Duration.ZERO, null, null)
                    var ticks = 0
                    val position =
                        withTimeoutOrNull(TIMEOUT) {
                            while (player.snapshot().position < TARGET && failure == null) {
                                delay(POLL)
                                val snapshot = player.snapshot()
                                val used = runtime.totalMemory() - runtime.freeMemory()
                                most = maxOf(most, snapshot.bufferBytes)
                                mostHeap = maxOf(mostHeap, used)
                                if (ticks++ % LOG_EVERY == 0) {
                                    Log.i(
                                        TAG,
                                        "position ${snapshot.position.inWholeSeconds} s, " +
                                            "buffered ${snapshot.buffered.inWholeSeconds} s, " +
                                            "buffer ${snapshot.bufferBytes / MB} MB, " +
                                            "heap used ${used / MB} of ${heap / MB} MB, " +
                                            "${if (snapshot.buffering) "waiting" else "playing"}",
                                    )
                                }
                            }
                            player.snapshot().position
                        }
                    watcher.cancel()
                    player.release()
                    surface.release()
                    texture.release()
                    assertNull(failure, "$TITLE failed")
                    position
                }
            Log.i(TAG, "most: buffer ${most / MB} MB of ${cap / MB}, heap used ${mostHeap / MB} of ${heap / MB} MB")
            assertTrue(reached != null && reached >= TARGET, "$TITLE reached $reached")
            // The cap was reached (the network outruns 40 Mb/s on the emulator), and never passed by more
            // than a few blocks of 64 KB that a loader takes before it is told to stop.
            assertTrue(most >= cap - SLACK, "the buffer never reached its cap: ${most / MB} of ${cap / MB} MB")
            assertTrue(most <= cap + SLACK, "the buffer went past its cap: ${most / MB} of ${cap / MB} MB")
            assertTrue(mostHeap < heap * HEAP_LIMIT_PERCENT / PERCENT, "heap ${mostHeap / MB} of ${heap / MB} MB")
        }

    private companion object {
        const val TITLE = "Déferlante"
        const val TAG = "HighBitrate"
        const val MB = 1024L * 1024L
        const val SLACK = 2 * MB
        const val HEAP_LIMIT_PERCENT = 85
        const val PERCENT = 100
        const val LOG_EVERY = 10
        val TARGET = 60.seconds
        val TIMEOUT = 240.seconds
        val POLL = 500.milliseconds
    }
}
