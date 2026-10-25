package fr.guim.cinedoury.core.data.index

import fr.guim.cinedoury.core.data.db.Converters
import fr.guim.cinedoury.core.data.db.IndexDao
import fr.guim.cinedoury.core.data.db.IndexStateEntity
import fr.guim.cinedoury.core.data.db.IndexedItemEntity
import fr.guim.cinedoury.core.model.AudioFeature
import fr.guim.cinedoury.core.model.DynamicRange
import fr.guim.cinedoury.core.model.ItemFilters
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.LibraryKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.model.TechnicalSummary
import fr.guim.cinedoury.core.model.VideoCodec
import fr.guim.cinedoury.core.source.LibrarySource
import fr.guim.cinedoury.core.source.SourceException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Where the index of a server stands, for the settings (`SPEC.md` § 4.10).
 *
 * @param read items read by the current update, out of [total] (null until the server said).
 * @param updatedAt when the last update ended; null before the first one.
 * @param error why the last update stopped; the index keeps what it had.
 */
data class IndexStatus(
    val running: Boolean = false,
    val read: Int = 0,
    val total: Int? = null,
    val films: Int = 0,
    val episodes: Int = 0,
    val updatedAt: Instant? = null,
    val error: SourceException? = null,
) {
    /** The index was filled at least once: its answers can be trusted. */
    val ready: Boolean get() = updatedAt != null
}

/**
 * The local index of the libraries (`SPEC.md` § 4.10): one row per film and per episode with its
 * technical facts, for the filters Jellyfin 12.3 lacks (dynamic range, audio format, codec) and the
 * badges of the details screens. It fills itself page by page in the background, then follows the
 * changes (`minDateLastSaved`); it never blocks a screen.
 */
class LibraryIndex(
    private val dao: IndexDao,
    private val clock: Clock = Clock.systemUTC(),
    private val pageSize: Int = PAGE_SIZE,
) {
    private val running = MutableStateFlow<Running?>(null)
    private val failures = MutableStateFlow<Map<String, SourceException>>(emptyMap())
    private val mutex = Mutex()

    /** The update in progress: items read out of the total announced by the server. */
    private data class Running(
        val serverId: String,
        val read: Int = 0,
        val total: Int = 0,
    )

    fun status(serverId: String): Flow<IndexStatus> =
        combine(
            dao.observeCounts(serverId),
            dao.observeStates(serverId),
            running,
            failures,
        ) { counts, states, current, failed ->
            val mine = current?.takeIf { it.serverId == serverId }
            IndexStatus(
                running = mine != null,
                read = mine?.read ?: 0,
                total = mine?.total?.takeIf { it > 0 },
                films = counts.firstOrNull { it.kind == ItemKind.Movie.name }?.count ?: 0,
                episodes = counts.firstOrNull { it.kind == ItemKind.Episode.name }?.count ?: 0,
                updatedAt = states.maxOfOrNull { it.syncedAt }?.let(Instant::ofEpochMilli),
                error = failed[serverId],
            )
        }.distinctUntilChanged()

    /**
     * Brings the index of [libraries] up to date: a library read for the first time, or not read
     * whole for [FULL_EVERY], is read whole (rows not seen any more are dropped); otherwise only
     * what the server saved since the last update, then the counts are compared, and a library whose
     * count differs (an item deleted) is read whole. [whole] reads everything again. A failure leaves
     * the index as it was, and is shown by [status].
     */
    suspend fun sync(
        serverId: String,
        source: LibrarySource,
        libraries: List<Library>,
        whole: Boolean = false,
    ) = mutex.withLock {
        val indexed = libraries.filter { it.kind in INDEXED_KINDS }
        running.value = Running(serverId)
        failures.update { it - serverId }
        try {
            val keep = indexed.map { it.id.value }
            dao.dropLibrariesExcept(serverId, keep)
            dao.dropStatesExcept(serverId, keep)
            indexed.forEach { library -> syncLibrary(serverId, source, library.id, whole) }
        } catch (error: SourceException) {
            failures.update { it + (serverId to error) }
            throw error
        } finally {
            running.value = null
        }
    }

    private suspend fun syncLibrary(
        serverId: String,
        source: LibrarySource,
        library: LibraryId,
        whole: Boolean,
    ) {
        val started = clock.millis()
        val state = dao.state(serverId, library.value)
        val full = whole || state == null || started - state.fullSyncAt > FULL_EVERY.inWholeMilliseconds
        val since = if (full) null else Instant.ofEpochMilli(state.syncedAt - MARGIN.inWholeMilliseconds)
        read(serverId, source, library, since, started)
        if (full) {
            dao.dropUnseen(serverId, library.value, started)
        } else {
            val onServer = source.technicalItems(library, changedSince = null, PageRequest(limit = 1)).totalCount
            if (onServer != dao.count(serverId, library.value)) {
                read(serverId, source, library, changedSince = null, started)
                dao.dropUnseen(serverId, library.value, started)
            }
        }
        val fullAt = if (full) clock.millis() else state.fullSyncAt
        dao.saveState(IndexStateEntity(serverId, library.value, syncedAt = started, fullSyncAt = fullAt))
    }

    private suspend fun read(
        serverId: String,
        source: LibrarySource,
        library: LibraryId,
        changedSince: Instant?,
        seenAt: Long,
    ) {
        var start: Int? = 0
        var counted = false
        while (start != null) {
            val page = source.technicalItems(library, changedSince, PageRequest(start, pageSize))
            if (!counted) {
                counted = true
                running.update { it?.copy(total = it.total + page.totalCount) }
            }
            dao.upsert(page.items.map { it.toIndexEntity(serverId, library, seenAt) })
            running.update { it?.copy(read = it.read + page.items.size) }
            start = page.nextStartIndex
        }
    }

    /**
     * The items of [library] that match the filters of the index in [filters]: films, or series
     * (one of whose episodes matches). Null when [filters] has none.
     */
    suspend fun matching(
        serverId: String,
        library: LibraryId,
        filters: ItemFilters,
    ): Set<ItemId>? {
        if (!filters.usesIndex) return null
        val audio = filters.audioFormats.flatMap { it.features }.toSet()
        return dao
            .matching(serverId, library.value, filters.ranges.mask(), audio.mask(), filters.videoCodecs.mask())
            .map(::ItemId)
            .toSet()
    }

    /** The technical facts of [item] as last indexed: a film or an episode, or a series from its episodes. */
    suspend fun summary(
        serverId: String,
        item: MediaItem,
    ): TechnicalSummary? =
        when (item.kind) {
            ItemKind.Series -> {
                dao
                    .episodesOf(serverId, item.id.value)
                    .takeIf { it.isNotEmpty() }
                    ?.map { it.toSummary() }
                    ?.reduce { a, b -> a.merge(b) }
            }

            else -> {
                dao.item(serverId, item.id.value)?.toSummary()
            }
        }

    suspend fun clear(serverId: String) = dao.clearServer(serverId)

    /** Forgets [items], deleted on the server (J6): a filter of the index never offers them again. */
    suspend fun forget(
        serverId: String,
        items: Collection<ItemId>,
    ) {
        if (items.isNotEmpty()) dao.forget(serverId, items.map { it.value })
    }

    companion object {
        const val PAGE_SIZE = 100

        /** A library is read whole again at least that often: it finds the items deleted on the server. */
        val FULL_EVERY: Duration = 7.days

        /** Read again before the last update: clocks of the server and the TV may differ. */
        val MARGIN: Duration = 1.hours

        val INDEXED_KINDS = setOf(LibraryKind.Movies, LibraryKind.Shows, LibraryKind.HomeVideos, LibraryKind.Mixed)
    }
}

