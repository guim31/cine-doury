package fr.guim.cinedoury.core.source.fake

import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.MediaSegment
import fr.guim.cinedoury.core.model.MediaSource
import fr.guim.cinedoury.core.model.PlayMethod
import fr.guim.cinedoury.core.model.PlaybackInfoRequest
import fr.guim.cinedoury.core.model.PlaybackReport
import fr.guim.cinedoury.core.model.PlaybackStream
import fr.guim.cinedoury.core.model.SubtitleDelivery
import fr.guim.cinedoury.core.model.SubtitleMethod
import fr.guim.cinedoury.core.model.TranscodeReason
import fr.guim.cinedoury.core.source.PlaybackSource
import fr.guim.cinedoury.core.source.SourceException

/**
 * Playback held in memory: it plays every source directly unless [transcode] says otherwise (or the
 * request forbids direct play), and records every report, in order.
 *
 * @param sources the versions of each item.
 * @param segments the segments of each item.
 * @param transcode why an item is transcoded; absent: played directly.
 */
class InMemoryPlayback(
    var sources: Map<ItemId, List<MediaSource>> = emptyMap(),
    var segments: Map<ItemId, List<MediaSegment>> = emptyMap(),
    var transcode: Map<ItemId, Set<TranscodeReason>> = emptyMap(),
) : PlaybackSource {
    /** Every report, as (kind, report): « start », « progress », « stopped ». */
    val reports: MutableList<Pair<String, PlaybackReport>> = mutableListOf()

    /** Every request for a stream. */
    val requests: MutableList<PlaybackInfoRequest> = mutableListOf()

    /** When set, every report fails with it (the server went away). */
    var failure: SourceException? = null

    override val streamHeaders: Map<String, String> = mapOf("Authorization" to "MediaBrowser Token=\"test\"")

    override suspend fun playbackInfo(request: PlaybackInfoRequest): PlaybackStream {
        requests += request
        failure?.let { throw it }
        val versions = sources[request.item] ?: throw SourceException.NotFound(request.item.value)
        val source = versions.firstOrNull { it.id == request.sourceId } ?: versions.first()
        val reasons =
            transcode[request.item] ?: if (request.allowDirect) emptySet() else setOf(TranscodeReason.DirectPlayError)
        val method = if (reasons.isEmpty()) PlayMethod.DirectPlay else PlayMethod.Transcode
        return PlaybackStream(
            url = if (reasons.isEmpty()) "memory://${source.id}/stream" else "memory://${source.id}/master.m3u8",
            method = method,
            reasons = reasons,
            playSessionId = "session-${requests.size}",
            source = source,
            container = if (reasons.isEmpty()) source.container else "ts",
            isHls = reasons.isNotEmpty(),
            subtitles =
                source.subtitles.map {
                    SubtitleDelivery(
                        it.index,
                        if (reasons.isEmpty()) SubtitleMethod.Embed else SubtitleMethod.External,
                        codec = it.codec,
                    )
                },
            audioIndex = request.audioIndex,
            subtitleIndex = request.subtitleIndex,
            maxBitrate = request.maxBitrate,
            headers = streamHeaders,
        )
    }

    override suspend fun reportStart(report: PlaybackReport) = record("start", report)

    override suspend fun reportProgress(report: PlaybackReport) = record("progress", report)

    override suspend fun reportStopped(report: PlaybackReport) = record("stopped", report)

    private fun record(
        kind: String,
        report: PlaybackReport,
    ) {
        failure?.let { throw it }
        reports += kind to report
    }

    override suspend fun segments(item: ItemId): List<MediaSegment> = segments[item].orEmpty()

    override fun trickplayTileUrl(
        item: ItemId,
        sourceId: String,
        width: Int,
        tile: Int,
    ): String = "memory://${item.value}/trickplay/$width/$tile.jpg"
}
