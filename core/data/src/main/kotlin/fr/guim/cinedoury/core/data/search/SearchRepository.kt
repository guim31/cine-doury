package fr.guim.cinedoury.core.data.search

import fr.guim.cinedoury.core.data.db.RecentSearchEntity
import fr.guim.cinedoury.core.data.db.SearchDao
import fr.guim.cinedoury.core.data.home.ProfileKey
import fr.guim.cinedoury.core.model.ItemFilters
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.ItemQuery
import fr.guim.cinedoury.core.model.ItemSort
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.LibraryKind
import fr.guim.cinedoury.core.model.MAX_IDS
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.model.SearchHit
import fr.guim.cinedoury.core.model.SearchMatcher
import fr.guim.cinedoury.core.model.SearchQuery
import fr.guim.cinedoury.core.model.SearchRanking
import fr.guim.cinedoury.core.model.SearchText
import fr.guim.cinedoury.core.model.SearchTitle
import fr.guim.cinedoury.core.model.SortField
import fr.guim.cinedoury.core.source.LibrarySource
import fr.guim.cinedoury.core.source.SourceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.time.Clock
import java.util.Locale

/** An indexed title, its card and its titles ready for the matcher. */
class IndexedTitle internal constructor(
    val item: MediaItem,
    val library: LibraryId,
    internal val titles: List<SearchTitle>,
)

/** What the search shows while nothing is typed (`FONCTIONNALITES.md` § 6). */
data class SearchSuggestions(
    val recentlyAdded: List<MediaItem> = emptyList(),
    val genres: List<String> = emptyList(),
)

/**
 * The search (`SPEC.md` § 4.12). Two steps per query:
 * 1. [local], at each keystroke, without network: the title index of the server (films and series,
 *    title, original title, sort title) against the query, typos forgiven, limited to the libraries
 *    of the profile;
 * 2. [complete], once the typing pauses: the server confirms those titles (the cards come back with
 *    what the profile watched; a title the profile may not see is dropped), and adds what the index
 *    does not hold (people, collections, episodes, titles not indexed yet).
 *
 * It also keeps the recent searches of each profile.
 */
