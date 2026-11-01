package fr.guim.cinedoury.core.model

/**
 * What the device can play, read on the device itself (`SPEC.md` § 4.11): its video decoders, the
 * audio it decodes, the audio its HDMI output passes to an amplifier, its screen.
 *
 * @param device the name of the model (« NVIDIA SHIELD Android TV »).
 * @param system the Android version (« 11 (API 30) »).
 * @param dolbyVisionProfiles the Dolby Vision profiles a Dolby Vision decoder declares (5, 7, 8…).
 * @param audioDecoders what the device decodes to PCM itself.
 * @param passthrough what the HDMI sink accepts as is (bitstream), as the output reports it now.
 * @param maxPcmChannels the channels of decoded audio the output takes (2, 6, 8).
 * @param subtitleFormats what the player draws itself.
 */
data class DeviceCapabilities(
    val device: String,
    val system: String,
    val videoDecoders: List<VideoDecoderInfo>,
    val dolbyVisionProfiles: Set<Int> = emptySet(),
    val audioDecoders: Set<AudioCodec>,
    val passthrough: Set<PassthroughFormat> = emptySet(),
    val maxPcmChannels: Int = STEREO,
    val display: DisplayCapabilities,
    val subtitleFormats: Set<SubtitleFormat> = SubtitleFormat.entries.toSet(),
) {
    /** The best decoder of [codec]: hardware first, then the largest pictures. */
    fun decoder(codec: VideoCodec): VideoDecoderInfo? =
        videoDecoders
            .filter { it.codec == codec }
            .maxWithOrNull(compareBy<VideoDecoderInfo> { it.hardware }.thenBy { it.maxWidth * it.maxHeight })

    private companion object {
        const val STEREO = 2
    }
}

/**
 * A video decoder of the device.
 *
 * @param profiles the profiles it declares, named as the server names them (« main 10 », « high »).
 * @param maxLevel the highest level it declares, on the scale of the server: 51 for H.264 level 5.1,
 * 153 for HEVC level 5.1 (30 × the level).
 * @param maxBitDepth 10 when it declares a 10-bit profile (HEVC Main 10, VP9 profile 2, AV1 Main 10).
 */
data class VideoDecoderInfo(
    val codec: VideoCodec,
    val name: String,
    val hardware: Boolean,
    val profiles: Set<String>,
    val maxLevel: Int?,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxBitDepth: Int = 8,
)

/**
 * The screen: its size and refresh rates, and the dynamic ranges it shows ([DynamicRange.Sdr]
 * always).
 */
data class DisplayCapabilities(
    val width: Int,
    val height: Int,
    val refreshRates: List<Float> = emptyList(),
    val hdr: Set<DynamicRange> = emptySet(),
    /**
     * What `Display.getHdrCapabilities()` announced, for the device profile and the report (P, `SPEC.md`
     * § 4.29): more than [hdr] when the current mode of the screen does not list it.
     */
    val announcedHdr: Set<DynamicRange> = hdr,
)

/** An audio format, named as ffmpeg and the server name it. */
enum class AudioCodec(
    val codecName: String,
) {
    Aac("aac"),
    Mp3("mp3"),
    Opus("opus"),
    Flac("flac"),
    Vorbis("vorbis"),
    Alac("alac"),
    Pcm("pcm_s16le"),
    Ac3("ac3"),
    Eac3("eac3"),
    Dts("dts"),
    TrueHd("truehd"),
}

/** What can go to an amplifier as is, through HDMI (`FONCTIONNALITES.md` § 5, « Son »). */
enum class PassthroughFormat(
    val codec: AudioCodec,
) {
    Ac3(AudioCodec.Ac3),
    Eac3(AudioCodec.Eac3),

    /** E-AC3 with Dolby Atmos objects (JOC). */
    Eac3Atmos(AudioCodec.Eac3),
    Dts(AudioCodec.Dts),
    DtsHd(AudioCodec.Dts),

    /** Dolby TrueHD, with or without Atmos. */
    TrueHd(AudioCodec.TrueHd),
}

/** A subtitle format, named as the server names it. */
enum class SubtitleFormat(
    val codecName: String,
    val text: Boolean,
) {
    Srt("srt", text = true),
    SubRip("subrip", text = true),
    Ass("ass", text = true),
    Ssa("ssa", text = true),
    WebVtt("vtt", text = true),
    Ttml("ttml", text = true),
    Pgs("pgssub", text = false),
    VobSub("dvdsub", text = false),
    Dvb("dvbsub", text = false),
}
