package fr.guim.cinedoury.core.model

import java.time.LocalDate

/**
 * What the profile may manage on the server (`SPEC.md` § 4.12): every management action is for an
 * administrator; deleting also needs the right to delete, everywhere or in some libraries (Jellyfin:
 * `Policy.IsAdministrator`, `EnableContentDeletion`, `EnableContentDeletionFromFolders`).
 *
 * @param deleteFrom the libraries where the profile may delete, when not everywhere.
 */
data class ManageRights(
    val administrator: Boolean,
    val deleteEverywhere: Boolean = false,
    val deleteFrom: Set<LibraryId> = emptySet(),
) {
    /** Refresh, identify, change the images, scan: the entries of « Gestion ». */
    val canManage: Boolean get() = administrator

    /** Whether « Supprimer » may show at all. */
    val canDeleteSomewhere: Boolean get() = administrator && (deleteEverywhere || deleteFrom.isNotEmpty())

    /**
     * Whether [item] may be deleted: the right of the profile, in its [library] when known, and the
     * server's own answer for the item ([MediaItem.canDelete]: a file it can reach).
     */
    fun canDelete(
        item: MediaItem,
        library: LibraryId? = null,
    ): Boolean {
        if (!administrator || !item.canDelete) return false
        return deleteEverywhere || (if (library != null) library in deleteFrom else deleteFrom.isNotEmpty())
    }

    companion object {
        /** A profile that manages nothing: what the screens assume until the server has answered. */
        val NONE = ManageRights(administrator = false)
    }
}

/** How much a refresh of the metadata replaces (`FONCTIONNALITES.md` § 9). */
enum class MetadataRefresh {
    /** « Compléter ce qui manque »: what the item lacks, images included; nothing is replaced. */
    FillMissing,

    /** « Tout remplacer » the metadata; the images stay. */
    ReplaceAll,

    /** « Tout remplacer, images comprises ». */
    ReplaceAllAndImages,
}

/** How deep a scan of a library goes. */
enum class LibraryScan {
    /** « Rechercher les nouveaux fichiers »: the files added, changed or gone; quick. */
    NewFiles,

    /** « Tout actualiser »: the metadata of every item, read again. */
    RefreshAll,
}

/** Where a running scan stands: [progress] from 0 to 1, null when the server does not say. */
data class ScanStatus(
    val running: Boolean,
    val progress: Float? = null,
) {
    companion object {
        val IDLE = ScanStatus(running = false)
    }
}

/**
 * A search for the right identity of an item (« Corriger l'identification »): [title] and [year]
 * as the user typed them, at [provider] (null: every provider of the server).
 */
data class IdentifySearch(
    val kind: ItemKind,
    val title: String,
    val year: Int? = null,
    val provider: String? = null,
)

/**
 * One answer of a provider: what the gallery shows, and what applying it needs ([providerIds], the
 * ids of the title at the providers, such as `Tmdb` → `603`).
 */
data class IdentifyMatch(
    val title: String,
    val year: Int?,
    val overview: String?,
    val imageUrl: String?,
    val provider: String?,
    val providerIds: Map<String, String>,
    val premiereDate: LocalDate? = null,
)

/** A picture a provider offers for an item (« Changer l'affiche, le fond ou le logo »). */
data class RemoteImage(
    val url: String,
    val thumbnailUrl: String?,
    val type: ImageType,
    val width: Int?,
    val height: Int?,
    /** ISO 639-1 code (« fr »), null for a picture without text. */
    val language: String?,
    val provider: String?,
    val communityRating: Double? = null,
    val voteCount: Int? = null,
)

/** The pictures of one type, and every provider that offers some (the filter of the gallery). */
data class RemoteImages(
    val images: List<RemoteImage>,
    val providers: List<String>,
)

/**
 * What a deletion removes, shown before it (`UI.md` § 8): the files, their total size, and the
 * episodes of a season or a series ([episodes], forgotten by the local index with the item).
 */
data class DeletionPreview(
    val item: MediaItem,
    val paths: List<String>,
    val sizeBytes: Long?,
    val episodes: List<ItemId>? = null,
) {
    val episodeCount: Int? get() = episodes?.size

    /** A whole series goes only after a second confirmation. */
    val needsSecondConfirmation: Boolean get() = item.kind == ItemKind.Series
}

/** The items of a library that need a hand (`FONCTIONNALITES.md` § 9, « Éléments à problème »). */
data class LibraryProblems(
    /** No id at any provider: the server did not recognise the title (a folder name misspelt). */
    val unidentified: List<MediaItem>,
    /** No poster. */
    val withoutPoster: List<MediaItem>,
)

/** The external ids shown in « Informations sur le fichier ». */
object ProviderKeys {
    const val TMDB = "Tmdb"
    const val IMDB = "Imdb"
    const val TVDB = "Tvdb"
    const val ANIDB = "AniDB"

    /** In the order the file information lists them. */
    val SHOWN = listOf(TMDB, IMDB, TVDB, ANIDB)
}
