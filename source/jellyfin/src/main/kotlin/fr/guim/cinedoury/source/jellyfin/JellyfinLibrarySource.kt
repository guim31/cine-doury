package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.model.FilterOptions
import fr.guim.cinedoury.core.model.HomeExclusions
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.ItemQuery
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.MAX_IDS
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.NamedId
import fr.guim.cinedoury.core.model.Page
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.model.PlaybackPreferences
import fr.guim.cinedoury.core.model.SubtitleMode
import fr.guim.cinedoury.core.model.UserData
import fr.guim.cinedoury.core.source.LibrarySource
import fr.guim.cinedoury.core.source.LoggingManageSource
import fr.guim.cinedoury.core.source.ManageSource
import fr.guim.cinedoury.core.source.PlaybackSource
import fr.guim.cinedoury.core.source.Session
import fr.guim.cinedoury.core.source.SourceLog
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.filterApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.personApi
import org.jellyfin.sdk.api.client.extensions.searchApi
import org.jellyfin.sdk.api.client.extensions.showApi
import org.jellyfin.sdk.api.client.extensions.studioApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userDataApi
import org.jellyfin.sdk.api.client.extensions.userViewApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.SubtitlePlaybackMode
import java.time.Instant
import java.time.ZoneId

/**
 * [LibrarySource] over the Jellyfin API (`SPEC.md` § 4.9). Lists ask for the few fields the screens
 * show; [item] asks for everything.
 */
