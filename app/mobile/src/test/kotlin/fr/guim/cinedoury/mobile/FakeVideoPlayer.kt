package fr.guim.cinedoury.mobile

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
    }

    override fun play() {
        playing = true
    }

    override fun pause() {
        playing = false
    }

    override fun seekTo(position: Duration) {
        this.position = position
    }

    override fun selectAudio(index: Int): Boolean = tracksInStream

    override fun selectSubtitles(index: Int?): Boolean = tracksInStream

    override fun snapshot() = PlayerSnapshot(position = position, duration = duration, playing = playing)

    override fun release() {
        released = true
    }
}

/** Gives the same [FakeVideoPlayer] to every playback. */
class FakeVideoPlayers : VideoPlayerFactory {
    val player = FakeVideoPlayer()

    override fun create(
        capabilities: fr.guim.cinedoury.core.model.DeviceCapabilities,
        settings: fr.guim.cinedoury.core.model.PlayerSettings,
    ): VideoPlayer = player
}
