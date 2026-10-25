package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.Page
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.source.LibrarySource
import fr.guim.cinedoury.core.source.Session
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.userViewApi
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder

internal class JellyfinLibrarySource(
    private val api: ApiClient,
    override val session: Session,
) : LibrarySource {
    private val userId =
        session.user.id.value
            .toJellyfinUuid()

    override suspend fun libraries(): List<Library> =
        jellyfinCall(what = "libraries") {
            api.userViewApi
                .getUserViews(userId = userId)
                .content.items
                .mapNotNull { it.toLibrary() }
        }

    override suspend fun items(library: LibraryId, page: PageRequest): Page<MediaItem> =
        jellyfinCall(what = "library ${library.value}") {
            val result =
                api.libraryApi
                    .getItems(
                        userId = userId,
                        parentId = library.value.toJellyfinUuid(),
                        startIndex = page.startIndex,
                        limit = page.limit,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                        sortOrder = listOf(SortOrder.ASCENDING),
                        // A server with collections groups their films by default (CLAUDE.md, « Pièges »).
                        collapseBoxSetItems = false,
                        fields = LIST_FIELDS,
                        enableUserData = true,
                        enableImageTypes = LIST_IMAGE_TYPES,
                        imageTypeLimit = 1,
                        enableTotalRecordCount = true,
                    ).content
            Page(
                items = result.items.map { it.toMediaItem() },
                startIndex = result.startIndex,
                totalCount = result.totalRecordCount,
            )
        }

    override suspend fun item(id: ItemId): MediaItem =
        jellyfinCall(what = "item ${id.value}") {
            api.libraryApi
                .getItem(itemId = id.value.toJellyfinUuid(), userId = userId)
                .content
                .toMediaItem()
        }

    private companion object {
        val LIST_FIELDS =
            listOf(
                ItemFields.SORT_NAME,
                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                ItemFields.CHILD_COUNT,
            )
        val LIST_IMAGE_TYPES = listOf(ImageType.PRIMARY, ImageType.BACKDROP, ImageType.LOGO, ImageType.THUMB)
    }
}
