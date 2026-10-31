package fr.guim.cinedoury.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** When the player says the device has no decoder, and asks the server to transcode (`SPEC.md` § 4.11). */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DecoderCheckTest {
    private val h264 =
        Format
            .Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setCodecs("avc1.424029")
            .build()

    /** An MP3 copied into fMP4, as the master playlist of Jellyfin 12.3 announces it. */
    private val mp3InFmp4 =
        Format
            .Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setCodecs("mp4a.40.34")
            .build()

    private val dts =
        Format
            .Builder()
            .setSampleMimeType(MimeTypes.AUDIO_DTS)
            .setCodecs("dtsc")
            .build()

    private val av1 =
        Format
            .Builder()
            .setSampleMimeType(MimeTypes.VIDEO_AV1)
            .setCodecs("av01.0.13M.10")
            .build()

    private fun group(
        format: Format,
        support: Int,
    ) = Tracks.Group(TrackGroup(format), false, intArrayOf(support), booleanArrayOf(false))

    private fun tracks(vararg groups: Tracks.Group) = Tracks(groups.toList())

    @Test
    fun `a track that only exceeds the advertised capabilities is not a missing decoder`() {
        // The case of the AVI (MPEG-4 + MP3) transcoded by Jellyfin: H.264 and the MP3 copied, which
        // Media3 reads from the playlist as an AAC of unknown profile.
        val tracks =
            tracks(
                group(h264, C.FORMAT_HANDLED),
                group(mp3InFmp4, C.FORMAT_EXCEEDS_CAPABILITIES),
            )
        assertNull(DecoderCheck.missing(tracks))
    }

    @Test
    fun `a video beyond the advertised resolution is not a missing decoder either`() {
        assertNull(DecoderCheck.missing(tracks(group(h264, C.FORMAT_EXCEEDS_CAPABILITIES))))
    }

    @Test
    fun `a video without any decoder asks for the transcode`() {
        val tracks = tracks(group(av1, C.FORMAT_UNSUPPORTED_SUBTYPE), group(mp3InFmp4, C.FORMAT_HANDLED))
        assertEquals("video av01.0.13M.10", DecoderCheck.missing(tracks))
    }

    @Test
    fun `audio without any decoder for every track asks for the transcode`() {
        val tracks = tracks(group(h264, C.FORMAT_HANDLED), group(dts, C.FORMAT_UNSUPPORTED_SUBTYPE))
        assertEquals("audio ${MimeTypes.AUDIO_DTS}", DecoderCheck.missing(tracks))
    }

    @Test
    fun `one playable audio track is enough`() {
        val tracks =
            tracks(
                group(h264, C.FORMAT_HANDLED),
                group(dts, C.FORMAT_UNSUPPORTED_SUBTYPE),
                group(mp3InFmp4, C.FORMAT_EXCEEDS_CAPABILITIES),
            )
        assertNull(DecoderCheck.missing(tracks))
    }
}
