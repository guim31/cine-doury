package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.model.DeletionPreview
import fr.guim.cinedoury.core.model.IdentifyMatch
import fr.guim.cinedoury.core.model.IdentifySearch
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.LibraryProblems
import fr.guim.cinedoury.core.model.LibraryScan
import fr.guim.cinedoury.core.model.ManageRights
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.MetadataRefresh
import fr.guim.cinedoury.core.model.RemoteImage
import fr.guim.cinedoury.core.model.RemoteImages
import fr.guim.cinedoury.core.model.ScanStatus
import fr.guim.cinedoury.core.source.ManageSource
import fr.guim.cinedoury.core.source.SourceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemLookupApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.remoteImageApi
import org.jellyfin.sdk.api.client.extensions.scheduledTaskApi
import org.jellyfin.sdk.api.client.extensions.showApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BoxSetInfo
import org.jellyfin.sdk.model.api.BoxSetInfoRemoteSearchQuery
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MetadataRefreshMode
import org.jellyfin.sdk.model.api.MovieInfo
import org.jellyfin.sdk.model.api.MovieInfoRemoteSearchQuery
import org.jellyfin.sdk.model.api.RemoteImageInfo
import org.jellyfin.sdk.model.api.RemoteSearchResult
import org.jellyfin.sdk.model.api.SeriesInfo
import org.jellyfin.sdk.model.api.SeriesInfoRemoteSearchQuery
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.TaskState
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import fr.guim.cinedoury.core.model.ImageType as ModelImageType

/**
 * [ManageSource] over the Jellyfin API (`SPEC.md` § 4.14). The server checks the rights of every
 * call; [rights] only lets the screens hide what the profile may not do.
 */
