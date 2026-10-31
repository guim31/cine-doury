package fr.guim.cinedoury.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi

/**
 * Whether the device has no decoder at all for the video, or for every audio track, of a stream: the
 * server must then transcode (`SPEC.md` § 4.11).
 *
 * Only a missing decoder counts. A format that merely exceeds what the decoder advertises is not a
 * failure: Media3 plays it anyway (`exceedRendererCapabilitiesIfNecessary`), and if the decoder really
 * fails, `onPlayerError` says so. This matters most for HLS, whose tracks come from the CODECS of the
 * playlist before any segment: Jellyfin announces an MP3 copied into fMP4 as `mp4a.40.34`, which
 * Media3 reads as an AAC of unknown profile (exceeds capabilities), though the segments carry MP3.
 * A track whose format says no MIME type (only declared, not known) is not judged either.
 */
@OptIn(UnstableApi::class)
internal object DecoderCheck {
    /** What has no decoder (« video avc1… », « audio audio/… »), or null when the stream can be played. */
    fun missing(tracks: Tracks): String? {
        val video = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO && it.isKnown() }
        val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isKnown() }
        return when {
            video.isNotEmpty() && video.none { it.hasDecoder() } -> "video ${video.first().format.codecs}"
            audio.isNotEmpty() && audio.none { it.hasDecoder() } -> "audio ${audio.first().format.sampleMimeType}"
            else -> null
        }
    }

    /** A track that merely exceeds what its decoder advertises still has one. */
    private const val ALLOW_EXCEEDS_CAPABILITIES = true

    private val Tracks.Group.format: Format get() = getTrackFormat(0)

    private fun Tracks.Group.hasDecoder(): Boolean = isSupported(ALLOW_EXCEEDS_CAPABILITIES)

    private fun Tracks.Group.isKnown(): Boolean = (0 until length).any { getTrackFormat(it).sampleMimeType != null }
}
