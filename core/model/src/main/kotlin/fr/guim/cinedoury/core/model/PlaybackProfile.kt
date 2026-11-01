package fr.guim.cinedoury.core.model

/**
 * What the device asks the server for (`SPEC.md` § 4.11): the files it plays as they are, and what
 * the server should make of the others. Built from [DeviceCapabilities] by [PlaybackProfiles]; the
 * source turns it into its own form (Jellyfin: `DeviceProfile`).
 *
 * @param containers the containers played directly.
 * @param video one rule per video codec played directly.
 * @param audioCodecs the audio codecs played directly: decoded on the device or sent to the
 * amplifier.
 * @param maxAudioChannels the channels the device takes (decoded, or through the amplifier).
 * @param transcodeVideoCodecs what the server encodes to, the preferred first.
 * @param transcodeAudioCodecs what the server encodes audio to, the preferred first.
 * @param transcodeContainer the segments of the stream the server makes (HLS): `mp4` (fMP4) when
 * HEVC is allowed, `ts` otherwise.
 * @param subtitles how each subtitle format reaches the player; the others are drawn into the
 * picture by the server.
 * @param transcodeMaxWidth the widest picture the server encodes: the long side of the screen (O,
 * `SPEC.md` § 4.29). A picture played directly or copied keeps its size: the device scales it.
 * @param transcodeMaxHeight the tallest: the short side of the screen.
 */
data class PlaybackProfile(
    val name: String,
    val maxBitrate: Int,
    val containers: List<String>,
    val video: List<VideoRule>,
    val audioCodecs: List<String>,
    val maxAudioChannels: Int,
    val transcodeVideoCodecs: List<String>,
    val transcodeAudioCodecs: List<String>,
    val transcodeContainer: String,
    val subtitles: List<SubtitleRule>,
    val transcodeMaxWidth: Int = TranscodeBitrates.UHD_WIDTH,
    val transcodeMaxHeight: Int = TranscodeBitrates.UHD_HEIGHT,
) {
    /**
     * The most the server encodes at with the quality « Automatique » (O, `SPEC.md` § 4.29): by the
     * size of the screen, for the least efficient codec the server may choose; never more than
     * [maxBitrate]. A quality chosen by the user is not capped (the source leaves it out).
     */
    val transcodeAutoBitrate: Int
        get() = minOf(TranscodeBitrates.cap(transcodeVideoCodecs, transcodeMaxHeight), maxBitrate)
}

/**
 * The bitrate of a picture the server encodes again, with the quality « Automatique » (O, `SPEC.md`
 * § 4.29), by the height of the screen (the short side) and the codec. Jellyfin otherwise follows the
 * file: a 4K HEVC encoded again into H.264 came at 42 Mb/s for a 1080p screen. H.264 1080p at
 * 20 Mb/s is a clean Blu-ray-like picture; HEVC needs about 60 % of that.
 */
object TranscodeBitrates {
    const val UHD_WIDTH = 3840
    const val UHD_HEIGHT = 2160
    private const val HD = 720
    private const val FULL_HD = 1080
    private const val QHD = 1440

    /** Height of the screen → (H.264, HEVC). */
    private val STEPS =
        listOf(
            HD to (8_000_000 to 5_000_000),
            FULL_HD to (20_000_000 to 12_000_000),
            QHD to (30_000_000 to 18_000_000),
            Int.MAX_VALUE to (40_000_000 to 25_000_000),
        )

    /**
     * The cap for a screen [height] pixels tall, when the server encodes to one of [codecs]: the server
     * chooses among them (HEVC only if it may encode it), so the least efficient one sets the cap.
     */
    fun cap(
        codecs: List<String>,
        height: Int,
    ): Int {
        val (h264, hevc) = STEPS.first { height <= it.first }.second
        return if (codecs.isEmpty() || codecs.any { !it.equals("hevc", ignoreCase = true) }) h264 else hevc
    }
}

