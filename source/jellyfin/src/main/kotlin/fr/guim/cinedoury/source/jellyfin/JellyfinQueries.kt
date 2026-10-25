package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.ItemQuery
import fr.guim.cinedoury.core.model.SortField
import fr.guim.cinedoury.core.model.WatchStatus
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest

/**
 * An [ItemQuery] as a `GET /Items` request (`SPEC.md` § 4.9). Every filter is applied by the
 * server. A query without [ItemQuery.kinds] lists the direct children of the library; with kinds,
 * the whole library is searched for them. A collection filter searches the collection instead of
 * the library.
 */
internal fun ItemQuery.toRequest(
    userId: UUID,
    startIndex: Int,
    limit: Int?,
    fields: List<ItemFields>,
    images: Boolean = true,
): GetItemsRequest {
    if (ids.isNotEmpty()) {
        return GetItemsRequest(
            userId = userId,
            ids = ids.map { it.value.toJellyfinUuid() },
            fields = fields,
            enableUserData = true,
            enableImages = images,
            enableImageTypes = IMAGE_TYPES,
            imageTypeLimit = 1,
            enableTotalRecordCount = false,
        )
    }
    val parent = filters.collection?.id ?: library?.value
    val types = kinds.mapNotNull { it.toBaseItemKind() }
    val resolution = filters.resolution
    return GetItemsRequest(
        userId = userId,
        parentId = parent?.toJellyfinUuid(),
        recursive = types.isNotEmpty() || filters.collection != null,
        includeItemTypes = types,
        startIndex = startIndex,
        limit = limit,
        sortBy = sortBy(),
        sortOrder = listOf(if (sort.ascending) SortOrder.ASCENDING else SortOrder.DESCENDING),
        // A server with collections groups their films by default (CLAUDE.md, « Pièges »).
        collapseBoxSetItems = groupCollections && filters.collection == null,
        isPlayed =
            when (filters.watch) {
                WatchStatus.Watched -> true
                WatchStatus.Unwatched -> false
                WatchStatus.InProgress, null -> null
            },
        filters = if (filters.watch == WatchStatus.InProgress) listOf(ItemFilter.IS_RESUMABLE) else null,
        genres = filters.genres.sorted(),
        years = filters.allYears.sorted(),
        minCommunityRating = filters.minRating?.toDouble(),
        minWidth = resolution?.minWidth,
        maxWidth = resolution?.maxWidth,
        audioLanguages = filters.audioLanguages.sorted(),
        subtitleLanguages = filters.subtitleLanguages.sorted(),
        officialRatings = filters.officialRatings.sorted(),
        studioIds = filters.studios.map { it.id.toJellyfinUuid() },
        personIds = filters.people.map { it.id.toJellyfinUuid() },
        tags = filters.tags.sorted(),
        isFavorite = if (filters.favorite) true else null,
        nameLessThan = titleBelow,
        maxPremiereDate = releasedBefore?.atTime(LAST_HOUR, 0),
        fields = fields,
        enableUserData = images,
        enableImages = images,
        enableImageTypes = if (images) IMAGE_TYPES else emptyList(),
        imageTypeLimit = 1,
        enableTotalRecordCount = true,
    )
}

private val IMAGE_TYPES = listOf(ImageType.PRIMARY, ImageType.BACKDROP, ImageType.LOGO, ImageType.THUMB)

private const val LAST_HOUR = 23

/** Ties are broken by title, so that a page boundary never splits them differently. */
private fun ItemQuery.sortBy(): List<ItemSortBy> {
    val main =
        when (sort.field) {
            SortField.Title -> ItemSortBy.SORT_NAME
            SortField.DateAdded -> ItemSortBy.DATE_CREATED
            SortField.ReleaseDate -> ItemSortBy.PREMIERE_DATE
            SortField.Rating -> ItemSortBy.COMMUNITY_RATING
            SortField.Runtime -> ItemSortBy.RUNTIME
            SortField.LastPlayed -> ItemSortBy.DATE_PLAYED
            SortField.PlayCount -> ItemSortBy.PLAY_COUNT
            SortField.Random -> ItemSortBy.RANDOM
        }
    return if (main == ItemSortBy.SORT_NAME ||
        main == ItemSortBy.RANDOM
    ) {
        listOf(main)
    } else {
        listOf(main, ItemSortBy.SORT_NAME)
    }
}

internal fun ItemKind.toBaseItemKind(): BaseItemKind? =
    when (this) {
        ItemKind.Movie -> BaseItemKind.MOVIE
        ItemKind.Series -> BaseItemKind.SERIES
        ItemKind.Season -> BaseItemKind.SEASON
        ItemKind.Episode -> BaseItemKind.EPISODE
        ItemKind.Collection -> BaseItemKind.BOX_SET
        ItemKind.Person -> BaseItemKind.PERSON
        ItemKind.Video -> BaseItemKind.VIDEO
        ItemKind.Folder, ItemKind.Other -> null
    }
