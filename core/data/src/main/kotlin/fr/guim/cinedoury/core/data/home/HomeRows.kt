package fr.guim.cinedoury.core.data.home

import fr.guim.cinedoury.core.model.HomeExclusions
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.ItemQuery
import fr.guim.cinedoury.core.model.ItemSort
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.LibraryKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.model.SortField
import fr.guim.cinedoury.core.source.LibrarySource
import fr.guim.cinedoury.core.source.SourceException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A row as the home shows it: a [HomeRowConfig] over the libraries it draws from. « Ajouts
 * récents » gives one row per library, the others one row for all their libraries.
 *
 * @param key stable: the cache and the focus memory of the row hang on it.
 * @param library the library of a row per library.
 * @param scope the libraries the row reads.
 * @param wholeServer the row reads the server at once: no library is left out of it.
 */
data class HomeRowSpec(
    val key: String,
    val config: HomeRowConfig,
    val library: Library?,
    val scope: List<Library>,
    val wholeServer: Boolean,
)

/** Turns the layout into the rows to show, and reads each row from the source (`SPEC.md` § 4.9). */
object HomeRows {
    /** Items per row: enough to scroll, few enough to answer fast. */
    const val LIMIT = 24

    /**
     * The rows of [layout] for the [libraries] of the user. The libraries excluded from the home on
     * the server never appear; those excluded from « Ajouts récents » get no row of their own.
     */
    fun plan(
        layout: List<HomeRowConfig>,
        libraries: List<Library>,
        exclusions: HomeExclusions,
    ): List<HomeRowSpec> =
        layout.flatMap { config ->
            val eligible = eligible(config.type, libraries, exclusions)
            val scope = if (config.libraries.isEmpty()) eligible else eligible.filter { it.id in config.libraries }
            when {
                scope.isEmpty() -> {
                    emptyList()
                }

                config.type == HomeRowType.Latest -> {
                    scope.filter { it.id !in exclusions.fromLatest }.map { library ->
                        HomeRowSpec(
                            "${config.id}:${library.id.value}",
                            config,
                            library,
                            listOf(library),
                            wholeServer = false,
                        )
                    }
                }

                else -> {
                    // Libraries of other kinds hold nothing for this row: only exclusions matter.
                    val whole = config.libraries.isEmpty() && exclusions.fromHome.isEmpty()
                    listOf(HomeRowSpec(config.id, config, null, scope, whole))
                }
            }
        }

    /** The libraries a row of [type] can draw from: the kinds that hold its items, not excluded. */
    fun eligible(
        type: HomeRowType,
        libraries: List<Library>,
        exclusions: HomeExclusions,
    ): List<Library> = libraries.filter { it.id !in exclusions.fromHome && it.kind in type.kinds }

    private val HomeRowType.kinds: Set<LibraryKind>
        get() =
            when (this) {
                HomeRowType.NextUp -> {
                    setOf(LibraryKind.Shows, LibraryKind.Mixed)
                }

                HomeRowType.RecentlyReleased -> {
                    setOf(LibraryKind.Movies, LibraryKind.Shows, LibraryKind.Mixed)
                }

                HomeRowType.Resume, HomeRowType.Latest -> {
                    setOf(LibraryKind.Movies, LibraryKind.Shows, LibraryKind.HomeVideos, LibraryKind.Mixed)
                }
            }

    /** The items of [row], read from [source]; a row over several libraries merges their answers. */
    suspend fun fetch(
        row: HomeRowSpec,
        source: LibrarySource,
        clock: Clock = Clock.systemDefaultZone(),
    ): List<MediaItem> =
        when (row.config.type) {
            HomeRowType.Resume -> {
                perLibrary(row) { source.resume(it, LIMIT) }
                    .sortedByDescending { it.userData?.lastPlayed ?: Instant.EPOCH }
            }

            HomeRowType.NextUp -> {
                val since = row.config.windowDays?.let { clock.instant().minus(it.toLong(), ChronoUnit.DAYS) }
                perLibrary(row) { source.nextUp(it, since, LIMIT) }
            }

            HomeRowType.Latest -> {
                source.latest(checkNotNull(row.library).id, LIMIT)
            }

            HomeRowType.RecentlyReleased -> {
                val today = LocalDate.now(clock)
                perLibrary(row) { library ->
                    val query =
                        ItemQuery(library, RELEASED_KINDS, ItemSort(SortField.ReleaseDate), releasedBefore = today)
                    source.items(query, PageRequest(limit = LIMIT)).items
                }.sortedByDescending { it.premiereDate ?: LocalDate.MIN }
            }
        }.take(LIMIT)

