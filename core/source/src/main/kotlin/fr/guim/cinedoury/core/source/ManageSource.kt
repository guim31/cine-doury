package fr.guim.cinedoury.core.source

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

/**
 * Management of the server from the app (`SPEC.md` § 4.12, `FONCTIONNALITES.md` § 9): refresh,
 * identify, change the images, delete, scan a library, find the items that need a hand. Every
 * action but [rights] is for an administrator; the server refuses it otherwise
 * ([SourceException.Forbidden]). Every failure is a [SourceException].
 */
@Suppress("TooManyFunctions") // One function per action of « Gestion ».
interface ManageSource {
    /** What the profile may manage (`/Users/Me`, `Policy`). */
    suspend fun rights(): ManageRights

    /** Reads the metadata of [item] again, as much as [mode] says; the server does it in the background. */
    suspend fun refresh(
        item: ItemId,
        mode: MetadataRefresh,
    )

    /** The providers the server can search for an item of [kind] (« TheMovieDb », « AniDB »), the main one first. */
    suspend fun identifyProviders(
        item: ItemId,
        kind: ItemKind,
    ): List<String>

    /** The titles that may be [item], by the providers of the server. */
    suspend fun searchIdentity(
        item: ItemId,
        search: IdentifySearch,
    ): List<IdentifyMatch>

    /** [item] is [match]: its ids are set and its metadata read again, its images too if [replaceImages]. */
    suspend fun applyIdentity(
        item: ItemId,
        match: IdentifyMatch,
        replaceImages: Boolean,
    )

    /** The pictures of [type] the providers offer for [item]; [provider] null for all of them. */
    suspend fun remoteImages(
        item: ItemId,
        type: ImageType,
        provider: String? = null,
        allLanguages: Boolean = false,
    ): RemoteImages

    /** The server downloads the picture at [url] and makes it the [type] of [item]. */
    suspend fun setImage(
        item: ItemId,
        type: ImageType,
        url: String,
    )

    /** What deleting [item] would remove: its files, their size, its episodes. */
    suspend fun deletionPreview(item: MediaItem): DeletionPreview

    /** Deletes [item] from the library **and its files** from the disks of the server. */
    suspend fun delete(item: ItemId)

    /** Starts a scan: [LibraryScan.NewFiles] scans every library, [LibraryScan.RefreshAll] reads [library] again. */
    suspend fun scan(
        library: LibraryId,
        mode: LibraryScan,
    )

    /** Where the scan of the libraries stands (the scheduled task of the server). */
    suspend fun scanStatus(): ScanStatus

    /** The films and series of [library] without any provider id, or without poster. */
    suspend fun problems(library: LibraryId): LibraryProblems

    /**
     * The films and episodes of [library] whose file the server cannot read any more (moved or
     * deleted outside it, before the next scan). Reads one byte of each: [onProgress] tells how many
     * were checked, out of how many.
     */
    suspend fun missingFiles(
        library: LibraryId,
        onProgress: (checked: Int, total: Int) -> Unit = { _, _ -> },
    ): List<MediaItem>
}

/** Where a source writes what it did (logcat in the app, the console in tests). Never a secret. */
fun interface SourceLog {
    fun log(message: String)

    companion object {
        val NONE = SourceLog { }
    }
}

/**
 * [delegate], each call written to [log] with its outcome (`SPEC.md` § 4.12): « manage delete
 * item=… ok », « manage refresh item=… failed: Forbidden ». Ids and modes only: no token, no path.
 */
@Suppress("TooManyFunctions") // ManageSource, one call at a time.
class LoggingManageSource(
    private val delegate: ManageSource,
    private val log: SourceLog,
) : ManageSource {
    private suspend fun <T> logged(
        what: String,
        block: suspend () -> T,
    ): T =
        try {
            block().also { log.log("manage $what ok") }
        } catch (error: SourceException) {
            log.log("manage $what failed: ${error::class.simpleName}")
            throw error
        }

    override suspend fun rights(): ManageRights = logged("rights") { delegate.rights() }

    override suspend fun refresh(
        item: ItemId,
        mode: MetadataRefresh,
    ) = logged("refresh item=${item.value} mode=$mode") { delegate.refresh(item, mode) }

    override suspend fun identifyProviders(
        item: ItemId,
        kind: ItemKind,
    ): List<String> = logged("identify-providers item=${item.value}") { delegate.identifyProviders(item, kind) }

    override suspend fun searchIdentity(
        item: ItemId,
        search: IdentifySearch,
    ): List<IdentifyMatch> =
        logged("identify-search item=${item.value} provider=${search.provider}") {
            delegate.searchIdentity(item, search)
        }

    override suspend fun applyIdentity(
        item: ItemId,
        match: IdentifyMatch,
        replaceImages: Boolean,
    ) = logged("identify-apply item=${item.value} ids=${match.providerIds} images=$replaceImages") {
        delegate.applyIdentity(item, match, replaceImages)
    }

    override suspend fun remoteImages(
        item: ItemId,
        type: ImageType,
        provider: String?,
        allLanguages: Boolean,
    ): RemoteImages =
        logged("images item=${item.value} type=$type provider=$provider all=$allLanguages") {
            delegate.remoteImages(item, type, provider, allLanguages)
        }

    override suspend fun setImage(
        item: ItemId,
        type: ImageType,
        url: String,
    ) = logged("image-set item=${item.value} type=$type") { delegate.setImage(item, type, url) }

    override suspend fun deletionPreview(item: MediaItem): DeletionPreview =
        logged("delete-preview item=${item.id.value}") { delegate.deletionPreview(item) }

    override suspend fun delete(item: ItemId) = logged("delete item=${item.value}") { delegate.delete(item) }

    override suspend fun scan(
        library: LibraryId,
        mode: LibraryScan,
    ) = logged("scan library=${library.value} mode=$mode") { delegate.scan(library, mode) }

    // Polled every second during a scan: not worth a line each time.
    override suspend fun scanStatus(): ScanStatus = delegate.scanStatus()

    override suspend fun problems(library: LibraryId): LibraryProblems =
        logged("problems library=${library.value}") { delegate.problems(library) }

    override suspend fun missingFiles(
        library: LibraryId,
        onProgress: (checked: Int, total: Int) -> Unit,
    ): List<MediaItem> = logged("missing-files library=${library.value}") { delegate.missingFiles(library, onProgress) }
}
