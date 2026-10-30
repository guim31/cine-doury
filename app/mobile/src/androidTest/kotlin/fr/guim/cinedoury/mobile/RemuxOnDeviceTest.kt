package fr.guim.cinedoury.mobile

import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.ItemQuery
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.model.PlaybackFailure
import fr.guim.cinedoury.core.model.PlaybackFallback
import fr.guim.cinedoury.core.model.PlaybackInfoRequest
import fr.guim.cinedoury.core.model.PlaybackProfiles
import fr.guim.cinedoury.core.model.PlaybackStream
import fr.guim.cinedoury.core.model.fmp4Segments
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
 * G (M2, `SPEC.md` § 4.27), on the phone emulator of the full CI: « Bretelle », an MKV in H.264 with
 * B-frames and DTS audio that `ci/emulator-margin.sh` adds to the Jellyfin of the CI. The phone takes no
 * DTS: the server copies the video and encodes the audio. Asked in fMP4 segments, as a device with a
 * HEVC decoder does, the stream must be asked again in MPEG-TS and play; and the fMP4 one, if Media3
 * refuses it, must fail as a format error (the fallback), never as a lost network. Skipped without the
 * `remuxFilm` argument.
 */
@RunWith(AndroidJUnit4::class)
class RemuxOnDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val url = arguments.getString("jellyfinUrl").orEmpty()
    private val title = arguments.getString("remuxFilm").orEmpty()

    @Test
    fun aCopiedH264WithBFramesPlaysInTsSegments() =
        runBlocking {
            assumeTrue("remuxFilm is not set", url.isNotBlank() && title.isNotBlank())
            val connector = JellyfinConnector(ClientIdentity("0.0.0-test", "cine-doury-remux", "Phone emulator"))
            val session = connector.signIn(ServerAddress.of(url), Credentials.Password("admin", "cinedoury-test"))
            val source = connector.open(session)
            val films = source.libraries().single { it.name == "Films" }.id
            val film =
                source
                    .items(ItemQuery(films, setOf(ItemKind.Movie)), PageRequest(limit = 200))
                    .items
                    .single { it.title == title }
            val device = DeviceCapabilitiesReader(context, allowPassthrough = false).read()
            // The phone of the maintainer decodes HEVC: its profile asks fMP4 segments.
            val fmp4 = PlaybackProfiles.build(device).copy(transcodeContainer = "mp4")
            val offered = source.playback.playbackInfo(PlaybackInfoRequest(film.id, fmp4))
            Log.i(LOG_TAG, "remux offered ${offered.method} ${offered.container} ${offered.reasons}")
            assertTrue(offered.fmp4Segments, "fMP4 segments: ${offered.container}")
            assertTrue(PlaybackFallback.wantsTs(offered), "a copied H.264 in fMP4 asks TS: ${offered.reasons}")
            // The fMP4 stream: it may play (Media3 may one day take it); if not, a format error.
            val fmp4Failure = play(offered)
            Log.i(LOG_TAG, "remux fMP4: ${fmp4Failure?.let { "${it.failure} ${it.detail}" } ?: "played"}")
            if (fmp4Failure != null) assertEquals(PlaybackFailure.Format, fmp4Failure.failure, fmp4Failure.detail)
            val ts = source.playback.playbackInfo(PlaybackInfoRequest(film.id, PlaybackFallback.tsProfile(fmp4)))
            assertEquals("ts", ts.container?.lowercase(), "${ts.reasons}")
            val tsFailure = play(ts)
            assertNull(tsFailure, "TS segments failed: ${tsFailure?.detail}")
            connector.signOut(session)
        }

    /** Plays [stream] until [TARGET]; the failure, if any. */
    private suspend fun play(stream: PlaybackStream): PlayerEvent.Failed? =
        withContext(Dispatchers.Main) {
            val device = DeviceCapabilitiesReader(context, allowPassthrough = false).read()
            val player = ExoVideoPlayer(context, device, emptySet(), matchFrameRate = false)
            val texture = SurfaceTexture(0)
            val surface = Surface(texture)
            player.player.setVideoSurface(surface)
            var failure: PlayerEvent.Failed? = null
            val watcher = launch { failure = player.events.first { it is PlayerEvent.Failed } as PlayerEvent.Failed }
            player.load(stream, Duration.ZERO, null, null)
            val reached =
                withTimeoutOrNull(TIMEOUT) {
                    while (player.snapshot().position < TARGET && failure == null) delay(POLL)
                    true
                }
            watcher.cancel()
            player.release()
            surface.release()
            texture.release()
            failure
                ?: if (reached == true) null else PlayerEvent.Failed(PlaybackFailure.Unknown, "never reached $TARGET")
        }

    private companion object {
        val TARGET = 5.seconds
        val TIMEOUT = 60.seconds
        val POLL = 200.milliseconds
    }
}
