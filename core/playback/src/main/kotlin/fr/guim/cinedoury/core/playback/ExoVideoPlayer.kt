package fr.guim.cinedoury.core.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioOutputProvider
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import fr.guim.cinedoury.core.model.DeviceCapabilities
import fr.guim.cinedoury.core.model.DynamicRange
import fr.guim.cinedoury.core.model.MarginInputs
import fr.guim.cinedoury.core.model.PassthroughFormat
import fr.guim.cinedoury.core.model.PlayMethod
import fr.guim.cinedoury.core.model.PlaybackFailure
import fr.guim.cinedoury.core.model.PlaybackStream
import fr.guim.cinedoury.core.model.StreamType
import fr.guim.cinedoury.core.model.SubtitleMethod
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * [VideoPlayer] on Media3 ExoPlayer (`SPEC.md` § 4.11):
 * - every request carries the headers of the source (Jellyfin: `Authorization`);
 * - a lost connection is retried, for about 90 s, while the buffer (up to 90 s) keeps playing;
 * - passthrough to the amplifier only for the formats the user allows ([allowedPassthrough]);
 * - without a Dolby Vision screen, a Dolby Vision decoder is never used: profile 8 plays its HDR10
 *   (or HLG, SDR) base layer on the HEVC decoder;
 * - the screen follows the frame rate of the film when the system can do it seamlessly
 *   (`Surface.setFrameRate`); the change of mode, when needed, is the screen's ([DisplayModes]);
 * - a stream whose video or audio no decoder takes is a failure ([PlaybackFailure.Decoder]), for the
 *   fallback to transcoding, rather than a silent black picture.
 */
