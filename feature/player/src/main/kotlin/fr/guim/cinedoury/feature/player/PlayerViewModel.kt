package fr.guim.cinedoury.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import fr.guim.cinedoury.core.data.ActiveSession
import fr.guim.cinedoury.core.data.ConnectionMode
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.UserDataUpdates
import fr.guim.cinedoury.core.data.home.HomeRepository
import fr.guim.cinedoury.core.data.home.ProfileKey
import fr.guim.cinedoury.core.model.DeviceCapabilities
import fr.guim.cinedoury.core.model.DirectPlayMode
import fr.guim.cinedoury.core.model.FallbackAction
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.MediaSegment
import fr.guim.cinedoury.core.model.NetworkKind
import fr.guim.cinedoury.core.model.PlayMethod
import fr.guim.cinedoury.core.model.PlaybackFailure
import fr.guim.cinedoury.core.model.PlaybackFallback
import fr.guim.cinedoury.core.model.PlaybackInfoRequest
import fr.guim.cinedoury.core.model.PlaybackProfile
import fr.guim.cinedoury.core.model.PlaybackProfiles
import fr.guim.cinedoury.core.model.PlaybackReport
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.model.PlaybackRules
import fr.guim.cinedoury.core.model.PlaybackStream
import fr.guim.cinedoury.core.model.PlayerSettings
import fr.guim.cinedoury.core.model.QualityGovernor
import fr.guim.cinedoury.core.model.SegmentAction
import fr.guim.cinedoury.core.model.StillWatching
import fr.guim.cinedoury.core.model.TrackPreselection
import fr.guim.cinedoury.core.model.TrackSelection
import fr.guim.cinedoury.core.model.UserData
import fr.guim.cinedoury.core.playback.NetworkMonitor
import fr.guim.cinedoury.core.playback.PlaybackReporter
import fr.guim.cinedoury.core.playback.PlayerEvent
import fr.guim.cinedoury.core.playback.PlayerSettingsStore
import fr.guim.cinedoury.core.playback.PlayerSnapshot
import fr.guim.cinedoury.core.playback.VideoPlayer
import fr.guim.cinedoury.core.playback.VideoPlayerFactory
import fr.guim.cinedoury.core.source.GestureLog
import fr.guim.cinedoury.core.source.LibrarySource
import fr.guim.cinedoury.core.source.PlaybackLog
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.ui.Events
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The player (`SPEC.md` § 4.11): asks the server how to play the item with the profile of the
 * device, plays it, reports to the server, falls back to transcoding when the device cannot read
 * the file, skips the segments, chains the episodes, and on leaving puts the position and « vu »
 * in the cache of the home and of the details screens.
 *
 * @param reportScope outlives the screen: the last report leaves after it.
 * @param network the network of a phone (M2, `SPEC.md` § 4.27): its default quality follows it (Wi-Fi or
 * mobile), and the quality goes down and back up with the margin ([QualityGovernor]); null on the TV.
 */
