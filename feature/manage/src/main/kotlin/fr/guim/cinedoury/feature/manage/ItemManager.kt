package fr.guim.cinedoury.feature.manage

import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.manage.ChangeKind
import fr.guim.cinedoury.core.data.manage.ManageRepository
import fr.guim.cinedoury.core.model.DeletionPreview
import fr.guim.cinedoury.core.model.IdentifyMatch
import fr.guim.cinedoury.core.model.IdentifySearch
import fr.guim.cinedoury.core.model.ImageType
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.LibraryProblems
import fr.guim.cinedoury.core.model.LibraryScan
import fr.guim.cinedoury.core.model.ManageRights
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.MetadataRefresh
import fr.guim.cinedoury.core.model.RemoteImages
import fr.guim.cinedoury.core.model.ScanStatus
import fr.guim.cinedoury.core.source.ManageSource
import fr.guim.cinedoury.core.source.SourceException

/**
 * Every management action of the screens (`SPEC.md` § 4.12), through the source of the open
 * profile, and what the app keeps of the item afterwards: a deleted item leaves the cache of the
 * home and the local index, a changed one is read again by whoever shows it.
 */
@Suppress("TooManyFunctions") // One function per action of « Gestion ».
class ItemManager(
    private val sessions: SessionManager,
    private val repository: ManageRepository,
) {
    private var known: Pair<String, ManageRights>? = null

    private val source: ManageSource
        get() =
            sessions.active.value
                ?.library
                ?.manage ?: throw SourceException.Unauthorized()

    private val profileKey: String?
        get() =
            sessions.active.value
                ?.profile
                ?.let { "${it.serverId}/${it.user.id.value}" }

    /**
     * What the profile may manage, as last read: an administrator of the cache sees the entries of
     * « Gestion » at once, « Supprimer » once the server has given the rights.
     */
    val cachedRights: ManageRights
        get() {
            val key = profileKey ?: return ManageRights.NONE
            known?.takeIf { it.first == key }?.let { return it.second }
            val administrator =
                sessions.active.value
                    ?.profile
                    ?.user
                    ?.isAdministrator == true
            return ManageRights(administrator = administrator)
        }

    /** The rights of the profile, read once per profile. */
    suspend fun rights(): ManageRights {
        val key = profileKey ?: return ManageRights.NONE
        known?.takeIf { it.first == key }?.let { return it.second }
        return source.rights().also { known = key to it }
    }

    suspend fun refresh(
        item: ItemId,
        mode: MetadataRefresh,
    ) {
        source.refresh(item, mode)
        repository.changed(item)
    }

    suspend fun providers(item: MediaItem): List<String> = source.identifyProviders(item.id, item.kind)

    suspend fun search(
        item: MediaItem,
        search: IdentifySearch,
    ): List<IdentifyMatch> = source.searchIdentity(item.id, search)

    suspend fun identify(
        item: ItemId,
        match: IdentifyMatch,
        replaceImages: Boolean,
    ) {
        source.applyIdentity(item, match, replaceImages)
        repository.changed(item, ChangeKind.Identified)
    }

    suspend fun images(
        item: ItemId,
        type: ImageType,
        provider: String?,
        allLanguages: Boolean,
    ): RemoteImages = source.remoteImages(item, type, provider, allLanguages)

    suspend fun setImage(
        item: ItemId,
        type: ImageType,
        url: String,
    ) {
        source.setImage(item, type, url)
        repository.changed(item, ChangeKind.Image)
    }

    suspend fun deletionPreview(item: MediaItem): DeletionPreview = source.deletionPreview(item)

    /** Deletes the item of [preview], files included; the app forgets it at once. */
    suspend fun delete(preview: DeletionPreview) {
        source.delete(preview.item.id)
        val serverId =
            sessions.active.value
                ?.profile
                ?.serverId ?: return
        repository.deleted(serverId, preview)
    }

    suspend fun scan(
        library: LibraryId,
        mode: LibraryScan,
    ) = source.scan(library, mode)

    suspend fun scanStatus(): ScanStatus = source.scanStatus()

    suspend fun problems(library: LibraryId): LibraryProblems = source.problems(library)

    suspend fun missingFiles(
        library: LibraryId,
        onProgress: (Int, Int) -> Unit,
    ): List<MediaItem> = source.missingFiles(library, onProgress)

    /** Marks [items] watched or not, one after the other; the libraries read them again. */
    suspend fun setPlayed(
        items: List<ItemId>,
        played: Boolean,
    ) {
        val library = sessions.active.value?.library ?: throw SourceException.Unauthorized()
        items.forEach { library.setPlayed(it, played) }
        items.forEach { repository.changed(it, ChangeKind.Watched) }
    }

    companion object {
        /** The kinds whose identity can be corrected: the providers search films, series and collections. */
        val IDENTIFIABLE = setOf(ItemKind.Movie, ItemKind.Series, ItemKind.Collection)
    }
}
