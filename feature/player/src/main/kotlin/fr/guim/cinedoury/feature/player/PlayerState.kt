package fr.guim.cinedoury.feature.player

import androidx.compose.runtime.Immutable
import fr.guim.cinedoury.core.model.Chapter
import fr.guim.cinedoury.core.model.DeviceCapabilities
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.MediaSegment
import fr.guim.cinedoury.core.model.PlayMethod
import fr.guim.cinedoury.core.model.PlaybackFailure
import fr.guim.cinedoury.core.model.PlaybackProfile
import fr.guim.cinedoury.core.model.PlaybackStream
import fr.guim.cinedoury.core.model.PlayerSettings
import fr.guim.cinedoury.core.model.TrickplayInfo
import fr.guim.cinedoury.core.playback.PlayerSnapshot
import fr.guim.cinedoury.core.source.SourceException
import kotlin.time.Duration

/** Why the player stopped on an error screen (`UI.md` § 10: two sentences and « Réessayer »). */
@Immutable
sealed interface PlayerError {
    /** The server could not be asked (PlaybackInfo), or it refused. */
    data class Source(
        val error: SourceException,
    ) : PlayerError

    /** The stream failed, and no fallback is left. */
    data class Playback(
        val failure: PlaybackFailure,
        val detail: String,
    ) : PlayerError
}

/**
 * What the player shows (`UI.md` § 8, « Lecteur »).
 *
 * @param stream how the server sends the item; null while it is asked.
 * @param fallback what failed when the file was played as it is, before the server transcoded it
 * (`SPEC.md` § 4.11); the information screen says it.
 * @param skip the segment « Passer l'intro » would skip, while it plays.
 * @param next the next episode, once known; [nextShown] during the credits, [nextCountdown] from 1
 * to 0 (null without autoplay).
 * @param scrub where a search would go, while the user moves through the film.
 * @param info what the player reads, while the information screen is open.
 * @param reconnecting the connection broke and the buffer ran out: the player tries again.
 */
@Immutable
data class PlayerState(
    val item: MediaItem,
    val trailer: Boolean = false,
    val loading: Boolean = true,
    val error: PlayerError? = null,
    val position: Duration = Duration.ZERO,
    val duration: Duration = item.runtime ?: Duration.ZERO,
    val buffered: Duration = Duration.ZERO,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val reconnecting: Boolean = false,
    val stream: PlaybackStream? = null,
    val fallback: String? = null,
    val audioIndex: Int? = null,
    val subtitleIndex: Int? = null,
    val maxBitrate: Int? = null,
    val segments: List<MediaSegment> = emptyList(),
    val skip: MediaSegment? = null,
    val next: MediaItem? = null,
    val nextShown: Boolean = false,
    val nextCountdown: Float? = null,
    val nextSeconds: Int? = null,
    val stillWatching: Boolean = false,
    val scrub: Duration? = null,
    val info: PlayerSnapshot? = null,
    val settings: PlayerSettings = PlayerSettings(),
    val capabilities: DeviceCapabilities? = null,
    val profile: PlaybackProfile? = null,
) {
    val chapters: List<Chapter> get() = item.chapters

    val method: PlayMethod? get() = stream?.method

    /** The preview pictures of the version played: the smallest at least 320 px wide, else the largest. */
    val trickplay: TrickplayInfo?
        get() {
            val source = stream?.source?.id ?: item.mediaSources.firstOrNull()?.id
            val versions =
                item.mediaSources
                    .firstOrNull { it.id == source }
                    ?.trickplay
                    .orEmpty()
            return versions.firstOrNull { it.width >= TRICKPLAY_WIDTH } ?: versions.lastOrNull()
        }

    /** The chapter at the position. */
    val chapterIndex: Int get() = chapters.indexOfLast { it.start <= position }

    private companion object {
        const val TRICKPLAY_WIDTH = 320
    }
}

/** What the screen of the player asks its view model; a no-op for the screenshots. */
@Suppress("TooManyFunctions") // One function per gesture of the player.
interface PlayerActions {
    /** Any key: « Vous êtes toujours là ? » counts episodes without one. */
    fun interacted()

    fun togglePlay()

    fun play()

    fun pause()

    /** The jumps of ← and → (10 s back, 30 s ahead, `FONCTIONNALITES.md` § 5). */
    fun jump(forward: Boolean)

    fun seekTo(position: Duration)

    /** Moves the search one step; [repeat] counts the steps of a key held down (it speeds up). */
    fun scrub(
        forward: Boolean,
        repeat: Int,
    )

    fun commitScrub()

    fun cancelScrub()

    fun chapter(forward: Boolean)

    fun skipSegment()

    fun playNext()

    fun dismissNext()

    fun keepWatching()

    fun selectAudio(index: Int)

    fun selectSubtitles(index: Int?)

    /** null: the best the device takes. */
    fun selectQuality(maxBitrate: Int?)

    fun updateSettings(change: (PlayerSettings) -> PlayerSettings)

    fun toggleInfo()

    fun retry()

    /** The address of tile [tile] of the preview pictures. */
    fun trickplayUrl(tile: Int): String?

    /** The headers the server asks for with them. */
    val streamHeaders: Map<String, String>
}
