package fr.guim.cinedoury.core.source.fake

import fr.guim.cinedoury.core.model.AudioCodec
import fr.guim.cinedoury.core.model.DeviceCapabilities
import fr.guim.cinedoury.core.model.DisplayCapabilities
import fr.guim.cinedoury.core.model.DynamicRange
import fr.guim.cinedoury.core.model.PassthroughFormat
import fr.guim.cinedoury.core.model.VideoCodec
import fr.guim.cinedoury.core.model.VideoDecoderInfo

/**
 * Typical devices, as their capabilities would be read (`SPEC.md` § 4.11): for the tests of the
 * profile, of the decision, and of the screens.
 */
object SampleDevices {
    private val PLATFORM_AUDIO =
        setOf(AudioCodec.Aac, AudioCodec.Mp3, AudioCodec.Opus, AudioCodec.Flac, AudioCodec.Vorbis, AudioCodec.Pcm)

    /** NVIDIA Shield TV: 4K, HDR10 and Dolby Vision, behind an Atmos and DTS amplifier. */
    val shield =
        DeviceCapabilities(
            device = "NVIDIA SHIELD Android TV",
            system = "11 (API 30)",
            videoDecoders =
                listOf(
                    VideoDecoderInfo(
                        VideoCodec.H264,
                        "OMX.Nvidia.h264.decode",
                        true,
                        setOf("baseline", "main", "high"),
                        52,
                        4096,
                        2304,
                    ),
                    VideoDecoderInfo(
                        VideoCodec.Hevc,
                        "OMX.Nvidia.h265.decode",
                        true,
                        setOf("main", "main 10"),
                        153,
                        4096,
                        2304,
                        10,
                    ),
                    VideoDecoderInfo(
                        VideoCodec.Vp9,
                        "OMX.Nvidia.vp9.decode",
                        true,
                        setOf("profile 0", "profile 2"),
                        null,
                        4096,
                        2304,
                        10,
                    ),
                    VideoDecoderInfo(
                        VideoCodec.Mpeg2,
                        "OMX.Nvidia.mpeg2v.decode",
                        true,
                        setOf("main"),
                        null,
                        1920,
                        1088,
                    ),
                    VideoDecoderInfo(VideoCodec.Vc1, "OMX.Nvidia.vc1.decode", true, emptySet(), null, 1920, 1088),
                    VideoDecoderInfo(
                        VideoCodec.H264,
                        "c2.android.avc.decoder",
                        false,
                        setOf("baseline", "main", "high"),
                        52,
                        4096,
                        2304,
                    ),
                ),
            dolbyVisionProfiles = setOf(5, 8),
            audioDecoders = PLATFORM_AUDIO + setOf(AudioCodec.Ac3, AudioCodec.Eac3),
            passthrough = PassthroughFormat.entries.toSet(),
            maxPcmChannels = 8,
            display =
                DisplayCapabilities(
                    3840,
                    2160,
                    listOf(23.976f, 24f, 25f, 29.97f, 50f, 59.94f, 60f),
                    setOf(DynamicRange.Hdr10, DynamicRange.Hlg, DynamicRange.DolbyVision),
                ),
        )

    /** Chromecast with Google TV: 4K HDR10, E-AC3 to the TV, neither DTS nor TrueHD. */
    val chromecast =
        DeviceCapabilities(
            device = "Chromecast",
            system = "12 (API 31)",
            videoDecoders =
                listOf(
                    VideoDecoderInfo(
                        VideoCodec.H264,
                        "c2.amlogic.avc.decoder",
                        true,
                        setOf("baseline", "main", "high"),
                        51,
                        3840,
                        2160,
                    ),
                    VideoDecoderInfo(
                        VideoCodec.Hevc,
                        "c2.amlogic.hevc.decoder",
                        true,
                        setOf("main", "main 10"),
                        153,
                        3840,
                        2160,
                        10,
                    ),
                    VideoDecoderInfo(
                        VideoCodec.Vp9,
                        "c2.amlogic.vp9.decoder",
                        true,
                        setOf("profile 0", "profile 2"),
                        null,
                        3840,
                        2160,
                        10,
                    ),
                    VideoDecoderInfo(
                        VideoCodec.Av1,
                        "c2.amlogic.av1.decoder",
                        true,
                        setOf("main"),
                        null,
                        3840,
                        2160,
                        10,
                    ),
                ),
            audioDecoders = PLATFORM_AUDIO + setOf(AudioCodec.Ac3, AudioCodec.Eac3),
            passthrough = setOf(PassthroughFormat.Ac3, PassthroughFormat.Eac3, PassthroughFormat.Eac3Atmos),
            maxPcmChannels = 6,
            display =
                DisplayCapabilities(
                    3840,
                    2160,
                    listOf(50f, 60f),
                    setOf(DynamicRange.Hdr10, DynamicRange.Hdr10Plus, DynamicRange.Hlg),
                ),
        )

    /**
     * Chromecast with Google TV under Android 14 on a 1080p SDR television (P, `SPEC.md` § 4.29): the
     * same decoders, no E-AC3 decoder, nothing to an amplifier; Android announces HDR10
     * ([DisplayCapabilities.announcedHdr]) that the mode of the screen does not list.
     */
    val chromecastSdr =
        chromecast.copy(
            system = "14 (API 34)",
            audioDecoders = PLATFORM_AUDIO,
            passthrough = emptySet(),
            maxPcmChannels = 2,
            display =
                DisplayCapabilities(
                    1920,
                    1080,
                    listOf(50f, 60f),
                    hdr = emptySet(),
                    announcedHdr = setOf(DynamicRange.Hdr10),
                ),
        )

    /** A phone held upright: its screen is read in its own orientation, 1080 × 2400. */
    val phone =
        chromecast.copy(
            device = "Phone",
            system = "16 (API 36)",
            passthrough = emptySet(),
            maxPcmChannels = 2,
            display = DisplayCapabilities(1080, 2400, listOf(60f, 120f), setOf(DynamicRange.Hdr10)),
        )

    /** An old SD box: H.264 up to 720p, stereo, no HDR. */
    val standard =
        DeviceCapabilities(
            device = "Boîtier SD",
            system = "11 (API 30)",
            videoDecoders =
                listOf(
                    VideoDecoderInfo(
                        VideoCodec.H264,
                        "OMX.box.avc.decoder",
                        true,
                        setOf("baseline", "main", "high"),
                        41,
                        1280,
                        720,
                    ),
                ),
            audioDecoders = setOf(AudioCodec.Aac, AudioCodec.Mp3),
            maxPcmChannels = 2,
            display = DisplayCapabilities(1280, 720, listOf(60f)),
        )
}
