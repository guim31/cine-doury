package fr.guim.cinedoury.tv

import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
 * The false playback error (`SPEC.md` § 4.11): an AVI with MPEG-4 Part 2 video and MP3 audio, which the
 * server transcodes to H.264 while copying the MP3 into fMP4 segments, as for a Chromecast. Its
 * playlist announces the MP3 as `mp4a.40.34`, which Media3 reads as an AAC of unknown profile: the
 * film must play all the same, and no failure be said. The film is added by `ci/emulator-avi.sh`.
 */
@RunWith(AndroidJUnit4::class)
class AviOnDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val url = InstrumentationRegistry.getArguments().getString("jellyfinUrl").orEmpty()

    @Test
    fun anAviWithMp3PlaysTranscodedWithItsMp3Copied() =
        runBlocking {
            assumeTrue("jellyfinUrl is not set", url.isNotBlank())
            val connector = JellyfinConnector(ClientIdentity("0.0.0-test", "cine-doury-emulator-avi", "Emulator"))
            val session = connector.signIn(ServerAddress.of(url), Credentials.Password("admin", "cinedoury-test"))
            val source = connector.open(session)
            val films = source.libraries().single { it.name == "Films" }.id
            val film =
                source.items(ItemQuery(films, setOf(ItemKind.Movie)), PageRequest(limit = 100)).items.single {
                    it.title == TITLE
                }
            val device = DeviceCapabilitiesReader(context).read()
            // As on a Chromecast: H.264 asked in fMP4 segments, the MP3 copied as it is.
            val profile =
                PlaybackProfiles
                    .build(device.copy(videoDecoders = device.videoDecoders.filter { it.codec == VideoCodec.H264 }))
                    .copy(transcodeVideoCodecs = listOf("h264"), transcodeContainer = "mp4")
            val audio = profile.transcodeAudioCodecs
            assertTrue("mp3" in audio, "the emulator decodes MP3: $audio")
            val stream = source.playback.playbackInfo(PlaybackInfoRequest(film.id, profile))
            assertEquals(PlayMethod.Transcode, stream.method, "${stream.reasons}")
            assertTrue(stream.reasons.none { it.isAudio }, "the MP3 is copied: ${stream.reasons}")
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
                    // A little more: a false failure said once the tracks are known would come now.
                    if (failure == null) delay(SETTLE)
                    watcher.cancel()
                    player.release()
                    surface.release()
                    texture.release()
                    assertNull(failure, "$TITLE failed")
                    position
                }
            assertTrue(reached != null && reached >= TARGET, "$TITLE reached $reached")
        }

    private companion object {
        const val TITLE = "Magnétoscope"
        val TARGET = 3.seconds
        val TIMEOUT = 90.seconds
        val SETTLE = 1.seconds
        val POLL = 200.milliseconds
    }
}