/**
 * A video codec played directly, within limits: the server transcodes what goes beyond them.
 *
 * @param codec the codec name of the server (`h264`, `hevc`, `av1`…).
 * @param profiles the profiles accepted (empty: any).
 * @param maxLevel on the scale of the server (see [VideoDecoderInfo.maxLevel]); null: any.
 * @param ranges the kinds of picture accepted as they are; the server tone maps the others.
 */
data class VideoRule(
    val codec: String,
    val profiles: List<String>,
    val maxLevel: Int?,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxBitDepth: Int,
    val ranges: List<RangeType>,
)

/**
 * The kind of picture a video stream carries, as the server tells them apart: Dolby Vision alone
 * (profile 5), or over a base layer that other players read (profile 7 with its enhancement layer,
 * profile 8 over HDR10, HLG or SDR).
 */
enum class RangeType(
    val serverName: String,
) {
    Sdr("SDR"),
    Hdr10("HDR10"),
    Hdr10Plus("HDR10Plus"),
    Hlg("HLG"),
    DolbyVision("DOVI"),
    DolbyVisionWithHdr10("DOVIWithHDR10"),
    DolbyVisionWithHlg("DOVIWithHLG"),
    DolbyVisionWithSdr("DOVIWithSDR"),
    DolbyVisionWithEl("DOVIWithEL"),
    DolbyVisionWithHdr10Plus("DOVIWithHDR10Plus"),
    DolbyVisionWithElHdr10Plus("DOVIWithELHDR10Plus"),
}

/** How a subtitle track reaches the player. */
enum class SubtitleMethod {
    /** In the file itself, which the player reads. */
    Embed,

    /** A separate file the server serves. */
    External,

    /** Drawn into the picture by the server (it transcodes the video). */
    Encode,

    /** In the HLS stream of the server. */
    Hls,

    /** Not delivered. */
    Drop,
}

data class SubtitleRule(
    val format: String,
    val method: SubtitleMethod,
)

/**
 * What the user allows on this device (`FONCTIONNALITES.md` § 12, « Image » and « Son »): the formats
 * sent to the amplifier, the bitrate, ASS subtitles drawn by the server rather than by the player,
 * the dynamic ranges and the channels.
 */
data class ProfileOptions(
    val passthrough: Set<PassthroughFormat> = PassthroughFormat.entries.toSet(),
    val maxBitrate: Int = MAX_BITRATE,
    val burnInAss: Boolean = false,
    /** HDR10, HDR10+ and HLG as they are; off, the server tone maps them (« Image », J7). */
    val allowHdr: Boolean = true,
    /** Dolby Vision as it is; off, its base layer or a tone mapping (« Image », J7). */
    val allowDolbyVision: Boolean = true,
    /** Two channels only, nothing to the amplifier (« Sortie stéréo forcée », J7). */
    val stereo: Boolean = false,
) {
    companion object {
        /** 120 Mb/s: a 4K Blu-ray remux. */
        const val MAX_BITRATE = 120_000_000
    }
}

/**
 * The profile of a device (`SPEC.md` § 4.11):
 * - each video codec with a decoder is played directly, within its profiles, level and size; a
 *   codec decoded in software only (HEVC, AV1, VP9) is limited to 1080p;
 * - the dynamic ranges are those of the screen: HDR10 (and HDR10+, whose base is HDR10), HLG, and
 *   Dolby Vision when a Dolby Vision decoder declares the profile (5, 7, 8) and the screen shows it;
 *   without them, a Dolby Vision profile 8 plays its base layer (HDR10, HLG or SDR) and profiles 5
 *   and 7 are tone mapped by the server;
 * - an audio codec is played directly when the device decodes it, or when the amplifier takes it
 *   and the user allows it;
 * - the server transcodes to HEVC (in fMP4) when a hardware HEVC Main 10 decoder exists, else to
 *   H.264 (in MPEG-TS); audio to AC3 when the amplifier or the device takes it in 5.1, else AAC;
 * - text subtitles are read in the file or served apart; image subtitles (PGS, VobSub) in the file;
 *   the server draws the rest into the picture (ASS too, if the user asks).
 */
