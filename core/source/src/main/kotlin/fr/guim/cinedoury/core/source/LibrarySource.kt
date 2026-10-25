package fr.guim.cinedoury.core.source

import fr.guim.cinedoury.core.model.FilterOptions
import fr.guim.cinedoury.core.model.HomeExclusions
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.ItemQuery
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.Page
import fr.guim.cinedoury.core.model.PageRequest
import java.time.Instant

/** The libraries of a signed-in user. Every failure is a [SourceException]. */
interface LibrarySource {
    val session: Session

    /**
     * Every library of the user, hidden ones included, in the order the user chose on the server
     * (Jellyfin: `OrderedViews`).
     */
    suspend fun libraries(): List<Library>

    /** The libraries the user left out of the home, or out of « Ajouts récents ». */
    suspend fun homeExclusions(): HomeExclusions

    /**
     * The top-level items of a library, sorted by sort title. Films stay individual items even
     * when they belong to a collection.
     */
    suspend fun items(library: LibraryId, page: PageRequest = PageRequest()): Page<MediaItem> =
        items(ItemQuery(library), page)

    /** One page of the items that match [query], with the total count. */
    suspend fun items(query: ItemQuery, page: PageRequest): Page<MediaItem>

    /** How many items match [query]. */
    suspend fun count(query: ItemQuery): Int

    /** One item with everything the detail screen needs, media sources included. */
    suspend fun item(id: ItemId): MediaItem

    /** Films and episodes started and not finished, the last played first (« Reprendre »). */
    suspend fun resume(library: LibraryId?, limit: Int): List<MediaItem>

    /**
     * The next episode of each series in progress (« À suivre »), the series watched last first.
     * Episodes already started are in [resume] and not here. [since]: only the series watched
     * since then; null for all.
     */
    suspend fun nextUp(library: LibraryId?, since: Instant?, limit: Int): List<MediaItem>

    /** The latest additions to [library], newest first; new episodes are grouped by series. */
    suspend fun latest(library: LibraryId, limit: Int): List<MediaItem>

    /** The values the filters of [library] can take, for items of [kinds]. */
    suspend fun filterOptions(library: LibraryId, kinds: Set<ItemKind>): FilterOptions
}
