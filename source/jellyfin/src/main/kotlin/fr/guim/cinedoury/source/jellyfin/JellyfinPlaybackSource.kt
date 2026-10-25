package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.MediaSegment
import fr.guim.cinedoury.core.model.PlayMethod
import fr.guim.cinedoury.core.model.PlaybackInfoRequest
import fr.guim.cinedoury.core.model.PlaybackReport
import fr.guim.cinedoury.core.model.PlaybackStream
import fr.guim.cinedoury.core.model.SegmentType
import fr.guim.cinedoury.core.model.SubtitleDelivery
import fr.guim.cinedoury.core.model.SubtitleMethod
import fr.guim.cinedoury.core.model.TranscodeReason
import fr.guim.cinedoury.core.source.PlaybackSource
import fr.guim.cinedoury.core.source.SourceException
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.mediaSegmentApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.trickPlayApi
import org.jellyfin.sdk.api.client.extensions.videoApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import java.net.URLDecoder
import kotlin.time.Duration
import org.jellyfin.sdk.model.api.PlayMethod as JellyfinPlayMethod

/**
 * [PlaybackSource] over the Jellyfin API (`SPEC.md` § 4.11). The server decides from the profile
 * of the device: direct play (the file, `/Videos/{id}/stream?static=true`), remux or transcoding
 * (its HLS playlist, `TranscodingUrl`, whose `TranscodeReasons` say why).
 */
