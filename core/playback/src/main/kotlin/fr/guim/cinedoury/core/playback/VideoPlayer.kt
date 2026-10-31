package fr.guim.cinedoury.core.playback

import androidx.media3.common.Player
import fr.guim.cinedoury.core.model.MarginStatus
import fr.guim.cinedoury.core.model.PlaybackFailure
import fr.guim.cinedoury.core.model.PlaybackStream
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * The player of a screen, behind which ExoPlayer stays (`SPEC.md` § 4.11): it reads a
 * [PlaybackStream], plays, jumps, changes tracks within the stream, and says where it is. The
 * screens and their tests only see this.
 */
interface VideoPlayer {
    /** The Media3 player, for the surface of the screen; null in tests. */
    val player: Player?

    /** Failures and the end of the stream. */
    val events: Flow<PlayerEvent>

    /**
     * Reads [stream] from [start], with its audio and subtitle tracks chosen (by stream index; null
     * for none), and plays.
     */
    fun load(
        stream: PlaybackStream,
        start: Duration,
        audioIndex: Int?,
        subtitleIndex: Int?,
    )

    fun play()

    fun pause()

    fun seekTo(position: Duration)

    /**
     * Stops reading the stream, where it stands: no more sound nor image, nothing more asked of the
     * server, the position kept. A [load] starts again.
     */
    fun stop() = pause()

    /** Chooses an audio track within the stream; false when the stream does not carry it. */
    fun selectAudio(index: Int): Boolean

    /** Chooses a subtitle track within the stream (null: none); false when the stream does not carry it. */
    fun selectSubtitles(index: Int?): Boolean

    /** Where playback is, what it reads, and how well (for the information screen). */
    fun snapshot(): PlayerSnapshot

    fun release()
}

sealed interface PlayerEvent {
    /** The stream cannot be played: [failure] says what to do next, [detail] goes to « Plus d'infos ». */
    data class Failed(
        val failure: PlaybackFailure,
        val detail: String,
    ) : PlayerEvent

    data object Ended : PlayerEvent
}

/** What the player reads now (`FONCTIONNALITES.md` § 5, « Écran d'informations technique »). */
data class PlayerSnapshot(
    val position: Duration = Duration.ZERO,
    val duration: Duration = Duration.ZERO,
    val buffered: Duration = Duration.ZERO,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val video: VideoOutput? = null,
    val audio: AudioOutput? = null,
    val droppedFrames: Int = 0,
    /** The bandwidth the player measured, in bits per second. */
    val bandwidth: Long? = null,
    /** The margin of the phone (M2, `SPEC.md` § 4.27); null on the TV. */
    val margin: MarginStatus? = null,
)

/**
 * @param codec the MIME type and codec string (« video/hevc · hvc1.2.4.L153 »).
 * @param range « HDR10 », « HLG », « Dolby Vision », « SDR », as the stream says it.
 */
data class VideoOutput(
    val codec: String,
    val width: Int,
    val height: Int,
    val frameRate: Float?,
    val range: String,
    val decoder: String?,
)

/** @param passthrough the audio goes to the amplifier as it is (no decoder, no PCM). */
data class AudioOutput(
    val codec: String,
    val channels: Int,
    val sampleRate: Int,
    val passthrough: Boolean,
    val decoder: String?,
)
