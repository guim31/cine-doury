package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.model.PlaybackProfile
import fr.guim.cinedoury.core.model.SubtitleMethod
import fr.guim.cinedoury.core.model.VideoRule
import org.jellyfin.sdk.model.api.CodecProfile
import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DirectPlayProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.EncodingContext
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.ProfileCondition
import org.jellyfin.sdk.model.api.ProfileConditionType
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import org.jellyfin.sdk.model.api.TranscodingProfile

/**
 * The Jellyfin form of a [PlaybackProfile] (`SPEC.md` § 4.11): one direct play profile (containers,
 * video and audio codecs), one HLS transcoding profile, one codec profile per video codec (profiles,
 * level, size, bit depth, dynamic ranges) and one for the audio channels, the subtitle methods.
 */
internal fun PlaybackProfile.toDeviceProfile(): DeviceProfile =
    DeviceProfile(
        name = name,
        maxStreamingBitrate = maxBitrate,
        maxStaticBitrate = maxBitrate,
        directPlayProfiles =
            listOf(
                DirectPlayProfile(
                    container = containers.joinToString(","),
                    videoCodec = video.joinToString(",") { it.codec },
                    audioCodec = audioCodecs.joinToString(","),
                    type = DlnaProfileType.VIDEO,
                ),
            ),
        transcodingProfiles =
            listOf(
                TranscodingProfile(
                    container = transcodeContainer,
                    type = DlnaProfileType.VIDEO,
                    videoCodec = transcodeVideoCodecs.joinToString(","),
                    audioCodec = transcodeAudioCodecs.joinToString(","),
                    protocol = MediaStreamProtocol.HLS,
                    context = EncodingContext.STREAMING,
                    maxAudioChannels = maxAudioChannels.toString(),
                    minSegments = 1,
                    conditions = emptyList(),
                ),
            ),
        containerProfiles = emptyList(),
        codecProfiles =
            video.map { it.toCodecProfile() } +
                CodecProfile(
                    type = CodecType.VIDEO_AUDIO,
                    conditions =
                        listOf(
                            condition(ProfileConditionValue.AUDIO_CHANNELS, LESS, maxAudioChannels.toString()),
                        ),
                    applyConditions = emptyList(),
                ),
        subtitleProfiles = subtitles.map { SubtitleProfile(format = it.format, method = it.method.toJellyfin()) },
    )

private fun VideoRule.toCodecProfile(): CodecProfile =
    CodecProfile(
        type = CodecType.VIDEO,
        codec = codec,
        conditions =
            listOfNotNull(
                profiles.takeIf { it.isNotEmpty() }?.let {
                    condition(
                        ProfileConditionValue.VIDEO_PROFILE,
                        ProfileConditionType.EQUALS_ANY,
                        it.joinToString("|"),
                    )
                },
                maxLevel?.let { condition(ProfileConditionValue.VIDEO_LEVEL, LESS, it.toString()) },
                condition(ProfileConditionValue.WIDTH, LESS, maxWidth.toString()),
                condition(ProfileConditionValue.HEIGHT, LESS, maxHeight.toString()),
                condition(ProfileConditionValue.VIDEO_BIT_DEPTH, LESS, maxBitDepth.toString()),
                condition(
                    ProfileConditionValue.VIDEO_RANGE_TYPE,
                    ProfileConditionType.EQUALS_ANY,
                    ranges.joinToString("|") { it.serverName },
                ),
            ),
        applyConditions = emptyList(),
    )

private val LESS = ProfileConditionType.LESS_THAN_EQUAL

// Not required: a stream whose value the server does not know (an old probe) still plays directly.
private fun condition(
    property: ProfileConditionValue,
    type: ProfileConditionType,
    value: String,
) = ProfileCondition(condition = type, property = property, value = value, isRequired = false)

internal fun SubtitleMethod.toJellyfin(): SubtitleDeliveryMethod =
    when (this) {
        SubtitleMethod.Embed -> SubtitleDeliveryMethod.EMBED
        SubtitleMethod.External -> SubtitleDeliveryMethod.EXTERNAL
        SubtitleMethod.Encode -> SubtitleDeliveryMethod.ENCODE
        SubtitleMethod.Hls -> SubtitleDeliveryMethod.HLS
        SubtitleMethod.Drop -> SubtitleDeliveryMethod.DROP
    }

internal fun SubtitleDeliveryMethod.toModel(): SubtitleMethod =
    when (this) {
        SubtitleDeliveryMethod.EMBED -> SubtitleMethod.Embed
        SubtitleDeliveryMethod.EXTERNAL -> SubtitleMethod.External
        SubtitleDeliveryMethod.ENCODE -> SubtitleMethod.Encode
        SubtitleDeliveryMethod.HLS -> SubtitleMethod.Hls
        SubtitleDeliveryMethod.DROP -> SubtitleMethod.Drop
    }
