package fr.guim.cinedoury.core.source

import fr.guim.cinedoury.core.model.PassthroughFormat
import fr.guim.cinedoury.core.model.PlaybackProfiles
import fr.guim.cinedoury.core.model.ProfileOptions
import fr.guim.cinedoury.core.model.RangeType
import fr.guim.cinedoury.core.model.SubtitleMethod
import fr.guim.cinedoury.core.source.fake.SampleDevices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The profile of `:core:model` built from simulated devices (`SPEC.md` § 4.11). Here rather than in
 * `:core:model`, whose tests cannot see the sample devices of the test fixtures of this module.
 */
class PlaybackProfilesTest {
    @Test
    fun `a Shield plays HEVC Main 10 in 4K, HDR10 and Dolby Vision, and passes all audio through`() {
        val profile = PlaybackProfiles.build(SampleDevices.shield)
        val hevc = profile.video.single { it.codec == "hevc" }
        assertEquals(4096, hevc.maxWidth)
        assertEquals(10, hevc.maxBitDepth)
        assertEquals(153, hevc.maxLevel)
        assertTrue(RangeType.Hdr10 in hevc.ranges)
        assertTrue(RangeType.DolbyVision in hevc.ranges, "profile 5")
        assertTrue(RangeType.DolbyVisionWithHdr10 in hevc.ranges, "profile 8")
        assertFalse(RangeType.DolbyVisionWithEl in hevc.ranges, "no profile 7 decoder")
        assertTrue(listOf("ac3", "eac3", "dts", "truehd").all { it in profile.audioCodecs })
        assertEquals(8, profile.maxAudioChannels)
        assertEquals(listOf("hevc", "h264"), profile.transcodeVideoCodecs)
        assertEquals("mp4", profile.transcodeContainer)
        assertEquals("ac3", profile.transcodeAudioCodecs.first())
        // The hardware H.264 decoder wins over the software one.
        assertEquals(52, profile.video.single { it.codec == "h264" }.maxLevel)
        assertTrue(profile.video.any { it.codec == "vc1" })
    }

    @Test
    fun `a Chromecast plays HDR10 but neither Dolby Vision profile 5 nor DTS nor TrueHD`() {
        val profile = PlaybackProfiles.build(SampleDevices.chromecast)
        val hevc = profile.video.single { it.codec == "hevc" }
        assertTrue(RangeType.Hdr10 in hevc.ranges && RangeType.Hlg in hevc.ranges)
        assertFalse(RangeType.DolbyVision in hevc.ranges)
        // Profile 8 plays its HDR10 base layer.
        assertTrue(RangeType.DolbyVisionWithHdr10 in hevc.ranges)
        assertFalse("dts" in profile.audioCodecs)
        assertFalse("truehd" in profile.audioCodecs)
        assertTrue("eac3" in profile.audioCodecs)
        assertTrue(profile.video.any { it.codec == "av1" })
    }

    @Test
    fun `an SD box gets H264 up to 720p in SDR, stereo, and transcodes the rest to H264 and AAC`() {
        val profile = PlaybackProfiles.build(SampleDevices.standard)
        val h264 = profile.video.single()
        assertEquals("h264", h264.codec)
        assertEquals(1280, h264.maxWidth)
        assertEquals(listOf(RangeType.Sdr), h264.ranges)
        assertEquals(listOf("aac", "mp3"), profile.audioCodecs)
        assertEquals(2, profile.maxAudioChannels)
        assertEquals(listOf("h264"), profile.transcodeVideoCodecs)
        assertEquals("ts", profile.transcodeContainer)
        assertEquals("aac", profile.transcodeAudioCodecs.first())
    }

    @Test
    fun `passthrough turned off for DTS and TrueHD leaves them to the server`() {
        val options = ProfileOptions(passthrough = setOf(PassthroughFormat.Ac3, PassthroughFormat.Eac3))
        val profile = PlaybackProfiles.build(SampleDevices.shield, options)
        assertFalse("dts" in profile.audioCodecs)
        assertFalse("truehd" in profile.audioCodecs)
        assertTrue("eac3" in profile.audioCodecs)
    }

    @Test
    fun `ASS subtitles drawn by the server when asked`() {
        val drawn = PlaybackProfiles.build(SampleDevices.shield)
        assertTrue(drawn.subtitles.any { it.format == "ass" && it.method == SubtitleMethod.Embed })
        val burnt = PlaybackProfiles.build(SampleDevices.shield, ProfileOptions(burnInAss = true))
        assertNull(burnt.subtitles.firstOrNull { it.format == "ass" })
        assertTrue(burnt.subtitles.any { it.format == "pgssub" && it.method == SubtitleMethod.Embed })
        assertFalse(burnt.subtitles.any { it.format == "pgssub" && it.method == SubtitleMethod.External })
    }
}
