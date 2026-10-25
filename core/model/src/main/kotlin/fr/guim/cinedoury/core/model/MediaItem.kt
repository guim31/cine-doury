package fr.guim.cinedoury.core.model

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
    val overview: String? = null,
    val runtime: Duration? = null,
    val officialRating: String? = null,
    val communityRating: Float? = null,
    val genres: List<String> = emptyList(),
    val images: ItemImages = ItemImages(),
    val userData: UserData? = null,
    val episode: EpisodeInfo? = null,
    val childCount: Int? = null,
    val mediaSources: List<MediaSource> = emptyList(),
)

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
)