internal class JellyfinPlaybackSource(
    private val api: ApiClient,
    private val userId: UUID,
) : PlaybackSource {
    private val base = api.baseUrl.orEmpty().trimEnd('/')

    // Jellyfin 12 only takes the token in this header (CLAUDE.md, « Pièges »): ExoPlayer sends it
    // with every request for the stream, the subtitles and the preview pictures.
    override val streamHeaders: Map<String, String> =
        mapOf(
            "Authorization" to
                AuthorizationHeaderBuilder.buildHeader(
                    clientName = api.clientInfo.name,
                    clientVersion = api.clientInfo.version,
                    deviceId = api.deviceInfo.id,
                    deviceName = api.deviceInfo.name,
                    accessToken = api.accessToken,
                ),
        )

    override suspend fun playbackInfo(request: PlaybackInfoRequest): PlaybackStream =
        jellyfinCall(what = "playback of ${request.item.value}") {
            val itemId = request.item.value.toJellyfinUuid()
            val response =
                api.mediaInfoApi
                    .getPostedPlaybackInfo(
                        itemId,
                        PlaybackInfoDto(
                            userId = userId,
                            maxStreamingBitrate = request.maxBitrate ?: request.profile.maxBitrate,
                            startTimeTicks = request.start.toTicks(),
                            audioStreamIndex = request.audioIndex,
                            // -1: no subtitles, rather than the server's own choice.
                            subtitleStreamIndex = request.subtitleIndex ?: NO_SUBTITLES,
                            maxAudioChannels = request.profile.maxAudioChannels,
                            mediaSourceId = request.sourceId,
                            deviceProfile = request.profile.toDeviceProfile(),
                            enableDirectPlay = request.allowDirect,
                            enableDirectStream = request.allowDirect,
                            enableTranscoding = true,
                            allowVideoStreamCopy = true,
                            allowAudioStreamCopy = true,
                            autoOpenLiveStream = true,
                        ),
                    ).content
            response.errorCode?.let { throw SourceException.Unplayable("Server refused: $it") }
            val info =
                response.mediaSources.firstOrNull { it.id == request.sourceId }
                    ?: response.mediaSources.firstOrNull()
                    ?: throw SourceException.Unplayable("No media source")
            info.toStream(itemId, response.playSessionId, request)
        }

    private fun MediaSourceInfo.toStream(
        itemId: UUID,
        playSessionId: String?,
        request: PlaybackInfoRequest,
    ): PlaybackStream {
        val transcoding = transcodingUrl
        val method =
            when {
                supportsDirectPlay -> PlayMethod.DirectPlay
                transcoding == null -> throw SourceException.Unplayable("Neither direct play nor transcoding")
                supportsDirectStream -> PlayMethod.DirectStream
                else -> PlayMethod.Transcode
            }
        val url =
            if (method == PlayMethod.DirectPlay) {
                api.videoApi.getVideoStreamUrl(
                    itemId = itemId,
                    container = container,
                    static = true,
                    tag = eTag,
                    playSessionId = playSessionId,
                    mediaSourceId = id,
                    deviceId = api.deviceInfo.id,
                )
            } else {
                absolute(checkNotNull(transcoding))
            }
        return PlaybackStream(
            url = url,
            method = method,
            reasons = transcoding?.let(::reasonsOf).orEmpty(),
            playSessionId = playSessionId,
            source = toModel(),
            container = if (method == PlayMethod.DirectPlay) container else transcodingContainer,
            isHls = method != PlayMethod.DirectPlay && transcodingSubProtocol == MediaStreamProtocol.HLS,
            subtitles =
                mediaStreams.orEmpty().filter { it.type == MediaStreamType.SUBTITLE }.map { stream ->
                    SubtitleDelivery(
                        index = stream.index,
                        method = stream.deliveryMethod?.toModel() ?: SubtitleMethod.Embed,
                        url = stream.deliveryUrl?.let { if (stream.isExternalUrl == true) it else absolute(it) },
                        codec = stream.codec,
                    )
                },
            audioIndex = request.audioIndex ?: defaultAudioStreamIndex,
            subtitleIndex = request.subtitleIndex,
            maxBitrate = request.maxBitrate,
            headers = streamHeaders,
        )
    }

    private fun absolute(path: String): String = if (path.startsWith("http")) path else base + path

    override suspend fun reportStart(report: PlaybackReport) {
        jellyfinCall(what = "playback report") {
            api.sessionApi.reportPlaybackStart(
                PlaybackStartInfo(
                    canSeek = true,
                    itemId = report.item.value.toJellyfinUuid(),
                    mediaSourceId = report.sourceId,
                    audioStreamIndex = report.audioIndex,
                    subtitleStreamIndex = report.subtitleIndex ?: NO_SUBTITLES,
                    isPaused = report.paused,
                    isMuted = false,
                    positionTicks = report.position.toTicks(),
                    playMethod = report.method.toJellyfin(),
                    playSessionId = report.playSessionId,
                    repeatMode = RepeatMode.REPEAT_NONE,
                    playbackOrder = PlaybackOrder.DEFAULT,
                ),
            )
        }
    }

    override suspend fun reportProgress(report: PlaybackReport) {
        jellyfinCall(what = "playback report") {
            api.sessionApi.reportPlaybackProgress(
                PlaybackProgressInfo(
                    canSeek = true,
                    itemId = report.item.value.toJellyfinUuid(),
                    mediaSourceId = report.sourceId,
                    audioStreamIndex = report.audioIndex,
                    subtitleStreamIndex = report.subtitleIndex ?: NO_SUBTITLES,
                    isPaused = report.paused,
                    isMuted = false,
                    positionTicks = report.position.toTicks(),
                    playMethod = report.method.toJellyfin(),
                    playSessionId = report.playSessionId,
                    repeatMode = RepeatMode.REPEAT_NONE,
                    playbackOrder = PlaybackOrder.DEFAULT,
                ),
            )
        }
    }

    override suspend fun reportStopped(report: PlaybackReport) {
        jellyfinCall(what = "playback report") {
            api.sessionApi.reportPlaybackStopped(
                PlaybackStopInfo(
                    itemId = report.item.value.toJellyfinUuid(),
                    mediaSourceId = report.sourceId,
                    positionTicks = report.position.toTicks(),
                    playSessionId = report.playSessionId,
                    failed = report.failed,
                ),
            )
        }
    }

    override suspend fun segments(item: ItemId): List<MediaSegment> =
        jellyfinCall(what = "segments of ${item.value}") {
            api.mediaSegmentApi
                .getItemSegments(item.value.toJellyfinUuid())
                .content.items
                .map { MediaSegment(it.type.toModel(), it.startTicks.ticksToDuration(), it.endTicks.ticksToDuration()) }
                .filter { it.end > it.start }
                .sortedBy { it.start }
        }

    override fun trickplayTileUrl(
        item: ItemId,
        sourceId: String,
        width: Int,
        tile: Int,
    ): String =
        api.trickPlayApi.getTrickplayTileImageUrl(
            itemId = item.value.toJellyfinUuid(),
            width = width,
            index = tile,
            mediaSourceId =
                sourceId
                    .takeIf {
                        it != item.value
                    }?.let { runCatching { it.toJellyfinUuid() }.getOrNull() },
        )

    private companion object {
        const val NO_SUBTITLES = -1
        const val REASONS = "TranscodeReasons="

        /** `TranscodeReasons=VideoCodecNotSupported,AudioCodecNotSupported` in the address of the stream. */
        fun reasonsOf(url: String): Set<TranscodeReason> {
            val value =
                url
                    .substringAfter('?', "")
                    .split('&')
                    .firstOrNull { it.startsWith(REASONS, ignoreCase = true) }
                    ?.substring(REASONS.length) ?: return emptySet()
            return URLDecoder
                .decode(value, Charsets.UTF_8)
                .split(',')
                .mapNotNull { TranscodeReason.of(it) }
                .toSet()
        }
    }
}

private const val TICKS_PER_MICROSECOND = 10L

internal fun Duration.toTicks(): Long = inWholeMicroseconds * TICKS_PER_MICROSECOND

private fun PlayMethod.toJellyfin(): JellyfinPlayMethod =
    when (this) {
        PlayMethod.DirectPlay -> JellyfinPlayMethod.DIRECT_PLAY
        PlayMethod.DirectStream -> JellyfinPlayMethod.DIRECT_STREAM
        PlayMethod.Transcode -> JellyfinPlayMethod.TRANSCODE
    }

private fun MediaSegmentType.toModel(): SegmentType =
    when (this) {
        MediaSegmentType.INTRO -> SegmentType.Intro
        MediaSegmentType.OUTRO -> SegmentType.Outro
        MediaSegmentType.RECAP -> SegmentType.Recap
        MediaSegmentType.PREVIEW -> SegmentType.Preview
        MediaSegmentType.COMMERCIAL -> SegmentType.Commercial
        MediaSegmentType.UNKNOWN -> SegmentType.Unknown
    }
