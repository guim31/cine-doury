package fr.guim.cinedoury.core.playback

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioCapabilities
import fr.guim.cinedoury.core.model.AudioCodec
import fr.guim.cinedoury.core.model.DeviceCapabilities
import fr.guim.cinedoury.core.model.DisplayCapabilities
import fr.guim.cinedoury.core.model.DynamicRange
import fr.guim.cinedoury.core.model.PassthroughFormat
import fr.guim.cinedoury.core.model.SubtitleFormat
import fr.guim.cinedoury.core.model.VideoCodec
import fr.guim.cinedoury.core.model.VideoDecoderInfo

/**
 * Reads what the device can play (`SPEC.md` § 4.11): the video decoders of `MediaCodecList` with
 * their profiles, levels and sizes, the Dolby Vision decoder, the audio decoders, what the HDMI
 * output passes to an amplifier (`AudioCapabilities` of Media3), the screen and its HDR. Read again
 * at each playback: an amplifier may have been turned on since.
 */
class DeviceCapabilitiesReader(
    private val context: Context,
    /** False on a phone (M1): no amplifier, the profile declares no passthrough. */
    private val allowPassthrough: Boolean = true,
) {
    fun read(): DeviceCapabilities {
        val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot { it.isEncoder }
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        return DeviceCapabilities(
            device = listOf(Build.MANUFACTURER, Build.MODEL).distinct().joinToString(" "),
            system = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            videoDecoders = codecs.flatMap { videoDecoders(it) },
            dolbyVisionProfiles = dolbyVisionProfiles(codecs),
            audioDecoders = audioDecoders(codecs),
            passthrough = if (allowPassthrough) passthrough() else emptySet(),
            maxPcmChannels = pcmChannels(),
            display = display?.let(::displayCapabilities) ?: DisplayCapabilities(FALLBACK_WIDTH, FALLBACK_HEIGHT),
            subtitleFormats = SUBTITLE_FORMATS,
        )
    }

    private fun videoDecoders(info: MediaCodecInfo): List<VideoDecoderInfo> =
        info.supportedTypes.mapNotNull { type ->
            val codec = VIDEO_TYPES[type.lowercase()] ?: return@mapNotNull null
            val capabilities = runCatching { info.getCapabilitiesForType(type) }.getOrNull() ?: return@mapNotNull null
            val video = capabilities.videoCapabilities ?: return@mapNotNull null
            val levels = capabilities.profileLevels.orEmpty()
            VideoDecoderInfo(
                codec = codec,
                name = info.name,
                hardware = info.isHardwareAccelerated,
                profiles = levels.mapNotNull { CodecLevels.profileName(codec, it.profile) }.toSet(),
                maxLevel = levels.mapNotNull { CodecLevels.level(codec, it.level) }.maxOrNull(),
                maxWidth = video.supportedWidths.upper,
                maxHeight = video.supportedHeights.upper,
                maxBitDepth = if (levels.any { CodecLevels.isTenBit(codec, it.profile) }) TEN_BITS else EIGHT_BITS,
            )
        }

    private fun dolbyVisionProfiles(codecs: List<MediaCodecInfo>): Set<Int> =
        codecs
            .filter { info -> info.supportedTypes.any { it.equals(DOLBY_VISION, ignoreCase = true) } }
            .flatMap { info ->
                runCatching {
                    info
                        .getCapabilitiesForType(
                            DOLBY_VISION,
                        ).profileLevels
                        .toList()
                }.getOrDefault(emptyList())
            }.mapNotNull { CodecLevels.dolbyVisionProfile(it.profile) }
            .toSet()

    private fun audioDecoders(codecs: List<MediaCodecInfo>): Set<AudioCodec> =
        codecs
            .flatMap { it.supportedTypes.toList() }
            .mapNotNull { AUDIO_TYPES[it.lowercase()] }
            .toSet() + AudioCodec.Pcm

    @OptIn(UnstableApi::class)
    private fun audioCapabilities(): AudioCapabilities =
        AudioCapabilities.getCapabilities(context, AudioAttributes.DEFAULT, null, emptyList())

    @OptIn(UnstableApi::class)
    private fun passthrough(): Set<PassthroughFormat> {
        val output = audioCapabilities()
        return PASSTHROUGH.filterValues { output.supportsEncoding(it) }.keys
    }

    @OptIn(UnstableApi::class)
    private fun pcmChannels(): Int = audioCapabilities().maxChannelCount

    private fun displayCapabilities(display: Display): DisplayCapabilities {
        val mode = display.mode

        @Suppress("DEPRECATION") // Display.Mode.getSupportedHdrTypes is API 34; the Shield is on API 30.
        val announced = display.hdrCapabilities?.supportedHdrTypes
        val ofMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) mode.supportedHdrTypes else null
        val types = ScreenHdr.types(announced, ofMode)
        return DisplayCapabilities(
            width = mode.physicalWidth,
            height = mode.physicalHeight,
            refreshRates =
                display.supportedModes
                    .filter { it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
                    .map { it.refreshRate }
                    .distinct()
                    .sorted(),
            hdr = types.mapNotNull { HDR_TYPES[it] }.toSet(),
            announcedHdr = (announced ?: IntArray(0)).toList().mapNotNull { HDR_TYPES[it] }.toSet(),
        )
    }

    private companion object {
        const val TEN_BITS = 10
        const val EIGHT_BITS = 8
        const val FALLBACK_WIDTH = 1920
        const val FALLBACK_HEIGHT = 1080
        const val DOLBY_VISION = "video/dolby-vision"

        val VIDEO_TYPES =
            mapOf(
                "video/avc" to VideoCodec.H264,
                "video/hevc" to VideoCodec.Hevc,
                "video/av01" to VideoCodec.Av1,
                "video/x-vnd.on2.vp9" to VideoCodec.Vp9,
                "video/mpeg2" to VideoCodec.Mpeg2,
                "video/wvc1" to VideoCodec.Vc1,
                "video/vc1" to VideoCodec.Vc1,
            )

        val AUDIO_TYPES =
            mapOf(
                "audio/mp4a-latm" to AudioCodec.Aac,
                "audio/mpeg" to AudioCodec.Mp3,
                "audio/opus" to AudioCodec.Opus,
                "audio/flac" to AudioCodec.Flac,
                "audio/vorbis" to AudioCodec.Vorbis,
                "audio/alac" to AudioCodec.Alac,
                "audio/raw" to AudioCodec.Pcm,
                "audio/ac3" to AudioCodec.Ac3,
                "audio/eac3" to AudioCodec.Eac3,
                "audio/vnd.dts" to AudioCodec.Dts,
                "audio/true-hd" to AudioCodec.TrueHd,
            )

        val PASSTHROUGH =
            mapOf(
                PassthroughFormat.Ac3 to C.ENCODING_AC3,
                PassthroughFormat.Eac3 to C.ENCODING_E_AC3,
                PassthroughFormat.Eac3Atmos to C.ENCODING_E_AC3_JOC,
                PassthroughFormat.Dts to C.ENCODING_DTS,
                PassthroughFormat.DtsHd to C.ENCODING_DTS_HD,
                PassthroughFormat.TrueHd to C.ENCODING_DOLBY_TRUEHD,
            )

        val HDR_TYPES =
            mapOf(
                Display.HdrCapabilities.HDR_TYPE_HDR10 to DynamicRange.Hdr10,
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS to DynamicRange.Hdr10Plus,
                Display.HdrCapabilities.HDR_TYPE_HLG to DynamicRange.Hlg,
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION to DynamicRange.DolbyVision,
            )

        /** What Media3 draws itself: SubRip, SSA/ASS, WebVTT, TTML, PGS, VobSub, DVB. */
        val SUBTITLE_FORMATS = SubtitleFormat.entries.toSet()
    }
}

