package fr.guim.cinedoury.core.data.library

import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.Page
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.source.SourceException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A long list read page by page (`SPEC.md` § 4.9). Its size is known from the first page, so that a
 * grid lays out every place at once: the places not read yet are skeletons, and nothing moves when a
 * page arrives.
 *
 * @param total null until the first page answers.
 * @param generation changes with each new query, so that a screen knows the list was replaced.
 */
data class PagedItems(
    val total: Int? = null,
    private val pages: Map<Int, List<MediaItem>> = emptyMap(),
    val pageSize: Int = PageRequest.DEFAULT_LIMIT,
    val error: SourceException? = null,
    val generation: Int = 0,
) {
    /** The item at [index], or null while its page is not read. */
    operator fun get(index: Int): MediaItem? = pages[index / pageSize]?.getOrNull(index % pageSize)

    fun indexOf(id: ItemId): Int? =
        pages.entries.firstNotNullOfOrNull { (page, items) ->
            items.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { page * pageSize + it }
        }

    val loadedCount: Int get() = pages.values.sumOf { it.size }

    /** The list with [id] changed by [change], where it is read (« vu » from a menu, essai réel 5). */
    internal fun changed(
        id: ItemId,
        change: (MediaItem) -> MediaItem,
    ): PagedItems =
        if (pages.values.none { items -> items.any { it.id == id } }) {
            this
        } else {
            copy(pages = pages.mapValues { (_, items) -> items.map { if (it.id == id) change(it) else it } })
        }

    internal fun with(page: Page<MediaItem>): PagedItems =
        copy(total = page.totalCount, pages = pages + (page.startIndex / pageSize to page.items), error = null)
}

/**
 * Reads [PagedItems] for a screen: [load] starts a new query, [request] asks for the page of an index
 * (and the neighbour the user is heading to). A page is read once; a failed page can be asked again.
 */
class ItemPager(
    scope: CoroutineScope,
    private val pageSize: Int = PageRequest.DEFAULT_LIMIT,
) {
    private val state = MutableStateFlow(PagedItems(pageSize = pageSize))
    val items: StateFlow<PagedItems> = state.asStateFlow()

    private var reader: (suspend (PageRequest) -> Page<MediaItem>)? = null
    private val requested = mutableSetOf<Int>()
    private val failed = mutableSetOf<Int>()

    // A child of the screen's scope: its pages end with it, and a new query cancels them all.
    private val pages = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))

    /** Replaces the list by the results of [read], starting with the page of [around]. */
    fun load(
        around: Int = 0,
        read: suspend (PageRequest) -> Page<MediaItem>,
    ) {
        pages.coroutineContext.cancelChildren()
        requested.clear()
        failed.clear()
        reader = read
        state.update { PagedItems(pageSize = pageSize, generation = it.generation + 1) }
        request(around)
    }

    /** Makes sure the item at [index] is read, and the page after it when the user nears its end. */
    fun request(index: Int) {
        val page = index.coerceAtLeast(0) / pageSize
        fetch(page)
        val offset = index % pageSize
        if (offset >= pageSize / 2) fetch(page + 1)
        if (offset < pageSize / PREVIOUS_SHARE && page > 0) fetch(page - 1)
    }

    /** Changes the item [id] where it is read, without reading anything again. */
    fun update(
        id: ItemId,
        change: (MediaItem) -> MediaItem,
    ) = state.update { it.changed(id, change) }

    /** Asks again for the pages that failed. */
    fun retry() {
        if (state.value.error == null) return
        state.update { it.copy(error = null) }
        val pages = failed.toList().ifEmpty { listOf(0) }
        failed.clear()
        pages.forEach(::fetch)
    }

    private fun fetch(page: Int) {
        val read = reader ?: return
        val total = state.value.total
        if (total != null && page * pageSize >= total) return
        if (!requested.add(page)) return
        val generation = state.value.generation
        pages.launch {
            try {
                val result = read(PageRequest(page * pageSize, pageSize))
                state.update { if (it.generation == generation) it.with(result) else it }
            } catch (error: SourceException) {
                requested.remove(page)
                failed.add(page)
                state.update { if (it.generation == generation) it.copy(error = error) else it }
            }
        }
    }

    private companion object {
        /** Near the first quarter of a page, the previous one is read too (the list is scrolled up). */
        const val PREVIOUS_SHARE = 4
    }
}