class SearchRepository(
    private val dao: SearchDao,
    private val clock: Clock = Clock.systemUTC(),
) {
    /** Every indexed title of [serverId], ready for [local], again whenever the index changes. */
    fun titles(serverId: String): Flow<List<IndexedTitle>> =
        dao
            .observeTitles(serverId)
            .distinctUntilChanged()
            .map { rows ->
                rows.map { row ->
                    val item = row.toItem()
                    IndexedTitle(item, LibraryId(row.libraryId), SearchRanking.titles(item))
                }
            }.flowOn(Dispatchers.Default)

    /** The indexed titles of [libraries] that match [typed], scored. */
    fun local(
        typed: String,
        titles: List<IndexedTitle>,
        libraries: Set<LibraryId>,
    ): List<SearchHit> {
        val query = SearchQuery.of(typed)
        if (query.isEmpty) return emptyList()
        return titles
            .asSequence()
            .filter { it.library in libraries }
            .mapNotNull { title -> SearchMatcher.best(query, title.titles)?.let { SearchHit(title.item, it) } }
            .sortedByDescending { it.score }
            .take(MAX_IDS)
            .toList()
    }

    /**
     * [local] confirmed and completed by the server. A failure of the server search is thrown; the
     * screen keeps the local results.
     */
    suspend fun complete(
        source: LibrarySource,
        typed: String,
        local: List<SearchHit>,
    ): List<SearchHit> =
        coroutineScope {
            val query = SearchQuery.of(typed)
            if (query.isEmpty) return@coroutineScope emptyList()
            val confirmed = async { confirm(source, local) }
            val found =
                SERVER_GROUPS
                    .map { (kinds, limit) -> async { source.search(typed.trim(), kinds, limit) } }
                    .awaitAll()
                    .flatten()
                    .map { item ->
                        SearchHit(
                            item,
                            SearchMatcher.best(query, SearchRanking.titles(item)) ?: SearchRanking.SERVER_ONLY,
                        )
                    }
            val mine = confirmed.await()
            val known = mine.map { it.item.id }.toSet()
            mine + found.filter { it.item.id !in known }
        }

    /** The local hits as the server sees them now; those it does not return are gone (or forbidden). */
    private suspend fun confirm(
        source: LibrarySource,
        local: List<SearchHit>,
    ): List<SearchHit> {
        if (local.isEmpty()) return emptyList()
        val fresh =
            source
                .items(ItemQuery(ids = local.map { it.item.id }.take(MAX_IDS)), PageRequest(limit = MAX_IDS))
                .items
                .associateBy { it.id }
        return local.mapNotNull { hit -> fresh[hit.item.id]?.let { SearchHit(it, hit.score) } }
    }

    /** The films and series of [genre], the latest added first. */
    suspend fun byGenre(
        source: LibrarySource,
        genre: String,
    ): List<SearchHit> =
        source
            .items(
                ItemQuery(
                    kinds = setOf(ItemKind.Movie, ItemKind.Series),
                    sort = ItemSort(SortField.DateAdded),
                    filters = ItemFilters(genres = setOf(genre)),
                ),
                PageRequest(limit = GENRE_LIMIT),
            ).items
            .map { SearchHit(it, SearchRanking.SERVER_ONLY) }

    /**
     * The latest additions of the film and series libraries, and the genres they hold, for the empty
     * search. A library that fails is left out.
     */
    suspend fun suggestions(
        source: LibrarySource,
        libraries: List<Library>,
        locale: Locale = Locale.getDefault(),
    ): SearchSuggestions =
        coroutineScope {
            val shown = libraries.filter { it.kind in SUGGESTED_KINDS }
            val latest =
                shown.map { library ->
                    async { safely { source.latest(library.id, LATEST_PER_LIBRARY) } }
                }
            val genres =
                shown.map { library ->
                    async {
                        safely { source.filterOptions(library.id, setOf(ItemKind.Movie, ItemKind.Series)).genres }
                    }
                }
            val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
            SearchSuggestions(
                recentlyAdded =
                    latest
                        .awaitAll()
                        .flatten()
                        .distinctBy { it.id }
                        .sortedByDescending { it.dateAdded }
                        .take(LATEST),
                genres =
                    genres
                        .awaitAll()
                        .flatten()
                        .distinctBy { SearchText.normalize(it) }
                        .sortedWith(collator),
            )
        }

    private suspend fun <T> safely(block: suspend () -> List<T>): List<T> =
        try {
            block()
        } catch (_: SourceException.Unexpected) {
            emptyList()
        } catch (_: SourceException.NotFound) {
            emptyList()
        } catch (_: SourceException.Server) {
            emptyList()
        }

    /** The [RECENT] last searches of the profile, the latest first. */
    fun recent(profile: ProfileKey): Flow<List<String>> =
        dao.observeRecent(profile.serverId, profile.userId, RECENT).map { rows -> rows.map { it.text } }

    /** Remembers a search that led somewhere (a card opened); a search typed again moves up. */
    suspend fun remember(
        profile: ProfileKey,
        typed: String,
    ) {
        val text = typed.trim()
        val normalized = SearchText.normalize(text)
        if (normalized.isEmpty()) return
        dao.remember(RecentSearchEntity(profile.serverId, profile.userId, normalized, text, clock.millis()), RECENT)
    }

    suspend fun forget(
        profile: ProfileKey,
        typed: String,
    ) = dao.forgetRecent(profile.serverId, profile.userId, SearchText.normalize(typed))

    suspend fun clearRecent(profile: ProfileKey) = dao.clearRecent(profile.serverId, profile.userId)

    companion object {
        /** Recent searches kept per profile. */
        const val RECENT = 10
        const val LATEST = 18
        private const val LATEST_PER_LIBRARY = 12
        private const val GENRE_LIMIT = 48

        /** What the server is asked for, by group, so that one group never crowds out the others. */
        private val SERVER_GROUPS =
            listOf(
                setOf(ItemKind.Movie, ItemKind.Series) to 40,
                setOf(ItemKind.Episode) to SearchRanking.ROW_LIMIT,
                setOf(ItemKind.Person) to SearchRanking.ROW_LIMIT,
                setOf(ItemKind.Collection) to SearchRanking.ROW_LIMIT,
            )

        private val SUGGESTED_KINDS = setOf(LibraryKind.Movies, LibraryKind.Shows, LibraryKind.Mixed)
    }
}