@Suppress("TooManyFunctions") // One function per call of ManageSource.
internal class JellyfinManageSource(
    private val api: ApiClient,
    private val userId: UUID,
) : ManageSource {
    // Read at each call: the address follows the network of the phone (LibrarySource.moveTo, M1).
    private val base get() = api.baseUrl.orEmpty().trimEnd('/')

    private val authorization =
        AuthorizationHeaderBuilder.buildHeader(
            clientName = api.clientInfo.name,
            clientVersion = api.clientInfo.version,
            deviceId = api.deviceInfo.id,
            deviceName = api.deviceInfo.name,
            accessToken = api.accessToken,
        )

    override suspend fun rights(): ManageRights =
        jellyfinCall(what = "user policy") {
            val policy =
                api.userApi
                    .getCurrentUser()
                    .content.policy
            ManageRights(
                administrator = policy?.isAdministrator == true,
                deleteEverywhere = policy?.enableContentDeletion == true,
                // Library ids, written with or without dashes by the server.
                deleteFrom =
                    policy
                        ?.enableContentDeletionFromFolders
                        .orEmpty()
                        .map { LibraryId(it.replace("-", "").lowercase()) }
                        .toSet(),
            )
        }

    override suspend fun refresh(
        item: ItemId,
        mode: MetadataRefresh,
    ) {
        jellyfinCall(what = "item ${item.value}") {
            // « Compléter ce qui manque » is the full refresh that replaces nothing (Jellyfin web:
            // « Rechercher les métadonnées manquantes »).
            api.libraryApi.refreshItem(
                itemId = item.value.toJellyfinUuid(),
                metadataRefreshMode = MetadataRefreshMode.FULL_REFRESH,
                imageRefreshMode = MetadataRefreshMode.FULL_REFRESH,
                replaceAllMetadata = mode != MetadataRefresh.FillMissing,
                replaceAllImages = mode == MetadataRefresh.ReplaceAllAndImages,
            )
        }
    }

    override suspend fun identifyProviders(
        item: ItemId,
        kind: ItemKind,
    ): List<String> =
        jellyfinCall(what = "item ${item.value}") {
            // The external ids the server knows for the item tell which providers it has installed.
            val known =
                api.itemLookupApi
                    .getExternalIdInfos(item.value.toJellyfinUuid())
                    .content
                    .map { it.name }
                    .toSet()
            SEARCH_PROVIDERS.filter { it in known }.ifEmpty { listOf(TMDB_PROVIDER) }
        }

    override suspend fun searchIdentity(
        item: ItemId,
        search: IdentifySearch,
    ): List<IdentifyMatch> =
        jellyfinCall(what = "item ${item.value}") {
            val id = item.value.toJellyfinUuid()
            val title = search.title.trim()
            // Providers turned off in the library still answer: the user asked for them.
            val results =
                when (search.kind) {
                    ItemKind.Movie -> {
                        api.itemLookupApi
                            .getMovieRemoteSearchResults(
                                MovieInfoRemoteSearchQuery(
                                    searchInfo = MovieInfo(name = title, year = search.year, isAutomated = false),
                                    itemId = id,
                                    searchProviderName = search.provider,
                                    includeDisabledProviders = true,
                                ),
                            ).content
                    }

                    ItemKind.Series -> {
                        api.itemLookupApi
                            .getSeriesRemoteSearchResults(
                                SeriesInfoRemoteSearchQuery(
                                    searchInfo = SeriesInfo(name = title, year = search.year, isAutomated = false),
                                    itemId = id,
                                    searchProviderName = search.provider,
                                    includeDisabledProviders = true,
                                ),
                            ).content
                    }

                    ItemKind.Collection -> {
                        api.itemLookupApi
                            .getBoxSetRemoteSearchResults(
                                BoxSetInfoRemoteSearchQuery(
                                    searchInfo = BoxSetInfo(name = title, year = search.year, isAutomated = false),
                                    itemId = id,
                                    searchProviderName = search.provider,
                                    includeDisabledProviders = true,
                                ),
                            ).content
                    }

                    else -> {
                        emptyList()
                    }
                }
            results.map { it.toMatch() }
        }

    override suspend fun applyIdentity(
        item: ItemId,
        match: IdentifyMatch,
        replaceImages: Boolean,
    ) {
        jellyfinCall(what = "item ${item.value}") {
            api.itemLookupApi.applySearchCriteria(
                itemId = item.value.toJellyfinUuid(),
                replaceAllImages = replaceImages,
                data =
                    RemoteSearchResult(
                        name = match.title,
                        providerIds = match.providerIds,
                        productionYear = match.year,
                        imageUrl = match.imageUrl,
                        searchProviderName = match.provider,
                        overview = match.overview,
                        artists = emptyList(),
                    ),
            )
        }
    }

    override suspend fun remoteImages(
        item: ItemId,
        type: ModelImageType,
        provider: String?,
        allLanguages: Boolean,
    ): RemoteImages =
        jellyfinCall(what = "item ${item.value}") {
            val result =
                api.remoteImageApi
                    .getRemoteImages(
                        itemId = item.value.toJellyfinUuid(),
                        type = type.toJellyfin(),
                        providerName = provider,
                        includeAllLanguages = allLanguages,
                    ).content
            RemoteImages(
                images = result.images.orEmpty().mapNotNull { it.toModel(type) },
                providers = result.providers.orEmpty(),
            )
        }

    override suspend fun setImage(
        item: ItemId,
        type: ModelImageType,
        url: String,
    ) {
        jellyfinCall(what = "item ${item.value}") {
            api.remoteImageApi.downloadRemoteImage(item.value.toJellyfinUuid(), type.toJellyfin(), url)
        }
    }

    override suspend fun deletionPreview(item: MediaItem): DeletionPreview =
        jellyfinCall(what = "item ${item.id.value}") {
            val id = item.id.value.toJellyfinUuid()
            val full =
                api.libraryApi
                    .getItem(itemId = id, userId = userId)
                    .content
                    .toMediaItem()
            when (full.kind) {
                ItemKind.Series, ItemKind.Season -> {
                    val series = (if (full.kind == ItemKind.Series) full.id else full.episode?.seriesId) ?: full.id
                    val episodes =
                        api.showApi
                            .getEpisodes(
                                seriesId = series.value.toJellyfinUuid(),
                                userId = userId,
                                seasonId = id.takeIf { full.kind == ItemKind.Season },
                                fields = listOf(ItemFields.MEDIA_SOURCES),
                                enableImages = false,
                                enableUserData = false,
                            ).content.items
                    val sizes = episodes.flatMap { it.mediaSources.orEmpty() }.mapNotNull { it.size }
                    DeletionPreview(
                        item = full,
                        paths = listOfNotNull(full.path),
                        sizeBytes = sizes.takeIf { it.isNotEmpty() }?.sum(),
                        episodes = episodes.map { ItemId(it.id.toJellyfinId()) },
                    )
                }

                else -> {
                    val paths = full.mediaSources.mapNotNull { it.path }.ifEmpty { listOfNotNull(full.path) }
                    val sizes = full.mediaSources.mapNotNull { it.sizeBytes }
                    DeletionPreview(
                        item = full,
                        paths = paths.distinct(),
                        sizeBytes = sizes.takeIf { it.isNotEmpty() }?.sum(),
                    )
                }
            }
        }

    override suspend fun delete(item: ItemId) {
        jellyfinCall(what = "item ${item.value}") {
            api.libraryApi.deleteItem(item.value.toJellyfinUuid())
        }
    }

    override suspend fun scan(
        library: LibraryId,
        mode: LibraryScan,
    ) {
        jellyfinCall(what = "library ${library.value}") {
            when (mode) {
                // The scheduled task « Scan Media Library », whose progress the server publishes; it
                // scans every library, but only reads what changed.
                LibraryScan.NewFiles -> {
                    api.libraryApi.refreshLibrary()
                }

                LibraryScan.RefreshAll -> {
                    api.libraryApi.refreshItem(
                        itemId = library.value.toJellyfinUuid(),
                        metadataRefreshMode = MetadataRefreshMode.FULL_REFRESH,
                        imageRefreshMode = MetadataRefreshMode.FULL_REFRESH,
                        replaceAllMetadata = true,
                        replaceAllImages = false,
                    )
                }
            }
        }
    }

    override suspend fun scanStatus(): ScanStatus =
        jellyfinCall(what = "scheduled tasks") {
            val task =
                api.scheduledTaskApi
                    .getTasks(isHidden = false)
                    .content
                    .firstOrNull { it.key == SCAN_TASK } ?: return@jellyfinCall ScanStatus.IDLE
            if (task.state == TaskState.IDLE) {
                ScanStatus.IDLE
            } else {
                ScanStatus(running = true, progress = task.currentProgressPercentage?.let { (it / PERCENT).toFloat() })
            }
        }

    override suspend fun problems(library: LibraryId): LibraryProblems =
        jellyfinCall(what = "library ${library.value}") {
            val items =
                api.libraryApi
                    .getItems(
                        userId = userId,
                        parentId = library.value.toJellyfinUuid(),
                        recursive = true,
                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                        collapseBoxSetItems = false,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                        sortOrder = listOf(SortOrder.ASCENDING),
                        fields = listOf(ItemFields.PROVIDER_IDS, ItemFields.PATH, ItemFields.SORT_NAME),
                        enableImageTypes = listOf(ImageType.PRIMARY),
                        imageTypeLimit = 1,
                        enableUserData = false,
                        enableTotalRecordCount = false,
                    ).content.items
                    .map { it.toMediaItem() }
            LibraryProblems(
                unidentified = items.filter { it.providerIds.isEmpty() },
                withoutPoster = items.filter { it.images.primary == null },
            )
        }

    override suspend fun missingFiles(
        library: LibraryId,
        onProgress: (checked: Int, total: Int) -> Unit,
    ): List<MediaItem> {
        val items =
            jellyfinCall(what = "library ${library.value}") {
                api.libraryApi
                    .getItems(
                        userId = userId,
                        parentId = library.value.toJellyfinUuid(),
                        recursive = true,
                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                        sortOrder = listOf(SortOrder.ASCENDING),
                        fields = listOf(ItemFields.PATH, ItemFields.SORT_NAME),
                        enableImageTypes = listOf(ImageType.PRIMARY, ImageType.THUMB),
                        imageTypeLimit = 1,
                        enableUserData = false,
                        enableTotalRecordCount = false,
                    ).content.items
                    .map { it.toMediaItem() }
            }
        onProgress(0, items.size)
        val permits = Semaphore(PARALLEL_CHECKS)
        var checked = 0
        val lock = Any()
        return coroutineScope {
            items
                .map { item ->
                    async {
                        val missing = permits.withPermit { !fileExists(item.id) }
                        val count = synchronized(lock) { ++checked }
                        onProgress(count, items.size)
                        item.takeIf { missing }
                    }
                }.awaitAll()
                .filterNotNull()
        }
    }

    /**
     * Whether the server can read the file of [item]: its first byte (`GET /Items/{id}/File`, `Range:
     * bytes=0-0`). Jellyfin answers 404 once the file is gone, until the next scan forgets the item;
     * `HEAD` is refused (405).
     */
    private suspend fun fileExists(item: ItemId): Boolean =
        withContext(Dispatchers.IO) {
            val connection = URI("$base/Items/${item.value}/File").toURL().openConnection() as HttpURLConnection
            try {
                connection.setRequestProperty("Authorization", authorization)
                connection.setRequestProperty("Range", "bytes=0-0")
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                when (val status = connection.responseCode) {
                    HttpURLConnection.HTTP_NOT_FOUND -> false
                    HttpURLConnection.HTTP_UNAUTHORIZED -> throw SourceException.Unauthorized()
                    HttpURLConnection.HTTP_FORBIDDEN -> throw SourceException.Forbidden()
                    in SERVER_ERRORS -> throw SourceException.Server(status)
                    else -> true
                }
            } catch (error: IOException) {
                throw SourceException.Unreachable(error)
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val TMDB_PROVIDER = "TheMovieDb"

        /** The providers that can identify a title, in the order they are offered. */
        val SEARCH_PROVIDERS = listOf(TMDB_PROVIDER, "AniDB", "TheTVDB")

        /** Key of the scheduled task « Scan Media Library ». */
        const val SCAN_TASK = "RefreshLibrary"
        const val PERCENT = 100.0
        const val PARALLEL_CHECKS = 8
        const val TIMEOUT_MILLIS = 15_000
        val SERVER_ERRORS = 500..599
    }
}

private fun RemoteSearchResult.toMatch() =
    IdentifyMatch(
        title = name.orEmpty(),
        year = productionYear ?: premiereDate?.year,
        overview = overview?.takeIf { it.isNotBlank() },
        imageUrl = imageUrl?.takeIf { it.isNotBlank() },
        provider = searchProviderName,
        providerIds = providerIds.filterValues { it.isNotBlank() },
        premiereDate = premiereDate?.toLocalDate(),
    )

private fun RemoteImageInfo.toModel(type: ModelImageType): RemoteImage? {
    val address = url?.takeIf { it.isNotBlank() } ?: return null
    return RemoteImage(
        url = address,
        thumbnailUrl = thumbnailUrl?.takeIf { it.isNotBlank() },
        type = type,
        width = width,
        height = height,
        language = language?.takeIf { it.isNotBlank() },
        provider = providerName,
        communityRating = communityRating,
        voteCount = voteCount,
    )
}

private fun ModelImageType.toJellyfin(): ImageType =
    when (this) {
        ModelImageType.Primary -> ImageType.PRIMARY
        ModelImageType.Backdrop -> ImageType.BACKDROP
        ModelImageType.Logo -> ImageType.LOGO
        ModelImageType.Thumb -> ImageType.THUMB
    }
