package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.model.FilterOptions
import fr.guim.cinedoury.core.model.HomeExclusions
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.ItemQuery
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.NamedId
import fr.guim.cinedoury.core.model.Page
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.source.LibrarySource
import fr.guim.cinedoury.core.source.Session
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.filterApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.personApi
import org.jellyfin.sdk.api.client.extensions.showApi
import org.jellyfin.sdk.api.client.extensions.studioApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userViewApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.MediaType
import java.time.Instant
import java.time.ZoneId

/**
 * [LibrarySource] over the Jellyfin API (`SPEC.md` § 4.9). Lists ask for the few fields the screens
 * show; [item] asks for everything.
 */
internal class JellyfinLibrarySource(
    private val api: ApiClient,
    override val session: Session,
) : LibrarySource {
    private val userId =
        session.user.id.value
            .toJellyfinUuid()

    override suspend fun libraries(): List<Library> =
        jellyfinCall(what = "libraries") {
            // Hidden libraries (`MyMediaExcludes`) stay reachable from the rail; the server keeps
            // the order of `OrderedViews`.
            api.userViewApi
                .getUserViews(userId = userId, includeHidden = true)
                .content.items
                .mapNotNull { it.toLibrary() }
        }

    override suspend fun homeExclusions(): HomeExclusions =
        jellyfinCall(what = "user configuration") {
            val configuration =
                api.userApi
                    .getCurrentUser()
                    .content.configuration
            HomeExclusions(
                fromHome =
                    configuration
                        ?.myMediaExcludes
                        .orEmpty()
                        .map { LibraryId(it.toJellyfinId()) }
                        .toSet(),
                fromLatest =
                    configuration
                        ?.latestItemsExcludes
                        .orEmpty()
                        .map { LibraryId(it.toJellyfinId()) }
                        .toSet(),
            )
        }

    override suspend fun items(
        query: ItemQuery,
        page: PageRequest,
    ): Page<MediaItem> =
        jellyfinCall(what = "items of ${query.library?.value}") {
            val result =
                api.libraryApi
                    .getItems(query.toRequest(userId, page.startIndex, page.limit, LIST_FIELDS))
                    .content
            Page(
                items = result.items.map { it.toMediaItem() },
                startIndex = result.startIndex,
                totalCount = result.totalRecordCount,
            )
        }

    override suspend fun count(query: ItemQuery): Int =
        jellyfinCall(what = "items of ${query.library?.value}") {
            api.libraryApi
                .getItems(query.toRequest(userId, startIndex = 0, limit = 0, fields = emptyList()))
                .content.totalRecordCount
        }

    override suspend fun item(id: ItemId): MediaItem =
        jellyfinCall(what = "item ${id.value}") {
            api.libraryApi
                .getItem(itemId = id.value.toJellyfinUuid(), userId = userId)
                .content
                .toMediaItem()
        }

    override suspend fun resume(
        library: LibraryId?,
        limit: Int,
    ): List<MediaItem> =
        jellyfinCall(what = "resume") {
            api.libraryApi
                .getResumeItems(
                    userId = userId,
                    parentId = library?.value?.toJellyfinUuid(),
                    limit = limit,
                    // Without it, the server also lists the seasons and series in progress.
                    mediaTypes = listOf(MediaType.VIDEO),
                    fields = LIST_FIELDS,
                    enableUserData = true,
                    enableImageTypes = IMAGE_TYPES,
                    imageTypeLimit = 1,
                    enableTotalRecordCount = false,
                ).content.items
                .map { it.toMediaItem() }
        }

    override suspend fun nextUp(
        library: LibraryId?,
        since: Instant?,
        limit: Int,
    ): List<MediaItem> =
        jellyfinCall(what = "next up") {
            api.showApi
                .getNextUp(
                    userId = userId,
                    parentId = library?.value?.toJellyfinUuid(),
                    limit = limit,
                    fields = LIST_FIELDS,
                    enableUserData = true,
                    enableImageTypes = IMAGE_TYPES,
                    imageTypeLimit = 1,
                    nextUpDateCutoff = since?.atZone(ZoneId.systemDefault())?.toLocalDateTime(),
                    enableTotalRecordCount = false,
                    // Started episodes are in « Reprendre ».
                    enableResumable = false,
                    enableRewatching = false,
                ).content.items
                .map { it.toMediaItem() }
        }

    override suspend fun latest(
        library: LibraryId,
        limit: Int,
    ): List<MediaItem> =
        jellyfinCall(what = "latest of ${library.value}") {
            api.libraryApi
                .getLatestMedia(
                    userId = userId,
                    parentId = library.value.toJellyfinUuid(),
                    fields = LIST_FIELDS,
                    enableImages = true,
                    imageTypeLimit = 1,
                    enableImageTypes = IMAGE_TYPES,
                    enableUserData = true,
                    limit = limit,
                    groupItems = true,
                ).content
                .map { it.toMediaItem() }
        }

    override suspend fun filterOptions(
        library: LibraryId,
        kinds: Set<ItemKind>,
    ): FilterOptions =
        jellyfinCall(what = "filters of ${library.value}") {
            val parent = library.value.toJellyfinUuid()
            val types = kinds.mapNotNull { it.toBaseItemKind() }
            coroutineScope {
                val legacy = async { api.filterApi.getQueryFiltersLegacy(userId, parent, types).content }
                val languages = async { api.filterApi.getQueryFilters(userId, parent, types, recursive = true).content }
                val studios = async { studios(parent, types) }
                val people = async { people(parent) }
                val collections = async { collections(parent) }
                val values = legacy.await()
                FilterOptions(
                    genres = values.genres.orEmpty(),
                    years = values.years.orEmpty().sorted(),
                    officialRatings = values.officialRatings.orEmpty(),
                    tags = values.tags.orEmpty(),
                    audioLanguages =
                        languages
                            .await()
                            .audioLanguages
                            .orEmpty()
                            .mapNotNull { it.value },
                    subtitleLanguages =
                        languages
                            .await()
                            .subtitleLanguages
                            .orEmpty()
                            .mapNotNull { it.value },
                    studios = studios.await(),
                    people = people.await(),
                    collections = collections.await(),
                )
            }
        }

    private suspend fun studios(
        parent: UUID,
        types: List<BaseItemKind>,
    ): List<NamedId> =
        api.studioApi
            .getStudios(parentId = parent, includeItemTypes = types, userId = userId, enableImages = false)
            .content.items
            .map { it.toNamedId() }

    private suspend fun people(parent: UUID): List<NamedId> =
        api.personApi
            .getPersons(
                limit = PEOPLE_LIMIT,
                personTypes = PERSON_TYPES,
                parentId = parent,
                userId = userId,
                enableImages = false,
            ).content.items
            .map { it.toNamedId() }

    private suspend fun collections(parent: UUID): List<NamedId> =
        api.libraryApi
            .getItems(
                userId = userId,
                parentId = parent,
                recursive = true,
                includeItemTypes = listOf(BaseItemKind.BOX_SET),
                enableImages = false,
                enableTotalRecordCount = false,
            ).content.items
            .map { it.toNamedId() }
            .sortedBy { it.name.lowercase() }

    private fun BaseItemDto.toNamedId() = NamedId(id.toJellyfinId(), name.orEmpty())

    private companion object {
        /** Enough for the cards, the banner and the list view, without media streams. */
        val LIST_FIELDS =
            listOf(
                ItemFields.SORT_NAME,
                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                ItemFields.CHILD_COUNT,
                ItemFields.OVERVIEW,
                ItemFields.GENRES,
                ItemFields.DATE_CREATED,
                ItemFields.TAGLINES,
            )
        val IMAGE_TYPES = listOf(ImageType.PRIMARY, ImageType.BACKDROP, ImageType.LOGO, ImageType.THUMB)

        /** People offered as filters: the most frequent would need a server sort Jellyfin lacks. */
        const val PEOPLE_LIMIT = 200
        val PERSON_TYPES = listOf("Actor", "Director")
    }
}