    private val RELEASED_KINDS = setOf(ItemKind.Movie, ItemKind.Series)

    private suspend fun perLibrary(
        row: HomeRowSpec,
        read: suspend (LibraryId?) -> List<MediaItem>,
    ): List<MediaItem> =
        if (row.wholeServer) {
            read(null)
        } else {
            coroutineScope {
                row.scope
                    .map { library -> async { read(library.id) } }
                    .awaitAll()
                    .flatten()
            }
        }

    /** The keys under which the sources of the mosaic « Ce soir » are cached, apart from the rows. */
    object TonightKeys {
        const val RESUME = "tonight:resume"
        const val NEXT_UP = "tonight:next-up"
        const val LATEST = "tonight:latest"
        const val REDISCOVER = "tonight:rediscover"
        val ALL = listOf(RESUME, NEXT_UP, LATEST, REDISCOVER)
    }

    /** What a source of the mosaic reads: enough to fill it without doubles. */
    private const val TONIGHT_LIMIT = 12

    /**
     * The sources of the mosaic « Ce soir » (essai réel 5), whatever rows the home shows: the
     * libraries excluded from the home on the server never count, nor those excluded from the latest
     * additions for these. A source that fails leaves the others.
     */
    suspend fun fetchTonight(
        libraries: List<Library>,
        exclusions: HomeExclusions,
        source: LibrarySource,
    ): Map<String, List<MediaItem>> =
        coroutineScope {
            val resume = eligible(HomeRowType.Resume, libraries, exclusions)
            val shows = eligible(HomeRowType.NextUp, libraries, exclusions)
            val latest = eligible(HomeRowType.Latest, libraries, exclusions).filter { it.id !in exclusions.fromLatest }
            val films = eligible(HomeRowType.RecentlyReleased, libraries, exclusions)
            val whole = exclusions.fromHome.isEmpty()
            val reads =
                mapOf(
                    TonightKeys.RESUME to
                        async {
                            each(resume, whole) { source.resume(it, TONIGHT_LIMIT) }
                                .sortedByDescending { it.userData?.lastPlayed ?: Instant.EPOCH }
                        },
                    TonightKeys.NEXT_UP to async { each(shows, whole) { source.nextUp(it, null, TONIGHT_LIMIT) } },
                    TonightKeys.LATEST to
                        async {
                            latest
                                .map { library -> async { quietly { source.latest(library.id, TONIGHT_LIMIT) } } }
                                .awaitAll()
                                .flatten()
                                .sortedByDescending { it.dateAdded ?: Instant.EPOCH }
                        },
                    TonightKeys.REDISCOVER to
                        async {
                            // The oldest additions first: what was forgotten (`Tonight` keeps the unwatched).
                            val query = { library: Library ->
                                ItemQuery(library.id, RELEASED_KINDS, ItemSort(SortField.DateAdded, ascending = true))
                            }
                            films
                                .map { library ->
                                    async {
                                        quietly {
                                            source
                                                .items(
                                                    query(library),
                                                    PageRequest(limit = TONIGHT_LIMIT),
                                                ).items
                                        }
                                    }
                                }.awaitAll()
                                .flatten()
                        },
                )
            reads.mapValues { (_, read) -> read.await().take(TONIGHT_LIMIT) }
        }

    private suspend fun each(
        libraries: List<Library>,
        whole: Boolean,
        read: suspend (LibraryId?) -> List<MediaItem>,
    ): List<MediaItem> =
        when {
            libraries.isEmpty() -> emptyList()
            whole -> quietly { read(null) }
            else -> coroutineScope { libraries.map { async { quietly { read(it.id) } } }.awaitAll().flatten() }
        }

    private suspend fun quietly(read: suspend () -> List<MediaItem>): List<MediaItem> =
        try {
            read()
        } catch (_: SourceException) {
            emptyList()
        }
}