/**
 * The HDR the screen shows (P, `SPEC.md` § 4.29). A Chromecast with Google TV under Android 14, on a
 * 1080p SDR television, had its modes say `supportedHdrTypes=[]` (`dumpsys display`) while the profile
 * it sent let the server copy HDR10, which only `Display.getHdrCapabilities()` can have put there. From
 * API 34, a type counts only if the current mode lists it too ([ofMode], `Display.Mode.getSupportedHdrTypes`,
 * which replaces the deprecated list of the display); before, the list of the display stays (the Shield,
 * API 30, has nothing else).
 */
internal object ScreenHdr {
    fun types(
        announced: IntArray?,
        ofMode: IntArray?,
    ): Set<Int> {
        val display = announced?.toSet().orEmpty()
        return if (ofMode == null) display else display intersect ofMode.toSet()
    }
}

/**
 * The profiles and levels of `MediaCodecInfo.CodecProfileLevel`, named and numbered as the server
 * does (ffprobe): « main 10 », level 153 for HEVC 5.1, 51 for H.264 5.1.
 */
internal object CodecLevels {
    private val AVC_PROFILES =
        mapOf(
            CodecProfileLevel.AVCProfileBaseline to "baseline",
            CodecProfileLevel.AVCProfileConstrainedBaseline to "constrained baseline",
            CodecProfileLevel.AVCProfileMain to "main",
            CodecProfileLevel.AVCProfileHigh to "high",
            CodecProfileLevel.AVCProfileConstrainedHigh to "high",
            CodecProfileLevel.AVCProfileHigh10 to "high 10",
        )
    private val AVC_LEVELS =
        mapOf(
            CodecProfileLevel.AVCLevel1 to 10,
            CodecProfileLevel.AVCLevel1b to 9,
            CodecProfileLevel.AVCLevel11 to 11,
            CodecProfileLevel.AVCLevel12 to 12,
            CodecProfileLevel.AVCLevel13 to 13,
            CodecProfileLevel.AVCLevel2 to 20,
            CodecProfileLevel.AVCLevel21 to 21,
            CodecProfileLevel.AVCLevel22 to 22,
            CodecProfileLevel.AVCLevel3 to 30,
            CodecProfileLevel.AVCLevel31 to 31,
            CodecProfileLevel.AVCLevel32 to 32,
            CodecProfileLevel.AVCLevel4 to 40,
            CodecProfileLevel.AVCLevel41 to 41,
            CodecProfileLevel.AVCLevel42 to 42,
            CodecProfileLevel.AVCLevel5 to 50,
            CodecProfileLevel.AVCLevel51 to 51,
            CodecProfileLevel.AVCLevel52 to 52,
            CodecProfileLevel.AVCLevel6 to 60,
            CodecProfileLevel.AVCLevel61 to 61,
            CodecProfileLevel.AVCLevel62 to 62,
        )
    private val HEVC_PROFILES =
        mapOf(
            CodecProfileLevel.HEVCProfileMain to "main",
            CodecProfileLevel.HEVCProfileMain10 to "main 10",
            CodecProfileLevel.HEVCProfileMain10HDR10 to "main 10",
            CodecProfileLevel.HEVCProfileMain10HDR10Plus to "main 10",
        )

