package fr.guim.cinedoury.core.model

import kotlin.time.Duration

/** How a file reaches the player (`FONCTIONNALITES.md` § 5, « Démarrage »). */
enum class PlayMethod {
    /** The file itself, read by the player. */
    DirectPlay,

    /** The streams of the file, copied by the server into another container (remux). */
    DirectStream,

    /** Encoded again by the server, all or part (audio only, for example). */
    Transcode,
}

/** Why the server does not send the file as it is, as the server says it. */
enum class TranscodeReason {
    ContainerNotSupported,
    VideoCodecNotSupported,
    AudioCodecNotSupported,
    SubtitleCodecNotSupported,
    AudioIsExternal,
    SecondaryAudioNotSupported,
    VideoProfileNotSupported,
    VideoLevelNotSupported,
    VideoResolutionNotSupported,
    VideoBitDepthNotSupported,
    VideoFramerateNotSupported,
    RefFramesNotSupported,
    AnamorphicVideoNotSupported,
    InterlacedVideoNotSupported,
    AudioChannelsNotSupported,
    AudioProfileNotSupported,
    AudioSampleRateNotSupported,
    AudioBitDepthNotSupported,
    ContainerBitrateExceedsLimit,
    VideoBitrateNotSupported,
    AudioBitrateNotSupported,
    UnknownVideoStreamInfo,
    UnknownAudioStreamInfo,
    DirectPlayError,
    VideoRangeTypeNotSupported,
    VideoCodecTagNotSupported,
    StreamCountExceedsLimit,
    VideoRotationNotSupported,
    ;

    /** Whether the reason is about the audio: the video may still be copied. */
    val isAudio: Boolean get() = name.startsWith("Audio") || this == SecondaryAudioNotSupported

    companion object {
        fun of(name: String): TranscodeReason? = entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
    }
}

/**
 * What to ask the server: an item, its version and tracks, where to start, and the device.
 *
 * @param subtitleIndex the subtitle stream; null for none.
 * @param maxBitrate a lower bitrate chosen in « Qualité »; null for the profile's.
 * @param allowDirect false after the player failed on the file as it is, or when the settings say
 * never (`DirectPlayMode.Never`): the server transcodes.
 * @param forceDirect the file as it is even when the server would not send it so
 * (`DirectPlayMode.Forced`): the profile of the device may be too cautious.
 */
data class PlaybackInfoRequest(
    val item: ItemId,
    val profile: PlaybackProfile,
    val sourceId: String? = null,
    val audioIndex: Int? = null,
    val subtitleIndex: Int? = null,
    val start: Duration = Duration.ZERO,
    val maxBitrate: Int? = null,
    val allowDirect: Boolean = true,
    val forceDirect: Boolean = false,
)

/**
 * What the server decided (`SPEC.md` § 4.11): the address to read and how it was made.
 *
 * @param url the absolute address of the file or of the HLS playlist.
 * @param reasons why the server transcodes or remuxes; empty for a direct play.
 * @param source the version played, with its streams.
 * @param container what the player receives: the file's container, or the segments of the server.
 * @param subtitles how each subtitle track of [source] reaches the player.
 * @param audioIndex the audio stream the server put in a transcoded stream.
 * @param subtitleIndex the subtitle stream asked for.
 * @param maxBitrate the bitrate asked for.
 */
data class PlaybackStream(
    val url: String,
    val method: PlayMethod,
    val reasons: Set<TranscodeReason>,
    val playSessionId: String?,
    val source: MediaSource,
    val container: String?,
    val isHls: Boolean,
    val subtitles: List<SubtitleDelivery>,
    val audioIndex: Int?,
    val subtitleIndex: Int?,
    val maxBitrate: Int?,
    /** The headers to send with every request for the stream (Jellyfin: `Authorization`). */
    val headers: Map<String, String> = emptyMap(),
) {
    /** The video is copied as it is, in a transcoded stream: only the audio is encoded again. */
    val videoCopied: Boolean get() =
        method == PlayMethod.Transcode && reasons.isNotEmpty() &&
            reasons.all { it.isAudio }

    fun delivery(index: Int?): SubtitleDelivery? = subtitles.firstOrNull { it.index == index }
}

/** How one subtitle track reaches the player, and the address of an external one. */
data class SubtitleDelivery(
    val index: Int,
    val method: SubtitleMethod,
    val url: String? = null,
    val codec: String? = null,
)

/** One report of the player to the server (`/Sessions/Playing`…). */
data class PlaybackReport(
    val item: ItemId,
    val sourceId: String,
    val playSessionId: String?,
    val position: Duration,
    val paused: Boolean,
    val method: PlayMethod,
    val audioIndex: Int?,
    val subtitleIndex: Int?,
    val failed: Boolean = false,
)

/** What made the player stop, as far as the next step goes. */
enum class PlaybackFailure {
    /** The device has no decoder for a stream, or the decoder failed. */
    Decoder,

    /** The container or a stream cannot be read. */
    Format,

    /** The server cannot be reached, or the connection broke. */
    Network,

    /** The server refused or failed (4xx, 5xx). */
    Server,

    Unknown,
}

/** What to do after a failure. */
enum class FallbackAction {
    /** Ask the server again, without direct play: it transcodes, at the same position. */
    Transcode,

    /** Show the error, with « Réessayer ». */
    ShowError,
}

/**
 * The rule of fallback (`SPEC.md` § 4.11): a file played as it is (direct play or remux) that the
 * device cannot decode or read is asked again of the server, transcoded, at the same position and
 * without an error screen, once. Any other failure, and a transcoded stream that fails, show the
 * error.
 */
object PlaybackFallback {
    fun next(
        method: PlayMethod,
        failure: PlaybackFailure,
    ): FallbackAction =
        if (method != PlayMethod.Transcode && failure in setOf(PlaybackFailure.Decoder, PlaybackFailure.Format)) {
            FallbackAction.Transcode
        } else {
            FallbackAction.ShowError
        }
}
