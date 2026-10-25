package fr.guim.cinedoury.core.model

/** One playable version of an item: a film can have several (a 4K HDR file and a 1080p file). */
data class MediaSource(
    val id: String,
    val name: String?,
    val container: String?,
    val sizeBytes: Long?,
    val streams: List<MediaStream>,
    /** Where the file is on the server, for « Informations sur le fichier ». */
    val path: String? = null,
    /** Bits per second, all streams together. */
    val bitrate: Int? = null,
    /** The preview pictures of the version, at each width the server made (J4). */
    val trickplay: List<TrickplayInfo> = emptyList(),
) {
    val video: MediaStream? get() = streams.firstOrNull { it.type == StreamType.Video }
    val audio: List<MediaStream> get() = streams.filter { it.type == StreamType.Audio }
    val subtitles: List<MediaStream> get() = streams.filter { it.type == StreamType.Subtitle }
}

enum class StreamType {
    Video,
    Audio,
    Subtitle,
    Other,
}

enum class VideoRange {
    Sdr,
    Hdr10,
    Hdr10Plus,
    Hlg,
    DolbyVision,
    Unknown,
}

/** Object-based audio carried by a track (Jellyfin: `AudioSpatialFormat`). */
enum class SpatialAudio {
    DolbyAtmos,
    DtsX,
}

/**
 * @param profile the codec profile: « Main 10 » for a video, « DTS-HD MA » or « Dolby TrueHD + Dolby
 * Atmos » for an audio track.
 * @param displayTitle the server's own description of the track, for the file information.
 */
data class MediaStream(
    val index: Int,
    val type: StreamType,
    val codec: String?,
    val language: String? = null,
    val title: String? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isExternal: Boolean = false,
    val channels: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val bitDepth: Int? = null,
    val videoRange: VideoRange? = null,
    val profile: String? = null,
    val displayTitle: String? = null,
    val channelLayout: String? = null,
    val spatialAudio: SpatialAudio? = null,
    val isHearingImpaired: Boolean = false,
    val bitrate: Int? = null,
    /** Pictures per second of a video stream. */
    val frameRate: Float? = null,
    /** The Dolby Vision profile of a video stream (5, 7, 8). */
    val dolbyVisionProfile: Int? = null,
)