object PlaybackProfiles {
    /** Containers ExoPlayer reads, as the server names them. */
    val CONTAINERS = listOf("mkv", "webm", "mp4", "m4v", "mov", "ts", "m2ts", "mts", "avi", "flv", "3gp", "ogv")

    private const val SOFTWARE_MAX_WIDTH = 1920
    private const val SOFTWARE_MAX_HEIGHT = 1088
    private const val TEN_BITS = 10
    private const val SURROUND = 6
    private const val DOLBY_VISION_DUAL_LAYER = 7
    private const val DOLBY_VISION_SINGLE_LAYER = 5
    private const val DOLBY_VISION_COMPATIBLE = 8

    /** The profiles the server should encode with first, the best compressed. */
    private val ENCODED_FIRST = listOf("high", "main")

    private val SOFTWARE_LIMITED = setOf(VideoCodec.Hevc, VideoCodec.Av1, VideoCodec.Vp9)
    private val CODEC_NAMES =
        mapOf(
            VideoCodec.H264 to "h264",
            VideoCodec.Hevc to "hevc",
            VideoCodec.Av1 to "av1",
            VideoCodec.Vp9 to "vp9",
            VideoCodec.Mpeg2 to "mpeg2video",
            VideoCodec.Vc1 to "vc1",
        )

    /** What an HLS segment carries: other audio codecs are never asked of the server. */
    private val HLS_AUDIO = listOf(AudioCodec.Ac3, AudioCodec.Eac3, AudioCodec.Aac, AudioCodec.Mp3)

    fun build(
        capabilities: DeviceCapabilities,
        options: ProfileOptions = ProfileOptions(),
    ): PlaybackProfile {
        val device = capabilities.allowed(options)
        val video =
            CODEC_NAMES.mapNotNull { (codec, name) ->
                device.decoder(codec)?.let { videoRule(device, it, name) }
            }
        val passthrough = device.passthrough intersect options.passthrough
        val passed = passthrough.map { it.codec }.toSet()
        val direct = device.audioDecoders + passed
        val surround = passed.isNotEmpty() || device.maxPcmChannels > 2
        val channels = if (passed.isNotEmpty()) SURROUND_71 else device.maxPcmChannels
        val hevc = device.decoder(VideoCodec.Hevc)?.takeIf { it.hardware && it.maxBitDepth >= TEN_BITS }
        val transcodeAudio =
            HLS_AUDIO
                .filter { it in direct || it == AudioCodec.Aac }
                .sortedBy {
                    if (surround && it == AudioCodec.Ac3) {
                        0
                    } else if (it == AudioCodec.Aac) {
                        1
                    } else {
                        2
                    }
                }.map { it.codecName }
        return PlaybackProfile(
            name = "Cine Doury - ${device.device}",
            maxBitrate = options.maxBitrate,
            containers = CONTAINERS,
            video = video,
            audioCodecs = AudioCodec.entries.filter { it in direct }.map { it.codecName },
            maxAudioChannels = if (surround) maxOf(channels, SURROUND) else channels,
            transcodeVideoCodecs = listOfNotNull(hevc?.let { "hevc" }, "h264"),
            transcodeAudioCodecs = transcodeAudio,
            transcodeContainer = if (hevc != null) "mp4" else "ts",
            subtitles = subtitleRules(device.subtitleFormats, options.burnInAss),
            transcodeMaxWidth = maxOf(device.display.width, device.display.height),
            transcodeMaxHeight = minOf(device.display.width, device.display.height),
        )
    }

