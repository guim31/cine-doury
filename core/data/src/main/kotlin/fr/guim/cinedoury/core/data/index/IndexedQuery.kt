package fr.guim.cinedoury.core.data.index

import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemQuery
import fr.guim.cinedoury.core.model.MAX_IDS
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.Page
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.source.LibrarySource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [query] read page by page, its filters of the local index applied (`SPEC.md` § 4.10). Without them,
 * the server does everything. With them:
 * 1. the server gives the ids of the items that match its own filters, in the order asked
 *    ([LibrarySource.itemIds], one light request);
 * 2. the index keeps those that match its filters;
 * 3. each page is read by id, [MAX_IDS] at a time, and put back in that order.
 * The server's sort, filters and rights stay in force; the films of a collection are not grouped.
 */
class IndexedQuery(
    private val source: LibrarySource,
    private val index: LibraryIndex,
    private val serverId: String,
    private val query: ItemQuery,
) {
    private val mutex = Mutex()
    private var ordered: List<ItemId>? = null
    private val serverQuery get() = query.copy(filters = query.filters.forServer, groupCollections = false)

    val usesIndex: Boolean get() = query.filters.usesIndex && query.library != null

    suspend fun page(request: PageRequest): Page<MediaItem> {
        if (!usesIndex) return source.items(query, request)
        val all = ids()
        val slice = all.drop(request.startIndex).take(request.limit)
        val found =
            slice
                .chunked(MAX_IDS)
                .flatMap { ids -> source.items(ItemQuery(ids = ids), PageRequest(limit = ids.size)).items }
                .associateBy { it.id }
        return Page(slice.mapNotNull { found[it] }, request.startIndex, all.size)
    }

    /** How many items come before the sort title [title] (the A–Z jump). */
    suspend fun countBelow(title: String): Int {
        if (!usesIndex) return source.count(query.copy(titleBelow = title))
        val matching = matching()
        return source.itemIds(serverQuery.copy(titleBelow = title)).count { it in matching }
    }

    private suspend fun ids(): List<ItemId> =
        mutex.withLock {
            ordered ?: run {
                val matching = matching()
                source.itemIds(serverQuery).filter { it in matching }.also { ordered = it }
            }
        }

    private suspend fun matching(): Set<ItemId> =
        index.matching(serverId, checkNotNull(query.library), query.filters).orEmpty()
}
