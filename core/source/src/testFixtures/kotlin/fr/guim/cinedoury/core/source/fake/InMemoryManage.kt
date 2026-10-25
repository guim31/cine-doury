package fr.guim.cinedoury.core.source.fake

import fr.guim.cinedoury.core.model.DeletionPreview
import fr.guim.cinedoury.core.model.IdentifyMatch
import fr.guim.cinedoury.core.model.IdentifySearch
import fr.guim.cinedoury.core.model.ImageRef
import fr.guim.cinedoury.core.model.ImageType
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
import fr.guim.cinedoury.core.model.User
import fr.guim.cinedoury.core.source.ManageSource
import fr.guim.cinedoury.core.source.SourceException

/**
 * What [InMemorySource] does with the management calls, shared by every session: what was asked,
 * and what the server answers. An item deleted or changed here is deleted or changed for the
 * libraries of the source too.
 *
 * @param rightsOf what each user may manage: the administrator everything, the others nothing.
 */
class InMemoryManage(
    var rightsOf: (User) -> ManageRights = { user ->
        if (user.isAdministrator) ManageRights(administrator = true, deleteEverywhere = true) else ManageRights.NONE
    },
) {
    /** What the providers answer to a search, whatever the title. */
    var matches: List<IdentifyMatch> = emptyList()

    /** The providers the server has. */
    var providers: List<String> = listOf("TheMovieDb")

    /** The pictures the providers offer, by type. */
    var images: Map<ImageType, List<RemoteImage>> = emptyMap()

    /** What [ManageSource.scanStatus] says. */
    var scanStatus: ScanStatus = ScanStatus.IDLE

    /** The items whose file is gone. */
    val missing: MutableSet<ItemId> = mutableSetOf()

    /** Each call, in order: « refresh 12 FillMissing », « delete 12 »… */
    val log: MutableList<String> = mutableListOf()

    /** The items deleted. */
    val deleted: MutableSet<ItemId> = mutableSetOf()

    /** The items as changed by an identification or a new picture. */
    internal val changed: MutableMap<ItemId, MediaItem> = mutableMapOf()

    /** Fails every management call with this error, for the tests of the error messages. */
    var failure: SourceException? = null

    private var nextTag = 1

    internal inner class Session(
        private val user: User,
        private val find: (ItemId) -> MediaItem?,
        private val pool: (LibraryId) -> List<MediaItem>,
        private val episodesOf: (ItemId) -> List<MediaItem>,
        private val checkSession: () -> Unit,
    ) : ManageSource {
        private fun call(line: String) {
            checkSession()
            failure?.let { throw it }
            log += line
        }

        private fun admin(): ManageRights {
            val rights = rightsOf(user)
            if (!rights.canManage) throw SourceException.Forbidden()
            return rights
        }

        private fun existing(item: ItemId): MediaItem =
            find(item) ?: throw SourceException.NotFound("item ${item.value}")

        override suspend fun rights(): ManageRights {
            checkSession()
            return rightsOf(user)
        }

        override suspend fun refresh(
            item: ItemId,
            mode: MetadataRefresh,
        ) {
            call("refresh ${item.value} $mode")
            admin()
            existing(item)
        }

        override suspend fun identifyProviders(
            item: ItemId,
            kind: ItemKind,
        ): List<String> {
            call("providers ${item.value}")
            admin()
            return providers
        }

        override suspend fun searchIdentity(
            item: ItemId,
            search: IdentifySearch,
        ): List<IdentifyMatch> {
            call("search ${item.value} ${search.title} ${search.year} ${search.provider}")
            admin()
            return matches
        }

        override suspend fun applyIdentity(
            item: ItemId,
            match: IdentifyMatch,
            replaceImages: Boolean,
        ) {
            call("apply ${item.value} ${match.providerIds} $replaceImages")
            admin()
            val current = existing(item)
            changed[item] = current.copy(title = match.title, year = match.year, providerIds = match.providerIds)
        }

        override suspend fun remoteImages(
            item: ItemId,
            type: ImageType,
            provider: String?,
            allLanguages: Boolean,
        ): RemoteImages {
            call("images ${item.value} $type $provider $allLanguages")
            admin()
            val all = images[type].orEmpty()
            val shown =
                all
                    .filter { provider == null || it.provider == provider }
                    .filter { allLanguages || it.language == null || it.language == "fr" }
            return RemoteImages(shown, all.mapNotNull { it.provider }.distinct())
        }

        override suspend fun setImage(
            item: ItemId,
            type: ImageType,
            url: String,
        ) {
            call("set-image ${item.value} $type $url")
            admin()
            val current = existing(item)
            val image = ImageRef(item, type, "new-${nextTag++}")
            val images =
                when (type) {
                    ImageType.Primary -> current.images.copy(primary = image)
                    ImageType.Backdrop -> current.images.copy(backdrops = listOf(image))
                    ImageType.Logo -> current.images.copy(logo = image)
                    ImageType.Thumb -> current.images.copy(thumb = image)
                }
            changed[item] = current.copy(images = images)
        }

        override suspend fun deletionPreview(item: MediaItem): DeletionPreview {
            call("preview ${item.id.value}")
            admin()
            val current = existing(item.id)
            return when (current.kind) {
                ItemKind.Series, ItemKind.Season -> {
                    val series = if (current.kind == ItemKind.Series) current.id else current.episode?.seriesId
                    val episodes =
                        series
                            ?.let(episodesOf)
                            .orEmpty()
                            .filter { current.kind == ItemKind.Series || it.episode?.seasonId == current.id }
                    DeletionPreview(
                        item = current,
                        paths = listOfNotNull(current.path),
                        sizeBytes = episodes.flatMap { it.mediaSources }.mapNotNull { it.sizeBytes }.sum(),
                        episodes = episodes.map { it.id },
                    )
                }

                else -> {
                    DeletionPreview(
                        item = current,
                        paths = current.mediaSources.mapNotNull { it.path }.ifEmpty { listOfNotNull(current.path) },
                        sizeBytes =
                            current.mediaSources
                                .mapNotNull { it.sizeBytes }
                                .takeIf { it.isNotEmpty() }
                                ?.sum(),
                    )
                }
            }
        }

        override suspend fun delete(item: ItemId) {
            call("delete ${item.value}")
            val rights = admin()
            val current = existing(item)
            if (!rights.canDelete(current.copy(canDelete = true))) throw SourceException.Forbidden()
            deleted += item
            if (current.kind == ItemKind.Series) deleted += episodesOf(item).map { it.id }
        }

        override suspend fun scan(
            library: LibraryId,
            mode: LibraryScan,
        ) {
            call("scan ${library.value} $mode")
            admin()
        }

        override suspend fun scanStatus(): ScanStatus {
            checkSession()
            return scanStatus
        }

        override suspend fun problems(library: LibraryId): LibraryProblems {
            call("problems ${library.value}")
            admin()
            val items = pool(library).filter { it.kind == ItemKind.Movie || it.kind == ItemKind.Series }
            return LibraryProblems(
                unidentified = items.filter { it.providerIds.isEmpty() },
                withoutPoster = items.filter { it.images.primary == null },
            )
        }

        override suspend fun missingFiles(
            library: LibraryId,
            onProgress: (checked: Int, total: Int) -> Unit,
        ): List<MediaItem> {
            call("missing ${library.value}")
            admin()
            val items =
                pool(library).flatMap { if (it.kind == ItemKind.Series) episodesOf(it.id) else listOf(it) }
            onProgress(items.size, items.size)
            return items.filter { it.id in missing }
        }
    }
}