@OptIn(UnstableApi::class)
class ExoVideoPlayer(
    context: Context,
    capabilities: DeviceCapabilities,
    allowedPassthrough: Set<PassthroughFormat>,
    matchFrameRate: Boolean,
    audioOffsetMillis: Int = 0,
    allowDolbyVision: Boolean = true,
    buffer: PlayerBuffer = PlayerBuffer.Tv,
    dataSources: DataSources = DataSources.Direct,
    private val nomad: Nomad? = null,
) : VideoPlayer {
    private val main = Handler(Looper.getMainLooper())
    private val httpFactory =
        DefaultHttpDataSource
            .Factory()
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(true)

    /** The address of the server when the stream was asked for: the phone swaps it for the current one. */
    private var streamBase: String? = null

    /** The phone's margin (M2), none on the TV. */
    private val margin: MarginLoader? =
        nomad?.let { options ->
            MarginLoader(
                disk = options.cache,
                upstream = ResolvingDataSource.Factory(httpFactory) { spec -> spec.withCurrentAddress(options) },
                network = options.network,
                target = options.margin,
                prefix = "p${nextPlayer.incrementAndGet()}|",
            )
        }

    private val exo: ExoPlayer =
        ExoPlayer
            .Builder(context, Renderers(context, capabilities, allowedPassthrough, audioOffsetMillis, allowDolbyVision))
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    margin?.dataSources ?: dataSources.wrap(httpFactory),
                ).setLoadErrorHandlingPolicy(
                    if (nomad != null) NomadRetries() else DefaultLoadErrorHandlingPolicy(LOAD_RETRIES),
                ),
            ).setLoadControl(
                DefaultLoadControl
                    .Builder()
                    .setBufferDurationsMs(buffer.minMillis, buffer.maxMillis, buffer.startMillis, buffer.rebufferMillis)
                    .setTargetBufferBytes(buffer.targetBytes)
                    .setPrioritizeTimeOverSizeThresholds(false)
                    .build(),
            ).setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .build()
            .apply {
                setVideoChangeFrameRateStrategy(
                    if (matchFrameRate) {
                        C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
                    } else {
                        C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
                    },
                )
                if (nomad != null) {
                    // A call or another sound pauses, then resumes; headphones unplugged pause (M2).
                    setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                            .build(),
                        true,
                    )
                    setHandleAudioBecomingNoisy(true)
                }
            }

    /** Which stream the margin follows: one more at each [load]. */
    private var streamId = 0

    /** The film has played since the last [load]: the margin may start. */
    private var started = false
    private var segments: MarginLoader.Source? = null
    private val follow =
        object : Runnable {
            override fun run() {
                followMargin()
                main.postDelayed(this, MARGIN_LOOK_MS)
            }
        }

    override val player: Player get() = exo

    private val eventFlow =
        MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: Flow<PlayerEvent> = eventFlow

    private var stream: PlaybackStream? = null
    private var pendingAudio: Int? = null
    private var pendingSubtitles: Int? = null
    private var tracksApplied = false
    private var videoDecoder: String? = null
    private var audioDecoder: String? = null
    private var passthrough = false
    private var dropped = 0
    private var bandwidth: Long? = null

    init {
        exo.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    eventFlow.tryEmit(PlayerEvent.Failed(error.toFailure(), "${error.errorCodeName}: ${error.message}"))
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) eventFlow.tryEmit(PlayerEvent.Ended)
                }

                override fun onTracksChanged(tracks: Tracks) {
                    if (tracks.groups.isEmpty()) return
                    checkDecoders(tracks)
                    if (!tracksApplied) {
                        tracksApplied = true
                        applyAudio(pendingAudio)
                        applySubtitles(pendingSubtitles)
                    }
                }
            },
        )
        exo.addAnalyticsListener(
            object : AnalyticsListener {
                override fun onVideoDecoderInitialized(
                    eventTime: AnalyticsListener.EventTime,
                    decoderName: String,
                    initializedTimestampMs: Long,
                    initializationDurationMs: Long,
                ) {
                    videoDecoder = decoderName
                }

                override fun onAudioDecoderInitialized(
                    eventTime: AnalyticsListener.EventTime,
                    decoderName: String,
                    initializedTimestampMs: Long,
                    initializationDurationMs: Long,
                ) {
                    audioDecoder = decoderName
                }

                override fun onAudioTrackInitialized(
                    eventTime: AnalyticsListener.EventTime,
                    audioTrackConfig: AudioSink.AudioTrackConfig,
                ) {
                    passthrough = audioTrackConfig.encoding !in PCM_ENCODINGS
                    if (passthrough) audioDecoder = null
                }

                override fun onDroppedVideoFrames(
                    eventTime: AnalyticsListener.EventTime,
                    droppedFrames: Int,
                    elapsedMs: Long,
                ) {
                    dropped += droppedFrames
                }

                override fun onBandwidthEstimate(
                    eventTime: AnalyticsListener.EventTime,
                    totalLoadTimeMs: Int,
                    totalBytesLoaded: Long,
                    bitrateEstimate: Long,
                ) {
                    bandwidth = bitrateEstimate
                }
            },
        )
    }

    override fun load(
        stream: PlaybackStream,
        start: Duration,
        audioIndex: Int?,
        subtitleIndex: Int?,
    ) {
        this.stream = stream
        pendingAudio = audioIndex
        pendingSubtitles = subtitleIndex
        tracksApplied = false
        videoDecoder = null
        audioDecoder = null
        passthrough = false
        httpFactory.setDefaultRequestProperties(stream.headers)
        streamId++
        started = false
        segments = null
        streamBase = nomad?.address()?.trimEnd('/')?.takeIf { stream.url.startsWith(it) }
        if (margin != null) {
            if (streamId > 1) margin.newStream()
            main.removeCallbacks(follow)
            main.post(follow)
        }
        val item =
            MediaItem
                .Builder()
                .setUri(stream.url)
                .setMimeType(if (stream.isHls) MimeTypes.APPLICATION_M3U8 else null)
                .setSubtitleConfigurations(externalSubtitles(stream))
                .build()
        // A transcoded stream (HLS) covers the whole film too: the player starts where it was asked.
        exo.setMediaItem(item, start.inWholeMilliseconds)
        exo.trackSelectionParameters =
            exo.trackSelectionParameters
                .buildUpon()
                .clearOverrides()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitleIndex == null)
                .build()
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun externalSubtitles(stream: PlaybackStream): List<MediaItem.SubtitleConfiguration> =
        stream.subtitles
            .filter { it.method == SubtitleMethod.External && it.url != null }
            .map { delivery ->
                val track = stream.source.streams.firstOrNull { it.index == delivery.index }
                MediaItem.SubtitleConfiguration
                    .Builder(Uri.parse(checkNotNull(delivery.url)))
                    .setId(externalId(delivery.index))
                    .setMimeType(subtitleMime(delivery.codec ?: track?.codec))
                    .setLanguage(track?.language)
                    .setLabel(track?.title)
                    .setSelectionFlags(if (track?.isForced == true) C.SELECTION_FLAG_FORCED else 0)
                    .build()
            }

    override fun play() = exo.play()

    override fun pause() = exo.pause()

    override fun seekTo(position: Duration) = exo.seekTo(position.inWholeMilliseconds.coerceAtLeast(0))

    override fun selectAudio(index: Int): Boolean {
        if (!tracksApplied) {
            pendingAudio = index
            return stream?.let { it.method == PlayMethod.DirectPlay } ?: false
        }
        return applyAudio(index)
    }

    override fun selectSubtitles(index: Int?): Boolean {
        if (!tracksApplied) {
            pendingSubtitles = index
            return true
        }
        return applySubtitles(index)
    }

    /** The audio track of the file (direct play): the nth audio stream is the nth audio group. */
    private fun applyAudio(index: Int?): Boolean {
        val current = stream ?: return false
        if (index == null ||
            current.method != PlayMethod.DirectPlay
        ) {
            return index == null || index == current.audioIndex
        }
        val order =
            current.source.streams
                .filter { it.type == StreamType.Audio && !it.isExternal }
                .indexOfFirst { it.index == index }
        val group =
            exo.currentTracks.groups
                .filter { it.type == C.TRACK_TYPE_AUDIO }
                .getOrNull(order)
        group?.let {
            exo.trackSelectionParameters =
                exo.trackSelectionParameters
                    .buildUpon()
                    .setOverrideForType(TrackSelectionOverride(it.mediaTrackGroup, 0))
                    .build()
        }
        return group != null
    }

    /**
     * The subtitles: an external file is found by its id; a track of the file (direct play) is the
     * nth subtitle group of the file. Subtitles drawn by the server are not the player's.
     */
    private fun applySubtitles(index: Int?): Boolean {
        val current = stream ?: return false
        val builder = exo.trackSelectionParameters.buildUpon()
        if (index == null) {
            exo.trackSelectionParameters = builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            return true
        }
        val method = current.delivery(index)?.method
        // Drawn into the picture by the server: nothing to show here, unless it is this stream's.
        val group = if (method == SubtitleMethod.Encode) null else subtitleGroup(current, index, method)
        group?.let {
            exo.trackSelectionParameters =
                builder
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(it.mediaTrackGroup, 0))
                    .build()
        }
        return group != null || (method == SubtitleMethod.Encode && current.subtitleIndex == index)
    }

    private fun subtitleGroup(
        current: PlaybackStream,
        index: Int,
        method: SubtitleMethod?,
    ): Tracks.Group? {
        val text = exo.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        return when {
            method == SubtitleMethod.External -> {
                text.firstOrNull { group ->
                    group
                        .getTrackFormat(0)
                        .id
                        .orEmpty()
                        .endsWith(externalId(index))
                }
            }

            method == SubtitleMethod.Embed && current.method == PlayMethod.DirectPlay -> {
                val order =
                    current.source.streams
                        .filter { it.type == StreamType.Subtitle && !it.isExternal }
                        .indexOfFirst { it.index == index }
                text
                    .filterNot {
                        it
                            .getTrackFormat(0)
                            .id
                            .orEmpty()
                            .contains(EXTERNAL)
                    }.getOrNull(order)
            }

            else -> {
                null
            }
        }
    }

    /** No decoder for the video (or for every audio track): the server must transcode. */
    private fun checkDecoders(tracks: Tracks) {
        val video = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val missing =
            when {
                video.isNotEmpty() && video.none { it.isSupported } -> "video ${video.first().getTrackFormat(0).codecs}"

                audio.isNotEmpty() && audio.none { it.isSupported } -> "audio ${audio.first().getTrackFormat(
                    0,
                ).sampleMimeType}"

                else -> null
            }
        if (missing != null) {
            main.post { eventFlow.tryEmit(PlayerEvent.Failed(PlaybackFailure.Decoder, "No decoder for $missing")) }
        }
    }

    override fun snapshot(): PlayerSnapshot {
        val video = exo.videoFormat
        val audio = exo.audioFormat
        return PlayerSnapshot(
            position = exo.currentPosition.coerceAtLeast(0).milliseconds,
            duration =
                exo.duration
                    .takeIf { it != C.TIME_UNSET }
                    ?.coerceAtLeast(0)
                    ?.milliseconds ?: Duration.ZERO,
            buffered = exo.totalBufferedDuration.milliseconds,
            playing = exo.isPlaying,
            buffering = exo.playbackState == Player.STATE_BUFFERING,
            video = video?.let { videoOutput(it) },
            audio = audio?.let { audioOutput(it) },
            droppedFrames = dropped,
            bandwidth = bandwidth,
            margin =
                margin?.status(
                    exo.currentPosition.coerceAtLeast(0).milliseconds,
                    exo.bufferedPosition.milliseconds,
                ),
        )
    }

    /** Tells the margin what the player plays and where (M2): the segments of the playlist, or the file. */
    private fun followMargin() {
        val loader = margin ?: return
        val current = stream ?: return
        if (exo.isPlaying) started = true
        val duration = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.milliseconds
        val source =
            if (current.isHls) {
                segments ?: (exo.currentManifest as? HlsManifest)?.mediaPlaylist?.let { playlist ->
                    MarginLoader.segments(streamId, playlist).also { segments = it }
                }
            } else {
                val size = current.source.sizeBytes
                if (duration != null && MarginLoader.hasLength(size)) {
                    segments ?: MarginLoader.file(streamId, Uri.parse(current.url), checkNotNull(size), duration).also {
                        segments = it
                    }
                } else {
                    null
                }
            }
        loader.update(
            source,
            MarginInputs(
                position = exo.currentPosition.coerceAtLeast(0).milliseconds,
                memoryEnd = exo.bufferedPosition.milliseconds,
                playerLoading = exo.isLoading,
                started = started,
            ),
        )
    }

    /** The current address of the server in place of the one the stream was asked through. */
    @Suppress("ReturnCount") // Each case where the address stays.
    private fun DataSpec.withCurrentAddress(options: Nomad): DataSpec {
        val from = streamBase ?: return this
        val to = options.address()?.trimEnd('/') ?: return this
        val address = uri.toString()
        if (from == to || !address.startsWith(from)) return this
        return withUri(Uri.parse(to + address.removePrefix(from)))
    }

    private fun videoOutput(format: Format) =
        VideoOutput(
            codec = listOfNotNull(format.sampleMimeType, format.codecs).joinToString(" · "),
            width = format.width,
            height = format.height,
            frameRate = format.frameRate.takeIf { it > 0f },
            range =
                when {
                    format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision"
                    format.colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 -> "HDR10"
                    format.colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG -> "HLG"
                    else -> "SDR"
                },
            decoder = videoDecoder,
        )

    private fun audioOutput(format: Format) =
        AudioOutput(
            codec = listOfNotNull(format.sampleMimeType, format.codecs).joinToString(" · "),
            channels = format.channelCount,
            sampleRate = format.sampleRate,
            passthrough = passthrough,
            decoder = audioDecoder,
        )

    override fun release() {
        main.removeCallbacksAndMessages(null)
        margin?.release()
        exo.release()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 15_000
        const val SEEK_INCREMENT_MS = 10_000L

        /** How often the margin learns where the player is (M2). */
        const val MARGIN_LOOK_MS = 500L
        val nextPlayer = AtomicInteger()

        /** Network errors are retried patiently (about 90 s): the buffer keeps playing meanwhile. */
        const val LOAD_RETRIES = 20
        const val EXTERNAL = "external-"

        val PCM_ENCODINGS =
            setOf(
                C.ENCODING_PCM_16BIT,
                C.ENCODING_PCM_8BIT,
                C.ENCODING_PCM_24BIT,
                C.ENCODING_PCM_32BIT,
                C.ENCODING_PCM_FLOAT,
                C.ENCODING_INVALID,
            )

        fun externalId(index: Int) = "$EXTERNAL$index"

        fun subtitleMime(codec: String?): String =
            when (codec?.lowercase()) {
                "ass", "ssa" -> MimeTypes.TEXT_SSA
                "vtt", "webvtt" -> MimeTypes.TEXT_VTT
                "ttml" -> MimeTypes.APPLICATION_TTML
                else -> MimeTypes.APPLICATION_SUBRIP
            }

        /** What the error of ExoPlayer means for the next step ([fr.guim.cinedoury.core.model.PlaybackFallback]). */
        fun PlaybackException.toFailure(): PlaybackFailure =
            when (errorCode) {
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> PlaybackFailure.Server
                in IO_ERRORS -> PlaybackFailure.Network
                in PARSING_ERRORS -> PlaybackFailure.Format
                in DECODING_ERRORS, in AUDIO_TRACK_ERRORS -> PlaybackFailure.Decoder
                else -> PlaybackFailure.Unknown
            }

        // The families of error codes of Media3: 2xxx input/output, 3xxx parsing, 4xxx decoding, 5xxx audio track.
        val IO_ERRORS = 2000..2999
        val PARSING_ERRORS = 3000..3999
        val DECODING_ERRORS = 4000..4999
        val AUDIO_TRACK_ERRORS = 5000..5999
    }

    /**
     * The renderers of the device: decoder fallback, the passthrough allowed, no Dolby Vision decoder
     * without the screen for it.
     */
    private class Renderers(
        context: Context,
        private val capabilities: DeviceCapabilities,
        private val allowed: Set<PassthroughFormat>,
        private val audioOffsetMillis: Int,
        allowDolbyVision: Boolean,
    ) : DefaultRenderersFactory(context) {
        init {
            setEnableDecoderFallback(true)
            // « Dolby Vision autorisé » off (`SPEC.md` § 4.17): never the Dolby Vision decoder either.
            val dolbyVision = allowDolbyVision && DynamicRange.DolbyVision in capabilities.display.hdr
            setMediaCodecSelector { mimeType, secure, tunneling ->
                if (mimeType == MimeTypes.VIDEO_DOLBY_VISION && !dolbyVision) {
                    emptyList()
                } else {
                    MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)
                }
            }
        }

        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioOutputPlaybackParams: Boolean,
        ): AudioSink {
            val blocked = PASSTHROUGH_MIMES.filterKeys { it !in allowed }.values.toSet()
            val output =
                object : ForwardingAudioOutputProvider(AudioTrackAudioOutputProvider.Builder(context).build()) {
                    override fun getFormatSupport(
                        formatConfig: AudioOutputProvider.FormatConfig,
                    ): AudioOutputProvider.FormatSupport =
                        if (formatConfig.format.sampleMimeType?.let { it in blocked } == true) {
                            AudioOutputProvider.FormatSupport.UNSUPPORTED
                        } else {
                            super.getFormatSupport(formatConfig)
                        }
                }
            val sink =
                DefaultAudioSink
                    .Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                    .setAudioOutputProvider(output)
                    .build()
            if (audioOffsetMillis == 0) return sink
            // « Décalage audio »: the picture follows the clock of the sound; a clock ahead of the
            // sound heard shows each picture sooner, so the sound comes later than the picture.
            return object : ForwardingAudioSink(sink) {
                override fun getCurrentPositionUs(sourceEnded: Boolean): Long =
                    AudioOffset.clock(super.getCurrentPositionUs(sourceEnded), audioOffsetMillis)
            }
        }

        private companion object {
            val PASSTHROUGH_MIMES =
                mapOf(
                    PassthroughFormat.Ac3 to MimeTypes.AUDIO_AC3,
                    PassthroughFormat.Eac3 to MimeTypes.AUDIO_E_AC3,
                    PassthroughFormat.Eac3Atmos to MimeTypes.AUDIO_E_AC3_JOC,
                    PassthroughFormat.Dts to MimeTypes.AUDIO_DTS,
                    PassthroughFormat.DtsHd to MimeTypes.AUDIO_DTS_HD,
                    PassthroughFormat.TrueHd to MimeTypes.AUDIO_TRUEHD,
                )
        }
    }
}
