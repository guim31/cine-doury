package fr.guim.cinedoury.core.model

/** One playable version of an item: a film can have several (a 4K HDR file and a 1080p file). */
data class MediaSource(
    val id: String,
    val name: String?,
    val container: String?,
    val sizeBytes: Long?,
    val streams: List<MediaStream>,
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
)
