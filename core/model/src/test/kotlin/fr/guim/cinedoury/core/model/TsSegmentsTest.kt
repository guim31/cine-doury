package fr.guim.cinedoury.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An H.264 copied into fMP4 segments by Jellyfin can start before zero, which Media3 refuses (G, M2):
 * MPEG-TS segments for it, at once, or after the failure.
 */
class TsSegmentsTest {
    private fun stream(
        video: String,
        method: PlayMethod,
        container: String,
        reasons: Set<TranscodeReason> = emptySet(),
    ) = PlaybackStream(
        url = "http://192.0.2.10:8096/videos/1/master.m3u8",
        method = method,
        reasons = reasons,
        playSessionId = "s",
        source =
            MediaSource(
                id = "1",
                name = null,
                container = "mkv",
                sizeBytes = null,
                streams = listOf(MediaStream(0, StreamType.Video, video), MediaStream(1, StreamType.Audio, "dts")),
            ),
        container = container,
        isHls = true,
        subtitles = emptyList(),
        audioIndex = 1,
        subtitleIndex = null,
        maxBitrate = null,
    )

    @Test
    fun `a copied H264 in fMP4 asks for TS, an encoded or HEVC video stays in fMP4`() {
        val audioOnly = setOf(TranscodeReason.AudioCodecNotSupported)
        assertTrue(PlaybackFallback.wantsTs(stream("h264", PlayMethod.Transcode, "mp4", audioOnly)))
        assertTrue(PlaybackFallback.wantsTs(stream("h264", PlayMethod.DirectStream, "mp4")))
        // What Jellyfin 12.3 says for an MKV with DTS on a phone: the container, the audio; the video is copied.
        val mkvDts = setOf(TranscodeReason.ContainerNotSupported, TranscodeReason.AudioCodecNotSupported)
        assertTrue(PlaybackFallback.wantsTs(stream("h264", PlayMethod.Transcode, "mp4", mkvDts)))
        assertFalse(
            PlaybackFallback.wantsTs(
                stream("h264", PlayMethod.Transcode, "mp4", setOf(TranscodeReason.ContainerBitrateExceedsLimit)),
            ),
        )
        assertFalse(PlaybackFallback.wantsTs(stream("h264", PlayMethod.Transcode, "ts", audioOnly)))
        assertFalse(PlaybackFallback.wantsTs(stream("hevc", PlayMethod.DirectStream, "mp4")))
        assertFalse(
            PlaybackFallback.wantsTs(
                stream("h264", PlayMethod.Transcode, "mp4", setOf(TranscodeReason.VideoCodecNotSupported)),
            ),
        )
    }

    @Test
    fun `fMP4 that cannot be read is asked again in TS once, then the usual fallback`() {
        val remux = stream("hevc", PlayMethod.DirectStream, "mp4")
        assertEquals(FallbackAction.TsSegments, PlaybackFallback.next(remux, PlaybackFailure.Format, tsTried = false))
        assertEquals(FallbackAction.Transcode, PlaybackFallback.next(remux, PlaybackFailure.Format, tsTried = true))
        assertEquals(FallbackAction.ShowError, PlaybackFallback.next(remux, PlaybackFailure.Network, tsTried = false))
    }

    @Test
    fun `the TS profile asks H264 only`() {
        val profile =
            PlaybackProfile(
                "Phone",
                4_000_000,
                listOf("mkv"),
                emptyList(),
                listOf("aac"),
                2,
                listOf("hevc", "h264"),
                listOf("aac"),
                "mp4",
                emptyList(),
            )
        val ts = PlaybackFallback.tsProfile(profile)
        assertEquals("ts", ts.transcodeContainer)
        assertEquals(listOf("h264"), ts.transcodeVideoCodecs)
    }
}
