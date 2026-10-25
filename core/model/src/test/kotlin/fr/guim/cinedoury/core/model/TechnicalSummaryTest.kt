package fr.guim.cinedoury.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class TechnicalSummaryTest {
    private fun source(
        id: String,
        vararg streams: MediaStream,
    ) = MediaSource(id, null, "mkv", 1_000, streams.toList())

    @Test
    fun `badges of a 4K Dolby Vision film with Atmos, French and English`() {
        val summary =
            TechnicalSummary.of(
                listOf(
                    source(
                        "uhd",
                        MediaStream(
                            0,
                            StreamType.Video,
                            "hevc",
                            width = 3840,
                            height = 1608,
                            videoRange = VideoRange.DolbyVision,
                        ),
                        MediaStream(
                            1,
                            StreamType.Audio,
                            "truehd",
                            language = "eng",
                            channels = 8,
                            profile = "Dolby TrueHD + Dolby Atmos",
                        ),
                        MediaStream(2, StreamType.Audio, "eac3", language = "fre", channels = 6),
                        MediaStream(3, StreamType.Subtitle, "pgssub", language = "fra"),
                    ),
                    source("hd", MediaStream(0, StreamType.Video, "h264", width = 1920, videoRange = VideoRange.Sdr)),
                ),
            )
        assertEquals(
            listOf(
                TechBadge.Uhd,
                TechBadge.DolbyVision,
                TechBadge.Atmos,
                TechBadge.Surround71,
                TechBadge.LocalAudio,
                TechBadge.Subtitled,
            ),
            summary.badges(),
        )
        assertEquals(setOf(DynamicRange.DolbyVision, DynamicRange.Sdr), summary.ranges)
        assertEquals(setOf(VideoCodec.Hevc, VideoCodec.H264), summary.videoCodecs)
        assertEquals(2, summary.versions)
        assertEquals(2_000, summary.sizeBytes)
    }

    @Test
    fun `DTS family and channels`() {
        fun features(
            codec: String,
            profile: String?,
            channels: Int,
            spatial: SpatialAudio? = null,
        ) = MediaStream(
            1,
            StreamType.Audio,
            codec,
            channels = channels,
            profile = profile,
            spatialAudio = spatial,
        ).audioFeatures
        assertEquals(setOf(AudioFeature.Dts, AudioFeature.Surround51), features("dts", null, 6))
        assertEquals(setOf(AudioFeature.DtsHd, AudioFeature.Surround71), features("dts", "DTS-HD MA", 8))
        assertEquals(
            setOf(AudioFeature.DtsX, AudioFeature.DtsHd, AudioFeature.Surround71),
            features("dts", "DTS-HD MA", 8, SpatialAudio.DtsX),
        )
        assertEquals(setOf(AudioFeature.Stereo), features("aac", "LC", 2))
    }

    @Test
    fun `a foreign film without French subtitles is « VO »`() {
        val summary =
            TechnicalSummary.of(
                listOf(
                    source(
                        "a",
                        MediaStream(0, StreamType.Video, "h264", width = 1280),
                        MediaStream(1, StreamType.Audio, "ac3", language = "jpn", channels = 2),
                    ),
                ),
            )
        assertEquals(listOf(TechBadge.Hd, TechBadge.Original), summary.badges())
        assertEquals(listOf(TechBadge.Hd, TechBadge.LocalAudio), summary.badges(language = "jpn"))
    }
}