    /** The device as the options let it be: no HDR or Dolby Vision on the screen, two channels. */
    private fun DeviceCapabilities.allowed(options: ProfileOptions): DeviceCapabilities {
        val hdr =
            display.hdr.filterNot {
                (!options.allowHdr && it != DynamicRange.DolbyVision && it != DynamicRange.Sdr) ||
                    (!options.allowDolbyVision && it == DynamicRange.DolbyVision)
            }
        return copy(
            display = display.copy(hdr = hdr.toSet()),
            dolbyVisionProfiles = if (options.allowDolbyVision) dolbyVisionProfiles else emptySet(),
            passthrough = if (options.stereo) emptySet() else passthrough,
            maxPcmChannels = if (options.stereo) minOf(maxPcmChannels, STEREO) else maxPcmChannels,
        )
    }

    private fun videoRule(
        device: DeviceCapabilities,
        decoder: VideoDecoderInfo,
        name: String,
    ): VideoRule {
        val limited = !decoder.hardware && decoder.codec in SOFTWARE_LIMITED
        val tenBit = decoder.maxBitDepth >= TEN_BITS
        return VideoRule(
            codec = name,
            // A decoder of the Baseline profile reads its constrained subset, which the server names apart.
            // The first is the one Jellyfin encodes with: « high » before « baseline » (O, `SPEC.md`
            // § 4.29; the Chromecast had a 4K film encoded again in Baseline).
            profiles =
                (
                    decoder.profiles +
                        if ("baseline" in decoder.profiles) setOf("constrained baseline") else emptySet()
                ).sortedWith(compareBy({ encodeRank(it) }, { it })),
            maxLevel = decoder.maxLevel,
            maxWidth = if (limited) minOf(decoder.maxWidth, SOFTWARE_MAX_WIDTH) else decoder.maxWidth,
            maxHeight = if (limited) minOf(decoder.maxHeight, SOFTWARE_MAX_HEIGHT) else decoder.maxHeight,
            maxBitDepth = decoder.maxBitDepth,
            ranges = ranges(device, decoder.codec, tenBit),
        )
    }

    private fun encodeRank(profile: String): Int =
        ENCODED_FIRST.indexOf(profile).takeIf { it >= 0 } ?: ENCODED_FIRST.size

    private fun ranges(
        device: DeviceCapabilities,
        codec: VideoCodec,
        tenBit: Boolean,
    ): List<RangeType> {
        val screen = device.display.hdr
        val hdr10 = tenBit && DynamicRange.Hdr10 in screen
        val hlg = tenBit && DynamicRange.Hlg in screen
        val dolby = DynamicRange.DolbyVision in screen && codec == VideoCodec.Hevc
        val profiles = if (dolby) device.dolbyVisionProfiles else emptySet()
        return buildList {
            add(RangeType.Sdr)
            if (hdr10) addAll(listOf(RangeType.Hdr10, RangeType.Hdr10Plus))
            if (hlg) add(RangeType.Hlg)
            if (codec != VideoCodec.Hevc) return@buildList
            if (DOLBY_VISION_SINGLE_LAYER in profiles) add(RangeType.DolbyVision)
            if (DOLBY_VISION_DUAL_LAYER in profiles) {
                addAll(listOf(RangeType.DolbyVisionWithEl, RangeType.DolbyVisionWithElHdr10Plus))
            }
            // Profile 8: Dolby Vision itself, else its base layer, read by any HEVC decoder.
            val compatible = DOLBY_VISION_COMPATIBLE in profiles
            if (compatible || hdr10) addAll(listOf(RangeType.DolbyVisionWithHdr10, RangeType.DolbyVisionWithHdr10Plus))
            if (compatible || hlg) add(RangeType.DolbyVisionWithHlg)
            add(RangeType.DolbyVisionWithSdr)
        }
    }

    private fun subtitleRules(
        formats: Set<SubtitleFormat>,
        burnInAss: Boolean,
    ): List<SubtitleRule> {
        val drawn = formats.filterNot { burnInAss && it in setOf(SubtitleFormat.Ass, SubtitleFormat.Ssa) }
        return drawn.map { SubtitleRule(it.codecName, SubtitleMethod.Embed) } +
            drawn.filter { it.text }.map { SubtitleRule(it.codecName, SubtitleMethod.External) }
    }

    private const val SURROUND_71 = 8
    private const val STEREO = 2
}
