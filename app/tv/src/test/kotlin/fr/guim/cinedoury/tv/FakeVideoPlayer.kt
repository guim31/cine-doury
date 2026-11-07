package fr.guim.cinedoury.tv

import androidx.media3.common.Player
import fr.guim.cinedoury.core.model.PlaybackStream
import fr.guim.cinedoury.core.playback.PlayerEvent
import fr.guim.cinedoury.core.playback.PlayerSnapshot
import fr.guim.cinedoury.core.playback.VideoPlayer
import fr.guim.cinedoury.core.playback.VideoPlayerFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.time.Duration

/** A player without ExoPlayer, for the tests of the app: it records what it is asked to play. */
class FakeVideoPlayer : VideoPlayer {
    data class Load(
        val stream: PlaybackStream,
        val start: Duration,
        val audioIndex: Int?,
        val subtitleIndex: Int?,
    )

    val loads = mutableListOf<Load>()
    var position: Duration = Duration.ZERO
    var duration: Duration = Duration.ZERO
    var playing = false
    var released = false

    /** The player waits for its stream, the loads that succeeded, and those failing since (N, Q). */
    var buffering = false
    var loadsCompleted = 0
    var failingLoads = 0
    var playWhenReady = true
    var lastLoadError: String? = null

    /** How many times the player was stopped (an error shown). */
    var stops = 0

    /** Whether the tracks asked for are in the stream (direct play), or need another stream. */
    var tracksInStream = true

    override val player: Player? = null
    override val events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 8)

    override fun load(
        stream: PlaybackStream,
        start: Duration,
        audioIndex: Int?,
        subtitleIndex: Int?,
    ) {
        loads += Load(stream, start, audioIndex, subtitleIndex)
        position = start
        playing = true
        playWhenReady = true
    }

    override fun play() {
        playing = true
        playWhenReady = true
    }

    override fun pause() {
        playing = false
        playWhenReady = false
    }

    override fun stop() {
        stops++
        playing = false
    }

    override fun seekTo(position: Duration) {
        this.position = position
    }

    override fun selectAudio(index: Int): Boolean = tracksInStream

    override fun selectSubtitles(index: Int?): Boolean = tracksInStream

    override fun snapshot() =
        PlayerSnapshot(
            position = position,
            duration = duration,
            playing = playing,
            buffering = buffering,
            playWhenReady = playWhenReady,
            loadsCompleted = loadsCompleted,
            failingLoads = failingLoads,
            lastLoadError = lastLoadError,
        )

    override fun release() {
        released = true
    }
}

/** Gives the same [FakeVideoPlayer] to every playback, or a new one each time with [fresh]; keeps them all. */
class FakeVideoPlayers(
    private val fresh: Boolean = false,
) : VideoPlayerFactory {
    val player = FakeVideoPlayer()

    /** Every player made, in order. */
    val made = mutableListOf<FakeVideoPlayer>()

    /** A player made and not released would be heard: its sound with no screen is the bug of Y. */
    val sounding: List<FakeVideoPlayer> get() = made.distinct().filter { it.playing && !it.released }

    override fun create(
        capabilities: fr.guim.cinedoury.core.model.DeviceCapabilities,
        settings: fr.guim.cinedoury.core.model.PlayerSettings,
    ): VideoPlayer = (if (fresh && made.isNotEmpty()) FakeVideoPlayer() else player).also { made += it }
}
