package fr.guim.cinedoury.core.data.search

import fr.guim.cinedoury.core.data.db.SearchDao
import fr.guim.cinedoury.core.data.db.SearchStateEntity
import fr.guim.cinedoury.core.data.db.SearchTitleEntity
import fr.guim.cinedoury.core.data.index.LibraryIndex
import fr.guim.cinedoury.core.model.ImageType
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemImages
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.source.LibrarySource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant

/**
 * The local title index (`SPEC.md` § 4.12): the films and series of the libraries with their titles,
 * for the search as you type. Filled in the background with the technical index, the same way: a
 * library read whole the first time and every [LibraryIndex.FULL_EVERY], otherwise only what the
 * server saved since the last update, read whole again when the counts differ (an item deleted).
 */
class TitleIndex(
    private val dao: SearchDao,
    private val clock: Clock = Clock.systemUTC(),
    private val pageSize: Int = LibraryIndex.PAGE_SIZE,
) {
    private val mutex = Mutex()

    suspend fun sync(
        serverId: String,
        source: LibrarySource,
        libraries: List<Library>,
        whole: Boolean = false,
    ) = mutex.withLock {
        val indexed = libraries.filter { it.kind in LibraryIndex.INDEXED_KINDS }
        val keep = indexed.map { it.id.value }
        dao.dropLibrariesExcept(serverId, keep)
        dao.dropStatesExcept(serverId, keep)
        indexed.forEach { syncLibrary(serverId, source, it.id, whole) }
    }

    private suspend fun syncLibrary(
        serverId: String,
        source: LibrarySource,
        library: LibraryId,
        whole: Boolean,
    ) {
        val started = clock.millis()
        val state = dao.state(serverId, library.value)
        val full = whole || state == null || started - state.fullSyncAt > LibraryIndex.FULL_EVERY.inWholeMilliseconds
        val since = if (full) null else Instant.ofEpochMilli(state.syncedAt - LibraryIndex.MARGIN.inWholeMilliseconds)
        read(serverId, source, library, since, started)
        if (full) {
            dao.dropUnseen(serverId, library.value, started)
        } else if (total(source, library) != dao.count(serverId, library.value)) {
            read(serverId, source, library, changedSince = null, started)
            dao.dropUnseen(serverId, library.value, started)
        }
        val fullAt = if (full) clock.millis() else state.fullSyncAt
        dao.saveState(SearchStateEntity(serverId, library.value, syncedAt = started, fullSyncAt = fullAt))
    }

    private suspend fun total(
        source: LibrarySource,
        library: LibraryId,
    ): Int = source.titles(library, changedSince = null, PageRequest(limit = 1)).totalCount

    private suspend fun read(
        serverId: String,
        source: LibrarySource,
        library: LibraryId,
        changedSince: Instant?,
        seenAt: Long,
    ) {
        var start: Int? = 0
        while (start != null) {
            val page = source.titles(library, changedSince, PageRequest(start, pageSize))
            dao.upsert(page.items.filter { it.kind in KINDS }.map { it.toTitleEntity(serverId, library, seenAt) })
            start = page.nextStartIndex
        }
    }

    suspend fun clear(serverId: String) = dao.clearServer(serverId)

    private companion object {
        val KINDS = setOf(ItemKind.Movie, ItemKind.Series)
    }
}

internal fun MediaItem.toTitleEntity(
    serverId: String,
    library: LibraryId,
    seenAt: Long,
) = SearchTitleEntity(
    serverId = serverId,
    itemId = id.value,
    libraryId = library.value,
    kind = kind.name,
    title = title,
    originalTitle = originalTitle?.takeIf { it.isNotBlank() && it != title },
    sortTitle = sortTitle?.takeIf { it.isNotBlank() },
    year = year,
    dateAdded = dateAdded?.toEpochMilli(),
    primary = images.primary,
    backdrop = images.backdrops.firstOrNull(),
    indexedAt = seenAt,
)

/** The card of an indexed title, until the server gives the whole item. */
internal fun SearchTitleEntity.toItem() =
    MediaItem(
        id = ItemId(itemId),
        kind = ItemKind.entries.firstOrNull { it.name == kind } ?: ItemKind.Movie,
        title = title,
        originalTitle = originalTitle,
        sortTitle = sortTitle,
        year = year,
        dateAdded = dateAdded?.let(Instant::ofEpochMilli),
        images =
            ItemImages(
                primary = primary?.takeIf { it.type == ImageType.Primary },
                backdrops = listOfNotNull(backdrop),
            ),
    )