private fun <E : Enum<E>> Set<E>.mask(): Int = fold(0) { mask, value -> mask or (1 shl value.ordinal) }

private inline fun <reified E : Enum<E>> Int.toSet(): Set<E> =
    enumValues<E>()
        .filter {
            this and (1 shl it.ordinal) != 0
        }.toSet()

private fun String.values(): Set<String> = split(Converters.SEPARATOR).filter { it.isNotEmpty() }.toSet()

private fun Set<String>.joined(): String = sorted().joinToString(Converters.SEPARATOR)

internal fun MediaItem.toIndexEntity(
    serverId: String,
    library: LibraryId,
    seenAt: Long,
): IndexedItemEntity {
    val summary = TechnicalSummary.of(mediaSources)
    return IndexedItemEntity(
        serverId = serverId,
        itemId = id.value,
        libraryId = library.value,
        kind = kind.name,
        seriesId = episode?.seriesId?.value?.takeIf { kind == ItemKind.Episode },
        title = title,
        maxWidth = summary.maxWidth,
        ranges = summary.ranges.mask(),
        videoCodecs = summary.videoCodecs.mask(),
        audio = summary.audio.mask(),
        audioLanguages = summary.audioLanguages.joined(),
        subtitleLanguages = summary.subtitleLanguages.joined(),
        forcedLanguages = summary.forcedSubtitleLanguages.joined(),
        versions = summary.versions,
        sizeBytes = summary.sizeBytes,
        indexedAt = seenAt,
    )
}

internal fun IndexedItemEntity.toSummary(): TechnicalSummary =
    TechnicalSummary(
        maxWidth = maxWidth,
        ranges = ranges.toSet<DynamicRange>(),
        videoCodecs = videoCodecs.toSet<VideoCodec>(),
        audio = audio.toSet<AudioFeature>(),
        audioLanguages = audioLanguages.values(),
        subtitleLanguages = subtitleLanguages.values(),
        forcedSubtitleLanguages = forcedLanguages.values(),
        versions = versions,
        sizeBytes = sizeBytes,
    )

/** The facts of two items together (the episodes of a series). */
private fun TechnicalSummary.merge(other: TechnicalSummary) =
    TechnicalSummary(
        maxWidth = listOfNotNull(maxWidth, other.maxWidth).maxOrNull(),
        ranges = ranges + other.ranges,
        videoCodecs = videoCodecs + other.videoCodecs,
        audio = audio + other.audio,
        audioLanguages = audioLanguages + other.audioLanguages,
        subtitleLanguages = subtitleLanguages + other.subtitleLanguages,
        forcedSubtitleLanguages = forcedSubtitleLanguages + other.forcedSubtitleLanguages,
        versions = maxOf(versions, other.versions),
        sizeBytes = listOfNotNull(sizeBytes, other.sizeBytes).takeIf { it.isNotEmpty() }?.sum(),
    )
