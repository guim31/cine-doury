package fr.guim.cinedoury.tv

import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.guim.cinedoury.core.model.DeviceCapabilities
import fr.guim.cinedoury.core.model.DynamicRange
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.ItemQuery
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.model.PlayMethod
import fr.guim.cinedoury.core.model.PlaybackInfoRequest
import fr.guim.cinedoury.core.model.PlaybackProfiles
import fr.guim.cinedoury.core.model.VideoCodec
import fr.guim.cinedoury.core.playback.DeviceCapabilitiesReader
import fr.guim.cinedoury.core.playback.ExoVideoPlayer
import fr.guim.cinedoury.core.playback.PlayerEvent
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.LibrarySource
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
 * Real playback on the emulator of the CI (`SPEC.md` § 4.11), against the test Jellyfin: an H.264
 * film played directly, and the HEVC HDR10 film transcoded for a device without HEVC nor HDR. Each
 * must advance by 2 s. Skipped without the `jellyfinUrl` argument of the instrumentation.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackOnDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val url = InstrumentationRegistry.getArguments().getString("jellyfinUrl").orEmpty()

    private suspend fun source(): LibrarySource {
        val connector = JellyfinConnector(ClientIdentity("0.0.0-test", "cine-doury-emulator", "Emulator"))
        val session = connector.signIn(ServerAddress.of(url), Credentials.Password("admin", "cinedoury-test"))
        return connector.open(session)
    }

    @Test
    fun anH264FilmPlaysDirectly() = play("Le Phare", DeviceCapabilitiesReader(context).read(), PlayMethod.DirectPlay)

    @Test
    fun anHevcHdrFilmIsTranscodedForADeviceWithoutHevc() {
        val device = DeviceCapabilitiesReader(context).read()
        val limited =
            device.copy(
                videoDecoders = device.videoDecoders.filter { it.codec == VideoCodec.H264 },
                display = device.display.copy(hdr = emptySet<DynamicRange>()),
            )
        play("Nuit polaire", limited, PlayMethod.Transcode)
    }

    private fun play(
        title: String,
        device: DeviceCapabilities,
        expected: PlayMethod,
    ) = runBlocking {
        assumeTrue("jellyfinUrl is not set", url.isNotBlank())
        val source = source()
        val films = source.libraries().single { it.name == "Films" }.id
        val found =
            source.items(ItemQuery(films, setOf(ItemKind.Movie)), PageRequest(limit = 100)).items.single {
                it.title ==
                    title
            }
        val profile = PlaybackProfiles.build(device)
        val stream = source.playback.playbackInfo(PlaybackInfoRequest(found.id, profile))
        assertEquals(expected, stream.method, "$title: ${stream.reasons}")
        val reached =
            withContext(Dispatchers.Main) {
                val player = ExoVideoPlayer(context, device, emptySet(), matchFrameRate = false)
                val texture = SurfaceTexture(0)
                val surface = Surface(texture)
                player.player.setVideoSurface(surface)
                var failure: PlayerEvent.Failed? = null
                val watcher =
                    launch { failure = player.events.first { it is PlayerEvent.Failed } as PlayerEvent.Failed }
                player.load(stream, Duration.ZERO, null, null)
                val position =
                    withTimeoutOrNull(TIMEOUT) {
                        while (player.snapshot().position < TARGET && failure == null) delay(POLL)
                        player.snapshot().position
                    }
                watcher.cancel()
                player.release()
                surface.release()
                texture.release()
                assertNull(failure, "$title failed")
                position
            }
        assertTrue(reached != null && reached >= TARGET, "$title reached $reached")
    }

    private companion object {
        val TARGET = 2.seconds
        val TIMEOUT = 60.seconds
        val POLL = 200.milliseconds
    }
}
