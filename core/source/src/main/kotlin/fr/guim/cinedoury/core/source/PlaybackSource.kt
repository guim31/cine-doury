package fr.guim.cinedoury.core.source

import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.MediaSegment
import fr.guim.cinedoury.core.model.PlaybackInfoRequest
import fr.guim.cinedoury.core.model.PlaybackReport
import fr.guim.cinedoury.core.model.PlaybackStream

/**
 * Playback on a source (`SPEC.md` § 4.11): how to play an item, the reports of the player, the
 * segments and preview pictures. Every failure is a [SourceException].
 */
interface PlaybackSource {
    /**
     * Asks the server how to play the item on this device: as it is, remuxed or transcoded
     * (Jellyfin: `POST /Items/{id}/PlaybackInfo`).
     */
    suspend fun playbackInfo(request: PlaybackInfoRequest): PlaybackStream

    /** Playback started (`POST /Sessions/Playing`). */
    suspend fun reportStart(report: PlaybackReport)

    /** Where playback is (`POST /Sessions/Playing/Progress`): the server keeps it as the resume point. */
    suspend fun reportProgress(report: PlaybackReport)

    /** Playback stopped (`POST /Sessions/Playing/Stopped`); the server ends the transcoding. */
    suspend fun reportStopped(report: PlaybackReport)

    /** The intro, credits, recap… of an item (`GET /MediaSegments/{id}`); empty without them. */
    suspend fun segments(item: ItemId): List<MediaSegment>

    /** The address of one tile of preview pictures of a version, at a [width] the server made. */
    fun trickplayTileUrl(
        item: ItemId,
        sourceId: String,
        width: Int,
        tile: Int,
    ): String

    /** The headers to send with every request for a stream or a picture of the player. */
    val streamHeaders: Map<String, String>
}