@Suppress("TooManyFunctions") // One function per call of LibrarySource.
internal class JellyfinLibrarySource(
    private val api: ApiClient,
    override val session: Session,
    log: SourceLog = SourceLog.NONE,
) : LibrarySource {
    private val userId =
        session.user.id.value
            .toJellyfinUuid()

    override val playback: PlaybackSource = JellyfinPlaybackSource(api, userId)

    // Every management call is logged (`SPEC.md` § 4.14).
    override val manage: ManageSource = LoggingManageSource(JellyfinManageSource(api, userId), log)

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

    override suspend fun itemIds(query: ItemQuery): List<ItemId> =
        jellyfinCall(what = "ids of ${query.library?.value}") {
            api.libraryApi
                .getItems(query.toRequest(userId, startIndex = 0, limit = null, fields = emptyList(), images = false))
                .content.items
                .map { ItemId(it.id.toJellyfinId()) }
        }

    override suspend fun seasons(series: ItemId): List<MediaItem> =
        jellyfinCall(what = "seasons of ${series.value}") {
            api.showApi
                .getSeasons(
                    seriesId = series.value.toJellyfinUuid(),
                    userId = userId,
                    fields = LIST_FIELDS,
                    enableImages = true,
                    imageTypeLimit = 1,
                    enableImageTypes = IMAGE_TYPES,
                    enableUserData = true,
                ).content.items
                .map { it.toMediaItem() }
        }

    override suspend fun episodes(series: ItemId): List<MediaItem> =
        jellyfinCall(what = "episodes of ${series.value}") {
            api.showApi
                .getEpisodes(
                    seriesId = series.value.toJellyfinUuid(),
                    userId = userId,
                    fields = LIST_FIELDS,
                    enableImages = true,
                    imageTypeLimit = 1,
                    enableImageTypes = IMAGE_TYPES,
                    enableUserData = true,
                ).content.items
                .map { it.toMediaItem() }
        }

    override suspend fun similar(
        item: ItemId,
        limit: Int,
    ): List<MediaItem> =
        jellyfinCall(what = "similar to ${item.value}") {
            api.libraryApi
                .getSimilarItems(
                    itemId = item.value.toJellyfinUuid(),
                    userId = userId,
                    limit = limit,
                    fields = LIST_FIELDS,
                ).content.items
                .map { it.toMediaItem() }
        }

    override suspend fun collectionsOf(item: ItemId): List<MediaItem> =
        jellyfinCall(what = "collections of ${item.value}") {
            api.libraryApi
                .getItemCollections(itemId = item.value.toJellyfinUuid(), userId = userId, fields = LIST_FIELDS)
                .content.items
                .map { it.toMediaItem() }
        }

    override suspend fun collectionItems(collection: ItemId): List<MediaItem> =
        jellyfinCall(what = "items of ${collection.value}") {
            api.libraryApi
                .getItems(
                    userId = userId,
                    parentId = collection.value.toJellyfinUuid(),
                    sortBy = listOf(ItemSortBy.PREMIERE_DATE, ItemSortBy.PRODUCTION_YEAR, ItemSortBy.SORT_NAME),
                    sortOrder = listOf(SortOrder.ASCENDING),
                    fields = LIST_FIELDS,
                    enableUserData = true,
                    enableImageTypes = IMAGE_TYPES,
                    imageTypeLimit = 1,
                    enableTotalRecordCount = false,
                ).content.items
                .map { it.toMediaItem() }
        }

    override suspend fun filmography(person: ItemId): List<MediaItem> =
        jellyfinCall(what = "filmography of ${person.value}") {
            api.libraryApi
                .getItems(
                    userId = userId,
                    personIds = listOf(person.value.toJellyfinUuid()),
                    recursive = true,
                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                    sortBy = listOf(ItemSortBy.PREMIERE_DATE, ItemSortBy.PRODUCTION_YEAR, ItemSortBy.SORT_NAME),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    fields = LIST_FIELDS,
                    enableUserData = true,
                    enableImageTypes = IMAGE_TYPES,
                    imageTypeLimit = 1,
                    enableTotalRecordCount = false,
                ).content.items
                .map { it.toMediaItem() }
        }

    override suspend fun localTrailers(item: ItemId): List<MediaItem> =
        jellyfinCall(what = "trailers of ${item.value}") {
            api.libraryApi
                .getLocalTrailers(itemId = item.value.toJellyfinUuid(), userId = userId)
                .content
                .map { it.toMediaItem() }
        }

    override suspend fun setPlayed(
        item: ItemId,
        played: Boolean,
    ): UserData =
        jellyfinCall(what = "item ${item.value}") {
            val id = item.value.toJellyfinUuid()
            val data =
                if (played) {
                    api.userDataApi.markPlayedItem(itemId = id, userId = userId)
                } else {
                    api.userDataApi.markUnplayedItem(itemId = id, userId = userId)
                }
            data.content.toModel()
        }

    override suspend fun setFavorite(
        item: ItemId,
        favorite: Boolean,
    ): UserData =
        jellyfinCall(what = "item ${item.value}") {
            val id = item.value.toJellyfinUuid()
            val data =
                if (favorite) {
                    api.userDataApi.markFavoriteItem(itemId = id, userId = userId)
                } else {
                    api.userDataApi.unmarkFavoriteItem(itemId = id, userId = userId)
                }
            data.content.toModel()
        }

    override suspend fun playbackPreferences(): PlaybackPreferences =
        jellyfinCall(what = "user configuration") {
            val configuration =
                api.userApi
                    .getCurrentUser()
                    .content.configuration ?: return@jellyfinCall PlaybackPreferences()
            PlaybackPreferences(
                audioLanguage = configuration.audioLanguagePreference?.takeIf { it.isNotBlank() },
                subtitleLanguage = configuration.subtitleLanguagePreference?.takeIf { it.isNotBlank() },
                subtitleMode =
                    when (configuration.subtitleMode) {
                        SubtitlePlaybackMode.DEFAULT -> SubtitleMode.Default
                        SubtitlePlaybackMode.ALWAYS -> SubtitleMode.Always
                        SubtitlePlaybackMode.ONLY_FORCED -> SubtitleMode.OnlyForced
                        SubtitlePlaybackMode.NONE -> SubtitleMode.None
                        SubtitlePlaybackMode.SMART -> SubtitleMode.Smart
                    },
                playDefaultAudio = configuration.playDefaultAudioTrack,
            )
        }

    override suspend fun setPlaybackPreferences(preferences: PlaybackPreferences) {
        jellyfinCall(what = "user configuration") {
            val configuration =
                checkNotNull(
                    api.userApi
                        .getCurrentUser()
                        .content.configuration,
                ) { "No user configuration" }
            api.userApi.updateUserConfiguration(
                userId,
                configuration.copy(
                    audioLanguagePreference = preferences.audioLanguage.orEmpty(),
                    subtitleLanguagePreference = preferences.subtitleLanguage.orEmpty(),
                    subtitleMode =
                        when (preferences.subtitleMode) {
                            SubtitleMode.Default -> SubtitlePlaybackMode.DEFAULT
                            SubtitleMode.Always -> SubtitlePlaybackMode.ALWAYS
                            SubtitleMode.OnlyForced -> SubtitlePlaybackMode.ONLY_FORCED
                            SubtitleMode.None -> SubtitlePlaybackMode.NONE
                            SubtitleMode.Smart -> SubtitlePlaybackMode.SMART
                        },
                    playDefaultAudioTrack = preferences.playDefaultAudio,
                ),
            )
        }
    }

    override suspend fun setLibraryOrder(order: List<LibraryId>) {
        jellyfinCall(what = "user configuration") {
            val configuration =
                checkNotNull(
                    api.userApi
                        .getCurrentUser()
                        .content.configuration,
                ) { "No user configuration" }
            val chosen = order.map { it.value.toJellyfinUuid() }
            api.userApi.updateUserConfiguration(
                userId,
                configuration.copy(orderedViews = chosen + configuration.orderedViews.filter { it !in chosen }),
            )
        }
    }

    override suspend fun sendDiagnostic(report: String): String =
        jellyfinCall(what = "diagnostic report") {
            api.systemApi
                .logFile(report)
                .content.fileName
        }

    override suspend fun technicalItems(
        library: LibraryId,
        changedSince: Instant?,
        page: PageRequest,
    ): Page<MediaItem> =
        jellyfinCall(what = "index of ${library.value}") {
            val result =
                api.libraryApi
                    .getItems(
                        userId = userId,
                        parentId = library.value.toJellyfinUuid(),
                        recursive = true,
                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                        // Within one sort title, the date the item was added: a stable order for paging.
                        sortBy = listOf(ItemSortBy.SORT_NAME, ItemSortBy.DATE_CREATED),
                        sortOrder = listOf(SortOrder.ASCENDING),
                        minDateLastSaved = changedSince?.atZone(ZoneId.systemDefault())?.toLocalDateTime(),
                        startIndex = page.startIndex,
                        limit = page.limit,
                        fields = listOf(ItemFields.MEDIA_SOURCES, ItemFields.SORT_NAME),
                        enableImages = false,
                        enableUserData = false,
                        enableTotalRecordCount = true,
                    ).content
            Page(result.items.map { it.toMediaItem() }, result.startIndex, result.totalRecordCount)
        }

    override suspend fun search(
        term: String,
        kinds: Set<ItemKind>,
        limit: Int,
    ): List<MediaItem> =
        jellyfinCall(what = "search") {
            // `/Search/Hints` ignores accents and finds the start of any word (`/Items?searchTerm=`
            // and `/Persons` do not: « ines » finds no « Inès »); the cards come from `/Items`.
            val hints =
                api.searchApi
                    .getSearchHints(
                        searchTerm = term,
                        limit = limit,
                        userId = userId,
                        includeItemTypes = kinds.mapNotNull { it.toBaseItemKind() },
                        includePeople = ItemKind.Person in kinds,
                        includeMedia = kinds.any { it != ItemKind.Person },
                        includeGenres = false,
                        includeStudios = false,
                        includeArtists = false,
                    ).content.searchHints
                    .map { it.id }
                    .distinct()
            if (hints.isEmpty()) return@jellyfinCall emptyList()
            val items =
                hints
                    .chunked(MAX_IDS)
                    .flatMap { ids ->
                        api.libraryApi
                            .getItems(
                                userId = userId,
                                ids = ids,
                                fields = LIST_FIELDS,
                                enableUserData = true,
                                enableImageTypes = IMAGE_TYPES,
                                imageTypeLimit = 1,
                                enableTotalRecordCount = false,
                            ).content.items
                    }.associateBy { it.id }
            hints.mapNotNull { items[it]?.toMediaItem() }
        }

    override suspend fun titles(
        library: LibraryId,
        changedSince: Instant?,
        page: PageRequest,
    ): Page<MediaItem> =
        jellyfinCall(what = "titles of ${library.value}") {
            val result =
                api.libraryApi
                    .getItems(
                        userId = userId,
                        parentId = library.value.toJellyfinUuid(),
                        recursive = true,
                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                        sortBy = listOf(ItemSortBy.SORT_NAME, ItemSortBy.DATE_CREATED),
                        sortOrder = listOf(SortOrder.ASCENDING),
                        // Films of a collection one by one (CLAUDE.md, « Pièges »).
                        collapseBoxSetItems = false,
                        minDateLastSaved = changedSince?.atZone(ZoneId.systemDefault())?.toLocalDateTime(),
                        startIndex = page.startIndex,
                        limit = page.limit,
                        fields = listOf(ItemFields.SORT_NAME, ItemFields.ORIGINAL_TITLE, ItemFields.DATE_CREATED),
                        enableImageTypes = listOf(ImageType.PRIMARY, ImageType.BACKDROP),
                        imageTypeLimit = 1,
                        enableUserData = false,
                        enableTotalRecordCount = true,
                    ).content
            Page(result.items.map { it.toMediaItem() }, result.startIndex, result.totalRecordCount)
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