@Suppress("TooManyFunctions", "LargeClass", "LongParameterList") // A gesture each; the services it joins.
class PlayerViewModel(
    request: PlaybackRequest,
    private val sessions: SessionManager,
    private val settingsStore: PlayerSettingsStore,
    private val readCapabilities: () -> DeviceCapabilities,
    private val players: VideoPlayerFactory,
    private val home: HomeRepository,
    private val updates: UserDataUpdates,
    private val reportScope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
    private val network: NetworkMonitor? = null,
) : ViewModel(),
    PlayerActions {
    private val active: ActiveSession? = sessions.active.value
    private val source: LibrarySource? = active?.library
    private val local = MutableStateFlow(PlayerState(request.item, trailer = request.trailer))
    val state: StateFlow<PlayerState> = local.asStateFlow()

    private val surfacePlayer = MutableStateFlow<Player?>(null)

    /** The Media3 player, for the surface of the screen, once made. */
    val surface: StateFlow<Player?> = surfacePlayer.asStateFlow()

    /** The player is done: back to the screen it came from. */
    val closed = Events<Unit>()

    /** The server rejected the token: the profile is signed out. */
    val sessionEnded = Events<Unit>()

    private var request = request
    private var player: VideoPlayer? = null
    private var reporter: PlaybackReporter? = null
    private var profile: PlaybackProfile? = null

    /** The stream comes in MPEG-TS segments, not fMP4 (G, M2: [PlaybackFallback.wantsTs]). */
    private var tsSegments = false

    /** The profile sent to the server: in MPEG-TS segments once they are needed. */
    private val streamProfile: PlaybackProfile?
        get() = profile?.let { if (tsSegments) PlaybackFallback.tsProfile(it) else it }
    private var stillWatching = StillWatching()
    private var ticker: Job? = null
    private var fellBack = false
    private var lastSkipped: Int? = null
    private var nextDismissed = false
    private var countdown = Duration.ZERO
    private var bufferingFor = Duration.ZERO
    private var infoVisible = false
    private var finished = false

    /** The quality was chosen in the panel: nothing changes it any more (M2). */
    private var qualityChosen = false
    private var governor: QualityGovernor? = null
    private var networkKind: NetworkKind? = network?.state?.value?.kind
    private var sampledAt = Duration.ZERO
    private var noticeUntil = Duration.ZERO
    private var elapsed = Duration.ZERO

    init {
        viewModelScope.launch { start(request) }
    }

    @Suppress("CyclomaticComplexMethod") // The steps of a start, the phone's quality among them.
    private suspend fun start(request: PlaybackRequest) {
        val source = source ?: return
        guarded {
            val settings = settingsStore.current()
            val capabilities = readCapabilities()
            val remote = active?.connection?.mode == ConnectionMode.Remote
            networkKind = network?.state?.value?.kind
            val profile = PlaybackProfiles.build(capabilities, settings.profileOptions(remote, networkKind))
            this.profile = profile
            stillWatching = StillWatching(settings.stillWatchingAfter).also { it.restoreFrom(stillWatching) }
            val item = if (request.trailer) request.item else source.item(request.item.id)
            val selection = selection(source, request, item)
            val start =
                item.userData?.playbackPosition?.takeIf { request.resume && item.progress != null } ?: Duration.ZERO
            local.update {
                it.copy(
                    item = item,
                    settings = settings,
                    capabilities = capabilities,
                    profile = profile,
                    audioIndex = selection?.audioIndex,
                    subtitleIndex = selection?.subtitleIndex,
                    duration = item.runtime ?: it.duration,
                    // The default quality of the settings (`SPEC.md` § 4.17), unless one was chosen.
                    maxBitrate = it.maxBitrate ?: settings.defaultQuality(remote, networkKind),
                )
            }
            if (network != null && !qualityChosen && !request.trailer) {
                governor =
                    QualityGovernor(
                        ceiling = settings.defaultQuality(remote, networkKind),
                        sourceBitrate = item.mediaSources.firstOrNull()?.bitrate,
                        current = local.value.maxBitrate,
                    )
            }
            if (!request.trailer) {
                viewModelScope.launch { loadSegments(source, item) }
                viewModelScope.launch { loadNeighbours(source, item) }
            }
            val stream = source.firstStream(item, settings, selection, start)
            val player = player ?: players.create(capabilities, settings).also { created -> watch(created) }
            this.player = player
            surfacePlayer.value = player.player
            local.update { it.copy(stream = stream, loading = false, error = null) }
            logDecision(item, stream, fallback = false, settings.directPlay == DirectPlayMode.Forced)
            player.load(stream, start, selection?.audioIndex, selection?.subtitleIndex)
            if (!request.trailer) {
                reporter = PlaybackReporter(source.playback, reportScope) { report() }.also { it.start() }
            }
            startTicker()
        }
    }

    /** The version and tracks chosen on the details screen, else those of the profile (`SPEC.md` § 4.10). */
    private suspend fun selection(
        source: LibrarySource,
        request: PlaybackRequest,
        item: MediaItem,
    ): TrackSelection? {
        request.selection?.let { return it }
        val version = item.mediaSources.firstOrNull() ?: return null
        return TrackPreselection.choose(version, source.playbackPreferences())
    }

    private fun StillWatching.restoreFrom(previous: StillWatching) {
        repeat(previous.inARow - 1) { mayAutoplayNext() }
    }

    private suspend fun loadSegments(
        source: LibrarySource,
        item: MediaItem,
    ) {
        val segments = optional { source.playback.segments(item.id) }.orEmpty()
        local.update { it.copy(segments = segments) }
    }

    /**
     * The episodes before and after this one, in the order of the series, from a season to the next;
     * the specials only around a special.
     */
    private suspend fun loadNeighbours(
        source: LibrarySource,
        item: MediaItem,
    ) {
        if (item.kind != ItemKind.Episode) return
        val series = item.episode?.seriesId ?: return
        val episodes = optional { source.episodes(series) }.orEmpty()
        val special = item.episode?.seasonNumber == 0
        val list = episodes.filter { special || it.episode?.seasonNumber != 0 }
        val index = list.indexOfFirst { it.id == item.id }
        if (index < 0) return
        local.update { it.copy(previous = list.getOrNull(index - 1), next = list.getOrNull(index + 1)) }
    }

    private fun watch(player: VideoPlayer) {
        viewModelScope.launch {
            player.events.collect { event ->
                when (event) {
                    is PlayerEvent.Failed -> failed(event.failure, event.detail)
                    PlayerEvent.Ended -> ended()
                }
            }
        }
    }

    /** The fallback (`SPEC.md` § 4.11): once, to transcoding, at the same position, without a screen. */
    private fun failed(
        failure: PlaybackFailure,
        detail: String,
    ) {
        val stream = local.value.stream ?: return
        val action = PlaybackFallback.next(stream, failure, tsTried = tsSegments)
        if (action == FallbackAction.TsSegments) {
            // fMP4 segments the player cannot read (G, M2): the same stream in MPEG-TS segments.
            tsSegments = true
            local.update { it.copy(fallback = detail) }
            reload()
        } else if (!fellBack && action == FallbackAction.Transcode) {
            fellBack = true
            local.update { it.copy(fallback = detail) }
            reload(allowDirect = false)
        } else {
            reporter?.stop(report(failed = true))
            reporter = null
            ticker?.cancel()
            local.update { it.copy(error = PlayerError.Playback(failure, detail), playing = false) }
        }
    }

    private fun ended() {
        val state = local.value
        val next = state.next
        if (next != null && state.settings.autoplayNext && !nextDismissed) {
            autoplay()
        } else {
            close()
        }
    }

    /** The countdown ran out, or the episode ended: the next one, unless it is time to ask. */
    private fun autoplay() {
        if (local.value.stillWatching) return
        if (stillWatching.mayAutoplayNext()) {
            startNext()
        } else {
            player?.pause()
            local.update { it.copy(stillWatching = true, nextShown = false) }
        }
    }

    private fun startNext() {
        startEpisode(local.value.next ?: return)
    }

    /** Leaves this episode where it stands (its position saved as on « Retour ») for [next]. */
    private fun startEpisode(next: MediaItem) {
        leave()
        val settings = local.value.settings
        fellBack = false
        tsSegments = false
        lastSkipped = null
        nextDismissed = false
        countdown = Duration.ZERO
        local.value =
            PlayerState(item = next, settings = settings, capabilities = local.value.capabilities, profile = profile)
        request = PlaybackRequest(next, resume = next.progress != null)
        viewModelScope.launch { start(request) }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker =
            viewModelScope.launch {
                while (isActive) {
                    tick()
                    delay(TICK)
                }
            }
    }

    /** What the [GestureLog] was told last: playing, paused or buffering. */
    private var announced: String? = null

    /** A jump asked, not yet playing at its new place ([GestureLog]). */
    private var jumping = false

    /**
     * Playing, paused or loading, as it changes, and a jump played at its new place: the ends of
     * « Lire », a pause, a jump for `ci/couch-gestures.sh` ([GestureLog]).
     */
    private fun announce(snapshot: PlayerSnapshot) {
        val motion =
            when {
                snapshot.buffering -> "buffering"
                snapshot.playing -> "playing"
                else -> "paused"
            }
        if (motion != announced) {
            announced = motion
            GestureLog.mark(motion)
        }
        if (jumping && motion == "playing") {
            jumping = false
            GestureLog.mark("jumped")
        }
    }

    private fun tick() {
        val player = player ?: return
        val snapshot = player.snapshot()
        val state = local.value
        val duration = snapshot.duration.takeIf { it.isPositive() } ?: state.duration
        val position = snapshot.position
        announce(snapshot)
        bufferingFor = if (snapshot.buffering && state.stream != null) bufferingFor + TICK else Duration.ZERO
        val skip = segmentButton(player, state, position)
        val shows = showsNext(state, position, duration)
        if (shows && snapshot.playing) countdown += TICK
        if (!shows) countdown = Duration.ZERO
        val total = state.settings.nextCountdownSeconds.seconds
        val left = countdownLeft(state, shows, position, duration)
        local.update {
            it.copy(
                position = position,
                duration = duration,
                buffered = snapshot.buffered,
                playing = snapshot.playing,
                buffering = snapshot.buffering,
                reconnecting = bufferingFor >= RECONNECTING,
                margin = snapshot.margin,
                offline = snapshot.margin?.offline == true,
                qualityReduced = elapsed < noticeUntil,
                skip = skip,
                nextShown = shows && !it.stillWatching,
                nextCountdown = left?.div(total)?.toFloat(),
                nextSeconds = left?.wholeSecondsUp(),
                info = if (infoVisible) snapshot else null,
            )
        }
        if (left != null && !left.isPositive() && PlaybackRules.countdownStartsNext(state.segments)) autoplay()
        adaptQuality(snapshot)
    }

    /**
     * The phone's quality (M2, `SPEC.md` § 4.27), once a second: the default of the new network when
     * the phone changes network, one step down when the margin melts, back up when it is full again.
     * Never when the user chose a quality.
     */
    @Suppress("ReturnCount") // Nothing to adapt: on the TV, without a margin, a chosen quality, loading.
    private fun adaptQuality(snapshot: PlayerSnapshot) {
        elapsed += TICK
        val monitor = network ?: return
        val margin = snapshot.margin ?: return
        if (qualityChosen || local.value.loading || elapsed - sampledAt < QUALITY_LOOK) return
        sampledAt = elapsed
        val kind = monitor.state.value.kind
        if (kind != networkKind && monitor.state.value.online) {
            networkKind = kind
            val remote = active?.connection?.mode == ConnectionMode.Remote
            val ceiling = local.value.settings.defaultQuality(remote, kind)
            val current = local.value.maxBitrate
            governor =
                QualityGovernor(
                    ceiling,
                    local.value.item.mediaSources
                        .firstOrNull()
                        ?.bitrate,
                    current,
                )
            if (ceiling != null && (current == null || current > ceiling)) changeQuality(ceiling)
            return
        }
        val change = governor?.sample(elapsed, margin, snapshot.playing, margin.bandwidth) ?: return
        if (change.down) {
            noticeUntil = elapsed + NOTICE
            local.update { it.copy(qualityReduced = true) }
        }
        GestureLog.mark("quality ${change.maxBitrate ?: "original"}")
        changeQuality(change.maxBitrate)
    }

    private fun changeQuality(maxBitrate: Int?) {
        if (maxBitrate == local.value.maxBitrate) return
        reload(maxBitrate = maxBitrate)
    }

    /**
     * What is left of the countdown of « Épisode suivant », null without one. Without an Outro
     * segment, it ends with the episode, never before (essai réel 5).
     */
    private fun countdownLeft(
        state: PlayerState,
        shows: Boolean,
        position: Duration,
        duration: Duration,
    ): Duration? {
        val total = state.settings.nextCountdownSeconds.seconds
        if (!shows || !state.settings.autoplayNext || !total.isPositive()) return null
        return PlaybackRules.countdownLeft(position, duration, state.segments, countdown, total)
    }

    /** The seconds left, rounded up: « dans 10 s » until the last tenth. */
    private fun Duration.wholeSecondsUp(): Int = ((inWholeMilliseconds + MILLIS - 1) / MILLIS).toInt()

    private fun showsNext(
        state: PlayerState,
        position: Duration,
        duration: Duration,
    ): Boolean =
        !state.trailer && !nextDismissed &&
            PlaybackRules.showsNext(position, duration, state.segments, state.next != null)

    /** The segment « Passer » shows for; or the jump over it, once, when the setting says so. */
    private fun segmentButton(
        player: VideoPlayer,
        state: PlayerState,
        position: Duration,
    ): MediaSegment? {
        val segment = PlaybackRules.skippableAt(position, state.segments)?.takeIf { !state.trailer } ?: return null
        return when (state.settings.segments) {
            SegmentAction.Button -> {
                segment
            }

            SegmentAction.Skip -> {
                val index = state.segments.indexOf(segment)
                if (lastSkipped != index) {
                    lastSkipped = index
                    player.seekTo(segment.end)
                }
                null
            }

            SegmentAction.Nothing -> {
                null
            }
        }
    }

    private fun report(
        failed: Boolean = false,
        position: Duration? = null,
    ): PlaybackReport {
        val state = local.value
        val stream = state.stream
        return PlaybackReport(
            item = state.item.id,
            sourceId =
                stream?.source?.id ?: state.item.mediaSources
                    .firstOrNull()
                    ?.id
                    .orEmpty(),
            playSessionId = stream?.playSessionId,
            position = position ?: player?.snapshot()?.position ?: state.position,
            paused = player?.snapshot()?.playing == false,
            method = state.method ?: PlayMethod.DirectPlay,
            audioIndex = state.audioIndex,
            subtitleIndex = state.subtitleIndex,
            failed = failed,
        )
    }

    /** Asks the server again, at the same position: another track, another quality, the fallback. */
    private fun reload(
        audioIndex: Int? = local.value.audioIndex,
        subtitleIndex: Int? = local.value.subtitleIndex,
        maxBitrate: Int? = local.value.maxBitrate,
        allowDirect: Boolean = !fellBack && local.value.settings.directPlay != DirectPlayMode.Never,
    ) {
        val source = source ?: return
        val profile = streamProfile ?: return
        val player = player ?: return
        val state = local.value
        val at = player.snapshot().position.takeIf { it.isPositive() } ?: state.position
        reporter?.restart()
        viewModelScope.launch {
            guarded {
                val stream =
                    source.playback.playbackInfo(
                        PlaybackInfoRequest(
                            item = state.item.id,
                            profile = profile,
                            sourceId = state.stream?.source?.id,
                            audioIndex = audioIndex,
                            subtitleIndex = subtitleIndex,
                            start = at,
                            maxBitrate = maxBitrate,
                            allowDirect = allowDirect,
                            forceDirect = allowDirect && local.value.settings.directPlay == DirectPlayMode.Forced,
                        ),
                    )
                logDecision(state.item, stream, fallback = fellBack, forced = false)
                local.update {
                    it.copy(
                        stream = stream,
                        audioIndex = audioIndex,
                        subtitleIndex = subtitleIndex,
                        maxBitrate = maxBitrate,
                    )
                }
                player.load(stream, at, audioIndex, subtitleIndex)
                reporter?.start()
            }
        }
    }

    /**
     * What the server is first asked: the choices of the details screen, the settings of the device. An
     * H.264 the server would copy into fMP4 segments may start before zero, which Media3 refuses: it is
     * asked again at once in MPEG-TS segments (G, M2, [PlaybackFallback.wantsTs]).
     */
    private suspend fun LibrarySource.firstStream(
        item: MediaItem,
        settings: PlayerSettings,
        selection: TrackSelection?,
        start: Duration,
    ): PlaybackStream {
        val offered = playback.playbackInfo(firstRequest(item, checkNotNull(streamProfile), settings, selection, start))
        if (tsSegments || !PlaybackFallback.wantsTs(offered)) return offered
        tsSegments = true
        return playback.playbackInfo(firstRequest(item, checkNotNull(streamProfile), settings, selection, start))
    }

    private fun firstRequest(
        item: MediaItem,
        profile: PlaybackProfile,
        settings: PlayerSettings,
        selection: TrackSelection?,
        start: Duration,
    ) = PlaybackInfoRequest(
        item = item.id,
        profile = profile,
        sourceId = selection?.sourceId,
        audioIndex = selection?.audioIndex,
        subtitleIndex = selection?.subtitleIndex,
        start = start,
        maxBitrate = local.value.maxBitrate,
        allowDirect = !fellBack && settings.directPlay != DirectPlayMode.Never,
        forceDirect = !fellBack && settings.directPlay == DirectPlayMode.Forced,
    )

    /** What was played and how, for the diagnostic report (`SPEC.md` § 4.19). */
    private fun logDecision(
        item: MediaItem,
        stream: PlaybackStream,
        fallback: Boolean,
        forced: Boolean,
    ) {
        PlaybackLog.record(
            PlaybackLog.Entry(
                at = clock.instant(),
                title = item.title,
                method = stream.method,
                reasons = stream.reasons,
                container = stream.container,
                video = stream.source.video?.codec,
                audio =
                    stream.source.audio
                        .firstOrNull { it.index == stream.audioIndex }
                        ?.codec,
                bitrate = stream.maxBitrate,
                fallback = fallback,
                forced = forced && stream.reasons.isNotEmpty(),
            ),
        )
    }

    override fun interacted() = stillWatching.onInteraction()

    override fun togglePlay() {
        val player = player ?: return
        if (player.snapshot().playing) pause() else play()
    }

    override fun play() {
        player?.play()
        local.update { it.copy(playing = true) }
        reporter?.progress()
    }

    override fun pause() {
        player?.pause()
        local.update { it.copy(playing = false) }
        reporter?.progress()
    }

    override fun jump(forward: Boolean) {
        jumping = true
        val state = local.value
        val step = (if (forward) state.settings.seekForwardSeconds else -state.settings.seekBackSeconds).seconds
        seekTo((state.position + step).coerceIn(Duration.ZERO, state.duration))
    }

    override fun seekTo(position: Duration) {
        player?.seekTo(position)
        local.update { it.copy(position = position, scrub = null) }
        reporter?.progress()
    }

    /** Steps of 10 s, then 30 s, 1 min, 2 min, 5 min as the key stays down (`UI.md` § 8). */
    override fun scrub(
        forward: Boolean,
        repeat: Int,
    ) {
        val state = local.value
        val step = SCRUB_STEPS[(repeat / SCRUB_SPEED_UP).coerceAtMost(SCRUB_STEPS.lastIndex)]
        val from = state.scrub ?: state.position
        val to = (if (forward) from + step else from - step).coerceIn(Duration.ZERO, state.duration)
        local.update { it.copy(scrub = to) }
    }

    override fun commitScrub() {
        local.value.scrub?.let(::seekTo)
    }

    override fun cancelScrub() = local.update { it.copy(scrub = null) }

    override fun chapter(forward: Boolean) {
        val state = local.value
        val chapters = state.chapters
        if (chapters.isEmpty()) return
        val current = state.chapterIndex
        val target =
            if (forward) {
                chapters.getOrNull(current + 1)?.start ?: return
            } else {
                // Back to the start of this chapter, or the one before when just past its start.
                val start = chapters.getOrNull(current)?.start ?: Duration.ZERO
                if (state.position - start >
                    CHAPTER_GRACE
                ) {
                    start
                } else {
                    chapters.getOrNull(current - 1)?.start ?: Duration.ZERO
                }
            }
        seekTo(target)
    }

    override fun skipSegment() {
        val segment = local.value.skip ?: return
        lastSkipped = local.value.segments.indexOf(segment)
        seekTo(segment.end)
        local.update { it.copy(skip = null) }
    }

    override fun playNext() {
        stillWatching.onInteraction()
        startNext()
    }

    override fun playPrevious() {
        val previous = local.value.previous ?: return
        stillWatching.onInteraction()
        startEpisode(previous)
    }

    override fun dismissNext() {
        nextDismissed = true
        local.update { it.copy(nextShown = false, nextCountdown = null) }
    }

    override fun keepWatching() {
        stillWatching.onInteraction()
        local.update { it.copy(stillWatching = false) }
        startNext()
    }

    override fun selectAudio(index: Int) {
        if (index == local.value.audioIndex) return
        if (player?.selectAudio(index) == true && local.value.method == PlayMethod.DirectPlay) {
            local.update { it.copy(audioIndex = index) }
            reporter?.progress()
        } else {
            reload(audioIndex = index)
        }
    }

    override fun selectSubtitles(index: Int?) {
        if (index == local.value.subtitleIndex) return
        GestureLog.mark("subtitles ${index ?: "none"}")
        if (player?.selectSubtitles(index) == true) {
            local.update { it.copy(subtitleIndex = index) }
            reporter?.progress()
        } else {
            reload(subtitleIndex = index)
        }
    }

    override fun selectQuality(maxBitrate: Int?) {
        qualityChosen = true
        governor = null
        local.update { it.copy(qualityChosen = true) }
        if (maxBitrate == local.value.maxBitrate) return
        reload(maxBitrate = maxBitrate)
    }

    override fun updateSettings(change: (PlayerSettings) -> PlayerSettings) {
        local.update { it.copy(settings = change(it.settings)) }
        viewModelScope.launch { settingsStore.update(change) }
    }

    override fun toggleInfo() {
        infoVisible = !infoVisible
        local.update { it.copy(info = if (infoVisible) player?.snapshot() else null) }
    }

    override fun retry() {
        fellBack = false
        local.update { it.copy(error = null, loading = true) }
        viewModelScope.launch {
            start(
                request.copy(resume = true, item = local.value.item.withPosition(local.value.position)),
            )
        }
    }

    private fun MediaItem.withPosition(position: Duration): MediaItem =
        copy(userData = (userData ?: UserData(false, Duration.ZERO, 0, false)).copy(playbackPosition = position))

    override fun trickplayUrl(tile: Int): String? {
        val state = local.value
        val info = state.trickplay ?: return null
        val sourceId =
            state.stream?.source?.id ?: state.item.mediaSources
                .firstOrNull()
                ?.id ?: return null
        return source?.playback?.trickplayTileUrl(state.item.id, sourceId, info.width, tile)
    }

    override val streamHeaders: Map<String, String> get() = source?.playback?.streamHeaders.orEmpty()

    /** « Retour »: the player stops, the server and the cache learn where. */
    fun close() {
        leave()
        finished = true
        closed.send(Unit)
    }

    /**
     * Leaves the item played: the last report (at the end of the runtime when it is watched, so that
     * the server marks it too), then its new data in the cache of the home and of the details.
     */
    private fun leave() {
        val state = local.value
        val reporter = reporter ?: return
        this.reporter = null
        val position = player?.snapshot()?.position ?: state.position
        val watched =
            PlaybackRules.isWatched(position, state.duration, state.segments, state.settings.watchedThreshold)
        reporter.stop(report(position = if (watched) state.duration else position))
        if (state.trailer) return
        val before = state.item.userData ?: UserData(false, Duration.ZERO, 0, false)
        val data =
            before.copy(
                played = watched || before.played,
                playbackPosition = if (watched) Duration.ZERO else position,
                playCount = before.playCount + if (watched) 1 else 0,
                lastPlayed = clock.instant(),
            )
        updates.publish(state.item.id, data)
        val profile = active?.let { ProfileKey(it.profile.serverId, it.profile.user.id.value) } ?: return
        reportScope.launch { home.updateUserData(profile, state.item.id, data) }
    }

    override fun onCleared() {
        if (!finished) leave()
        ticker?.cancel()
        surfacePlayer.value = null
        player?.release()
        player = null
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (_: SourceException.Unauthorized) {
            sessions.sessionRejected()
            sessionEnded.send(Unit)
        } catch (error: SourceException) {
            local.update { it.copy(error = PlayerError.Source(error), loading = false) }
        }
    }

    private suspend fun <T> optional(block: suspend () -> T): T? =
        try {
            block()
        } catch (_: SourceException) {
            null
        }

    companion object {
        val TICK = 250.milliseconds
        private val QUALITY_LOOK = 1.seconds

        /** How long « Qualité réduite : réseau faible » stays. */
        val NOTICE = 5.seconds
        val RECONNECTING = 3.seconds
        val CHAPTER_GRACE = 3.seconds

        /** The steps of a search, each held for [SCRUB_SPEED_UP] repeats of the key. */
        val SCRUB_STEPS = listOf(10.seconds, 30.seconds, 1.minutes, 2.minutes, 5.minutes)
        const val SCRUB_SPEED_UP = 6
        private const val MILLIS = 1_000
    }
}
