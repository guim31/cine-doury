package fr.guim.cinedoury.core.data.home

import fr.guim.cinedoury.core.data.db.CachedItemEntity
import fr.guim.cinedoury.core.data.db.CachedLibraryEntity
import fr.guim.cinedoury.core.data.db.Converters
import fr.guim.cinedoury.core.model.EpisodeInfo
import fr.guim.cinedoury.core.model.HomeExclusions
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemImages
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.LibraryKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.UserData
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** The user a cache belongs to. */
data class ProfileKey(
    val serverId: String,
    val userId: String,
)

internal fun MediaItem.toEntity(profile: ProfileKey): CachedItemEntity =
    CachedItemEntity(
        serverId = profile.serverId,
        userId = profile.userId,
        itemId = id.value,
        kind = kind.name,
        title = title,
        sortTitle = sortTitle,
        year = year,
        premiereDate = premiereDate?.toEpochDay(),
        dateAdded = dateAdded?.toEpochMilli(),
        overview = overview,
        tagline = tagline,
        runtimeMs = runtime?.inWholeMilliseconds,
        officialRating = officialRating,
        communityRating = communityRating,
        genres = genres.joinToString(Converters.SEPARATOR),
        primary = images.primary,
        backdrop = images.backdrops.firstOrNull(),
        logo = images.logo,
        thumb = images.thumb,
        seriesPrimary = images.seriesPrimary,
        parentBackdrop = images.parentBackdrop,
        parentLogo = images.parentLogo,
        parentThumb = images.parentThumb,
        played = userData?.played,
        positionMs = userData?.playbackPosition?.inWholeMilliseconds ?: 0,
        playCount = userData?.playCount ?: 0,
        favorite = userData?.isFavorite == true,
        unplayedCount = userData?.unplayedCount,
        lastPlayed = userData?.lastPlayed?.toEpochMilli(),
        seriesId = episode?.seriesId?.value,
        seriesTitle = episode?.seriesTitle,
        seasonId = episode?.seasonId?.value,
        seasonNumber = episode?.seasonNumber,
        episodeNumber = episode?.episodeNumber,
        childCount = childCount,
    )

internal fun CachedItemEntity.toModel(): MediaItem {
    val kind = ItemKind.entries.firstOrNull { it.name == kind } ?: ItemKind.Other
    return MediaItem(
        id = ItemId(itemId),
        kind = kind,
        title = title,
        sortTitle = sortTitle,
        year = year,
        premiereDate = premiereDate?.let(LocalDate::ofEpochDay),
        dateAdded = dateAdded?.let(Instant::ofEpochMilli),
        overview = overview,
        tagline = tagline,
        runtime = runtimeMs?.milliseconds,
        officialRating = officialRating,
        communityRating = communityRating,
        genres = genres.split(Converters.SEPARATOR).filter { it.isNotEmpty() },
        images =
            ItemImages(
                primary = primary,
                backdrops = listOfNotNull(backdrop),
                logo = logo,
                thumb = thumb,
                seriesPrimary = seriesPrimary,
                parentBackdrop = parentBackdrop,
                parentLogo = parentLogo,
                parentThumb = parentThumb,
            ),
        userData =
            played?.let {
                UserData(
                    played = it,
                    playbackPosition = if (positionMs > 0) positionMs.milliseconds else Duration.ZERO,
                    playCount = playCount,
                    isFavorite = favorite,
                    unplayedCount = unplayedCount,
                    lastPlayed = lastPlayed?.let(Instant::ofEpochMilli),
                )
            },
        episode =
            if (kind == ItemKind.Episode || kind == ItemKind.Season) {
                EpisodeInfo(
                    seriesId = seriesId?.let(::ItemId),
                    seriesTitle = seriesTitle,
                    seasonId = seasonId?.let(::ItemId),
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                )
            } else {
                null
            },
        childCount = childCount,
    )
}

/** The libraries of the user and their exclusions, as the server gave them last. */
data class UserLibraries(
    val libraries: List<Library> = emptyList(),
    val exclusions: HomeExclusions = HomeExclusions(),
)

internal fun UserLibraries.toEntities(profile: ProfileKey): List<CachedLibraryEntity> =
    libraries.mapIndexed { position, library ->
        CachedLibraryEntity(
            serverId = profile.serverId,
            userId = profile.userId,
            libraryId = library.id.value,
            position = position,
            name = library.name,
            kind = library.kind.name,
            image = library.image,
            excludedFromHome = library.id in exclusions.fromHome,
            excludedFromLatest = library.id in exclusions.fromLatest,
        )
    }

internal fun List<CachedLibraryEntity>.toUserLibraries(): UserLibraries =
    UserLibraries(
        libraries =
            map { entity ->
                Library(
                    id = LibraryId(entity.libraryId),
                    name = entity.name,
                    kind = LibraryKind.entries.firstOrNull { it.name == entity.kind } ?: LibraryKind.Other,
                    image = entity.image,
                )
            },
        exclusions =
            HomeExclusions(
                fromHome = filter { it.excludedFromHome }.map { LibraryId(it.libraryId) }.toSet(),
                fromLatest = filter { it.excludedFromLatest }.map { LibraryId(it.libraryId) }.toSet(),
            ),
    )
