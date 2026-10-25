package fr.guim.cinedoury.core.model

import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration

@JvmInline
value class ItemId(val value: String)

enum class ItemKind {
    Movie,
    Series,
    Season,
    Episode,
    Collection,
    Person,
    Video,
    Folder,
    Other,
}

/** A film, a series, a season, an episode, a collection or a person, as the screens see it. */
data class MediaItem(
    val id: ItemId,
    val kind: ItemKind,
    val title: String,
    val sortTitle: String? = null,
    val originalTitle: String? = null,
    val year: Int? = null,
    val premiereDate: LocalDate? = null,
    /** When the item entered the library. */
    val dateAdded: Instant? = null,
    val overview: String? = null,
    /** One line that sells the title (a TMDb tagline), when the server has one. */
    val tagline: String? = null,
    val runtime: Duration? = null,
    val officialRating: String? = null,
    val communityRating: Float? = null,
    val genres: List<String> = emptyList(),
    val images: ItemImages = ItemImages(),
    val userData: UserData? = null,
    val episode: EpisodeInfo? = null,
    val childCount: Int? = null,
    val mediaSources: List<MediaSource> = emptyList(),
) {
    /** Playback progress between 0 and 1, or null when not started. */
    val progress: Float?
        get() {
            val position = userData?.playbackPosition ?: return null
            val total = runtime ?: return null
            if (position <= Duration.ZERO || total <= Duration.ZERO) return null
            return (position / total).toFloat().coerceIn(0f, 1f)
        }

    /** Not watched yet: a film or an episode never finished. */
    val isUnwatched: Boolean
        get() = userData?.played == false && kind in PLAYABLE

    private companion object {
        val PLAYABLE = setOf(ItemKind.Movie, ItemKind.Episode, ItemKind.Video)
    }

    /** Episodes left to watch in a series. */
    val unwatchedEpisodes: Int? get() = userData?.unplayedCount?.takeIf { kind == ItemKind.Series }
}

/** Where an episode or a season sits in its series. */
data class EpisodeInfo(
    val seriesId: ItemId?,
    val seriesTitle: String?,
    val seasonId: ItemId?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
)

/** What the current user did with an item. */
data class UserData(
    val played: Boolean,
    val playbackPosition: Duration,
    val playCount: Int,
    val isFavorite: Boolean,
    val unplayedCount: Int? = null,
    val lastPlayed: Instant? = null,
)
