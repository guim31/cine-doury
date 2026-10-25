package fr.guim.cinedoury.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.guim.cinedoury.core.model.AspectMode
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PlayMethod
import fr.guim.cinedoury.core.model.PlaybackFailure
import fr.guim.cinedoury.core.model.SegmentAction
import fr.guim.cinedoury.core.model.SegmentType
import fr.guim.cinedoury.core.model.SubtitleBackground
import fr.guim.cinedoury.core.model.SubtitleColor
import fr.guim.cinedoury.core.model.SubtitleEdge
import fr.guim.cinedoury.core.model.SubtitlePosition
import fr.guim.cinedoury.core.model.SubtitleScale
import fr.guim.cinedoury.core.model.SubtitleStyle
import fr.guim.cinedoury.core.model.TranscodeReason
import fr.guim.cinedoury.core.playback.PlayerSnapshot
import fr.guim.cinedoury.core.ui.cardTitle
import fr.guim.cinedoury.core.ui.describe
import fr.guim.cinedoury.core.ui.episodeCode
import fr.guim.cinedoury.core.ui.screenLocale
import java.text.NumberFormat
import java.util.Locale
import kotlin.time.Duration

/** « 1:02:03 », « 42:10 »: a position in the film, in tabular figures. */
fun Duration.toClockText(): String {
    val total = inWholeSeconds.coerceAtLeast(0)
    val hours = total / SECONDS_PER_HOUR
    val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = total % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

private const val SECONDS_PER_HOUR = 3_600
private const val SECONDS_PER_MINUTE = 60

/** The title at the top left (`UI.md` § 8): a film, or for an episode its series. */
val MediaItem.playerTitle: String get() = if (kind == ItemKind.Episode) cardTitle else title

/** Under it, for an episode: « S02 · E05 — titre ». */
@Composable
fun MediaItem.playerSubtitle(): String? {
    if (kind != ItemKind.Episode) return null
    val code = episodeCode() ?: return title
    return "$code — $title"
}

@Composable
fun SegmentType.skipLabel(): String =
    stringResource(
        when (this) {
            SegmentType.Recap -> R.string.player_skip_recap
            SegmentType.Preview -> R.string.player_skip_preview
            SegmentType.Commercial -> R.string.player_skip_commercial
            else -> R.string.player_skip_intro
        },
    )

@Composable
fun PlayMethod.label(audioOnly: Boolean): String =
    stringResource(
        when (this) {
            PlayMethod.DirectPlay -> R.string.mode_direct
            PlayMethod.DirectStream -> R.string.mode_remux
            PlayMethod.Transcode -> if (audioOnly) R.string.mode_transcode_audio else R.string.mode_transcode
        },
    )

@Composable
fun TranscodeReason.label(): String = stringResource(REASONS.getValue(this))

private val REASONS =
    mapOf(
        TranscodeReason.ContainerNotSupported to R.string.reason_ContainerNotSupported,
        TranscodeReason.VideoCodecNotSupported to R.string.reason_VideoCodecNotSupported,
        TranscodeReason.AudioCodecNotSupported to R.string.reason_AudioCodecNotSupported,
        TranscodeReason.SubtitleCodecNotSupported to R.string.reason_SubtitleCodecNotSupported,
        TranscodeReason.AudioIsExternal to R.string.reason_AudioIsExternal,
        TranscodeReason.SecondaryAudioNotSupported to R.string.reason_SecondaryAudioNotSupported,
        TranscodeReason.VideoProfileNotSupported to R.string.reason_VideoProfileNotSupported,
        TranscodeReason.VideoLevelNotSupported to R.string.reason_VideoLevelNotSupported,
        TranscodeReason.VideoResolutionNotSupported to R.string.reason_VideoResolutionNotSupported,
        TranscodeReason.VideoBitDepthNotSupported to R.string.reason_VideoBitDepthNotSupported,
        TranscodeReason.VideoFramerateNotSupported to R.string.reason_VideoFramerateNotSupported,
        TranscodeReason.RefFramesNotSupported to R.string.reason_RefFramesNotSupported,
        TranscodeReason.AnamorphicVideoNotSupported to R.string.reason_AnamorphicVideoNotSupported,
        TranscodeReason.InterlacedVideoNotSupported to R.string.reason_InterlacedVideoNotSupported,
        TranscodeReason.AudioChannelsNotSupported to R.string.reason_AudioChannelsNotSupported,
        TranscodeReason.AudioProfileNotSupported to R.string.reason_AudioProfileNotSupported,
        TranscodeReason.AudioSampleRateNotSupported to R.string.reason_AudioSampleRateNotSupported,
        TranscodeReason.AudioBitDepthNotSupported to R.string.reason_AudioBitDepthNotSupported,
        TranscodeReason.ContainerBitrateExceedsLimit to R.string.reason_ContainerBitrateExceedsLimit,
        TranscodeReason.VideoBitrateNotSupported to R.string.reason_VideoBitrateNotSupported,
        TranscodeReason.AudioBitrateNotSupported to R.string.reason_AudioBitrateNotSupported,
        TranscodeReason.UnknownVideoStreamInfo to R.string.reason_UnknownVideoStreamInfo,
        TranscodeReason.UnknownAudioStreamInfo to R.string.reason_UnknownAudioStreamInfo,
        TranscodeReason.DirectPlayError to R.string.reason_DirectPlayError,
        TranscodeReason.VideoRangeTypeNotSupported to R.string.reason_VideoRangeTypeNotSupported,
        TranscodeReason.VideoCodecTagNotSupported to R.string.reason_VideoCodecTagNotSupported,
        TranscodeReason.StreamCountExceedsLimit to R.string.reason_StreamCountExceedsLimit,
        TranscodeReason.VideoRotationNotSupported to R.string.reason_VideoRotationNotSupported,
    )

@Composable
fun PlaybackFailure.message(): String =
    stringResource(
        when (this) {
            PlaybackFailure.Network -> R.string.player_error_network
            PlaybackFailure.Server -> R.string.player_error_server
            else -> R.string.player_error_decoder
        },
    )

@Composable
fun AspectMode.label(): String =
    stringResource(
        when (this) {
            AspectMode.Fit -> R.string.player_aspect_fit
            AspectMode.Fill -> R.string.player_aspect_fill
            AspectMode.Zoom -> R.string.player_aspect_zoom
        },
    )

@Composable
fun SegmentAction.label(): String =
    stringResource(
        when (this) {
            SegmentAction.Button -> R.string.player_segments_button
            SegmentAction.Skip -> R.string.player_segments_skip
            SegmentAction.Nothing -> R.string.player_segments_nothing
        },
    )

@Composable
fun SubtitleColor.label(): String =
    stringResource(
        when (this) {
            SubtitleColor.White -> R.string.color_white
            SubtitleColor.Yellow -> R.string.color_yellow
            SubtitleColor.Cyan -> R.string.color_cyan
            SubtitleColor.Green -> R.string.color_green
        },
    )

@Composable
fun SubtitleEdge.label(): String =
    stringResource(
        when (this) {
            SubtitleEdge.None -> R.string.edge_none
            SubtitleEdge.Outline -> R.string.edge_outline
            SubtitleEdge.Shadow -> R.string.edge_shadow
        },
    )

@Composable
fun SubtitleBackground.label(): String =
    stringResource(
        when (this) {
            SubtitleBackground.None -> R.string.background_none
            SubtitleBackground.Shaded -> R.string.background_shaded
            SubtitleBackground.Band -> R.string.background_band
        },
    )

@Composable
fun SubtitlePosition.label(): String =
    stringResource(
        when (this) {
            SubtitlePosition.Bottom -> R.string.position_bottom
            SubtitlePosition.Raised -> R.string.position_raised
            SubtitlePosition.High -> R.string.position_high
        },
    )

/** « 20 Mb/s », « 8,5 Mb/s ». */
@Composable
fun Int.megabits(): String {
    val format = NumberFormat.getNumberInstance(screenLocale()).apply { maximumFractionDigits = 1 }
    return format.format(this / MEGA)
}

private const val MEGA = 1_000_000.0

/**
 * The lines of the information screen (`FONCTIONNALITES.md` § 5): enough to understand a stutter
 * from a photo of the screen — engine, mode and its reason, the fallback, the container, what is
 * decoded and how, the bitrate, the buffer, the dropped frames, the device and its screen.
 */
@Composable
fun PlayerState.infoLines(snapshot: PlayerSnapshot): List<Pair<String, String>> {
    val none = stringResource(R.string.info_none)
    val stream = stream
    val lines = mutableListOf<Pair<String, String>>()
    lines += stringResource(R.string.info_engine) to stringResource(R.string.info_engine_value)
    lines += stringResource(R.string.info_mode) to (stream?.method?.label(stream.videoCopied) ?: none)
    if (stream != null && stream.reasons.isNotEmpty()) {
        lines += stringResource(R.string.info_reason) to stream.reasons.map { it.label() }.joinToString(", ")
    }
    fallback?.let {
        lines += stringResource(
            R.string.info_fallback,
        ) to stringResource(R.string.info_fallback_value, it)
    }
    lines += stringResource(R.string.info_container) to (stream?.container?.uppercase() ?: none)
    val source = stream?.source
    val video = snapshot.video
    lines += stringResource(R.string.info_video) to (video?.codec ?: source?.video?.describe() ?: none)
    if (video != null) {
        val rate =
            video.frameRate?.let {
                NumberFormat
                    .getNumberInstance(
                        screenLocale(),
                    ).apply { maximumFractionDigits = 3 }
                    .format(it)
            }
                ?: none
        lines +=
            stringResource(R.string.info_resolution) to
            stringResource(R.string.info_resolution_value, video.width, video.height, rate)
        lines +=
            stringResource(R.string.info_range) to
            listOfNotNull(video.range, source?.video?.dolbyVisionProfile?.let { "DV $it" }).joinToString(" · ")
        lines += stringResource(R.string.info_decoder) to (video.decoder ?: none)
    }
    val audio = snapshot.audio
    lines += stringResource(R.string.info_audio) to
        (audio?.let { stringResource(R.string.info_audio_value, it.codec, it.channels, it.sampleRate) } ?: none)
    lines += stringResource(R.string.info_passthrough) to
        stringResource(if (audio?.passthrough == true) R.string.info_passthrough_on else R.string.info_passthrough_off)
    if (audio?.decoder != null) lines += stringResource(R.string.info_decoder) to audio.decoder.orEmpty()
    val bitrate = source?.bitrate ?: maxBitrate
    lines +=
        stringResource(R.string.info_bitrate) to
        (bitrate?.let { stringResource(R.string.info_mbps, it.megabits()) } ?: none)
    snapshot.bandwidth?.let {
        lines +=
            stringResource(R.string.info_bandwidth) to stringResource(R.string.info_mbps, it.toInt().megabits())
    }
    val buffer =
        NumberFormat
            .getNumberInstance(screenLocale())
            .apply {
                maximumFractionDigits = 1
            }.format(snapshot.buffered.inWholeMilliseconds / MILLIS)
    lines += stringResource(R.string.info_buffer) to stringResource(R.string.info_seconds, buffer)
    lines += stringResource(R.string.info_dropped) to snapshot.droppedFrames.toString()
    capabilities?.let { device ->
        lines += stringResource(R.string.info_device) to "${device.device} · Android ${device.system}"
        val hdr =
            device.display.hdr
                .joinToString(", ") { it.name }
                .ifEmpty { "SDR" }
        lines +=
            stringResource(R.string.info_screen) to
            stringResource(R.string.info_screen_value, device.display.width, device.display.height, hdr)
    }
    return lines
}

private const val MILLIS = 1_000.0

/** The size of the subtitles, « 140 % ». */
@Composable
fun SubtitleStyle.scaleLabel(): String = stringResource(R.string.setting_percent, SubtitleScale.percent(scale))