    /** HEVC levels come in pairs (main tier, high tier), from level 1 (bits 0 and 1) up. */
    private val HEVC_LEVELS = listOf(30, 60, 63, 90, 93, 120, 123, 150, 153, 156, 180, 183, 186)
    private val VP9_PROFILES =
        mapOf(
            CodecProfileLevel.VP9Profile0 to "profile 0",
            CodecProfileLevel.VP9Profile1 to "profile 1",
            CodecProfileLevel.VP9Profile2 to "profile 2",
            CodecProfileLevel.VP9Profile3 to "profile 3",
            CodecProfileLevel.VP9Profile2HDR to "profile 2",
            CodecProfileLevel.VP9Profile3HDR to "profile 3",
            CodecProfileLevel.VP9Profile2HDR10Plus to "profile 2",
            CodecProfileLevel.VP9Profile3HDR10Plus to "profile 3",
        )
    private val AV1_PROFILES =
        mapOf(
            CodecProfileLevel.AV1ProfileMain8 to "main",
            CodecProfileLevel.AV1ProfileMain10 to "main",
            CodecProfileLevel.AV1ProfileMain10HDR10 to "main",
            CodecProfileLevel.AV1ProfileMain10HDR10Plus to "main",
        )
    private val TEN_BIT =
        setOf(
            CodecProfileLevel.HEVCProfileMain10,
            CodecProfileLevel.HEVCProfileMain10HDR10,
            CodecProfileLevel.HEVCProfileMain10HDR10Plus,
        )
    private val TEN_BIT_VP9 =
        setOf(
            CodecProfileLevel.VP9Profile2,
            CodecProfileLevel.VP9Profile3,
            CodecProfileLevel.VP9Profile2HDR,
            CodecProfileLevel.VP9Profile3HDR,
            CodecProfileLevel.VP9Profile2HDR10Plus,
            CodecProfileLevel.VP9Profile3HDR10Plus,
        )
    private val TEN_BIT_AV1 =
        setOf(
            CodecProfileLevel.AV1ProfileMain10,
            CodecProfileLevel.AV1ProfileMain10HDR10,
            CodecProfileLevel.AV1ProfileMain10HDR10Plus,
        )
    private val DOLBY_VISION =
        mapOf(
            CodecProfileLevel.DolbyVisionProfileDvheDtr to 4,
            CodecProfileLevel.DolbyVisionProfileDvheStn to 5,
            CodecProfileLevel.DolbyVisionProfileDvheDth to 6,
            CodecProfileLevel.DolbyVisionProfileDvheDtb to 7,
            CodecProfileLevel.DolbyVisionProfileDvheSt to 8,
            CodecProfileLevel.DolbyVisionProfileDvavSe to 9,
        )

    fun profileName(
        codec: VideoCodec,
        profile: Int,
    ): String? =
        when (codec) {
            VideoCodec.H264 -> AVC_PROFILES[profile]
            VideoCodec.Hevc -> HEVC_PROFILES[profile]
            VideoCodec.Vp9 -> VP9_PROFILES[profile]
            VideoCodec.Av1 -> AV1_PROFILES[profile]
            else -> null
        }

    fun level(
        codec: VideoCodec,
        level: Int,
    ): Int? =
        when (codec) {
            VideoCodec.H264 -> AVC_LEVELS[level]
            VideoCodec.Hevc -> hevcLevel(level)
            else -> null
        }

    private fun hevcLevel(level: Int): Int? {
        if (level <= 0) return null
        val bit = Integer.numberOfTrailingZeros(level)
        return HEVC_LEVELS.getOrNull(bit / 2)
    }

    fun isTenBit(
        codec: VideoCodec,
        profile: Int,
    ): Boolean =
        when (codec) {
            VideoCodec.Hevc -> profile in TEN_BIT
            VideoCodec.Vp9 -> profile in TEN_BIT_VP9
            VideoCodec.Av1 -> profile in TEN_BIT_AV1
            else -> false
        }

    fun dolbyVisionProfile(profile: Int): Int? = DOLBY_VISION[profile]
}
