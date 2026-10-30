package fr.guim.cinedoury.core.playback

import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.upstream.Loader
import fr.guim.cinedoury.core.model.PlaybackFailure
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the player makes of an error of ExoPlayer (G, M2): what fails to be read is not the network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackErrorsTest {
    private fun source(
        error: IOException,
        code: Int = PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    ) = ExoPlaybackException.createForSource(error, code)

    @Test
    fun `fMP4 segments starting before zero are a format error, not a lost connection`() {
        // Seen on a phone: a remux of an H.264 with B-frames, Jellyfin's -copyts, a negative tfdt.
        val error = source(Loader.UnexpectedLoaderException(IllegalStateException("Top bit not zero: -256")))
        assertEquals(PlaybackFailure.Format, PlaybackErrors.failureOf(error))
        assertTrue("Top bit not zero" in PlaybackErrors.detail(error))
    }

    @Test
    fun `a parser that gives up inside a loader is a format error too`() {
        val error =
            source(Loader.UnexpectedLoaderException(ParserException.createForMalformedContainer("bad box", null)))
        assertEquals(PlaybackFailure.Format, PlaybackErrors.failureOf(error))
    }

    @Test
    fun `the network and the server stay what they are`() {
        val lost =
            source(
                HttpDataSource.HttpDataSourceException(
                    IOException("Connection reset"),
                    androidx.media3.datasource.DataSpec(android.net.Uri.parse("http://192.0.2.10:8096/x")),
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    HttpDataSource.HttpDataSourceException.TYPE_OPEN,
                ),
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            )
        assertEquals(PlaybackFailure.Network, PlaybackErrors.failureOf(lost))
        val server = source(IOException("500"), PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
        assertEquals(PlaybackFailure.Server, PlaybackErrors.failureOf(server))
    }
}
