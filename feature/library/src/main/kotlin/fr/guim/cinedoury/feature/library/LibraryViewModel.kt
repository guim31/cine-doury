package fr.guim.cinedoury.feature.library

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.guim.cinedoury.core.data.ConnectionMode
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.home.HomeRepository
import fr.guim.cinedoury.core.data.home.ProfileKey
import fr.guim.cinedoury.core.data.library.ItemPager
import fr.guim.cinedoury.core.data.library.LibraryState
import fr.guim.cinedoury.core.data.library.LibraryStateRepository
import fr.guim.cinedoury.core.data.library.LibraryTab
import fr.guim.cinedoury.core.data.library.LibraryView
import fr.guim.cinedoury.core.data.library.PagedItems
import fr.guim.cinedoury.core.model.FilterOptions
import fr.guim.cinedoury.core.model.ItemFilters
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.ItemQuery
import fr.guim.cinedoury.core.model.ItemSort
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.LibraryKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.SortField
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.ui.Events
import fr.guim.cinedoury.core.ui.RailState
import fr.guim.cinedoury.core.ui.railState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @param browse what the library remembers: tab, view, sort, filters, position.
 * @param items the items of the « Tout » and « Collections » tabs, read page by page.
 * @param nextUp the « À suivre » tab of a series library; null while read.
 * @param options the values of the filters; null until read.
 * @param restored false until the remembered state is read: the screen waits for it, so that the
 * grid opens at the right place.
 */
@Immutable
data class LibraryUiState(
    val library: Library? = null,
    val rail: RailState = RailState(),
    val remote: Boolean = false,
    val browse: LibraryState = LibraryState(),
    val items: PagedItems = PagedItems(),
    val nextUp: List<MediaItem>? = null,
    val options: FilterOptions? = null,
    val restored: Boolean = false,
    val error: SourceException? = null,
) {
    val tabs: List<LibraryTab> get() = library?.kind.tabs()

    /** The A–Z jump works on titles only. */
    val alphabetical: Boolean
        get() = browse.sort.field == SortField.Title && browse.tab in setOf(LibraryTab.All, LibraryTab.Collections)
}

/** The tabs of a library of this kind (`FONCTIONNALITES.md` § 3). */
fun LibraryKind?.tabs(): List<LibraryTab> =
    when (this) {
        LibraryKind.Movies -> listOf(LibraryTab.All, LibraryTab.Collections, LibraryTab.Genres)
        LibraryKind.Shows -> listOf(LibraryTab.All, LibraryTab.NextUp, LibraryTab.Genres)
        else -> listOf(LibraryTab.All)
    }

/** A place in the list the screen must show and focus: a letter of the A–Z jump. */
data class Jump(
    val index: Int,
)

/**
 * A library (`FONCTIONNALITES.md` § 3): its tabs, its view, its sort and its filters, all run by
 * the server, its items read page by page, the A–Z jump. It remembers all of it, and the item that
 * had the focus, for the next visit (`SPEC.md` § 4.9).
 */
@Suppress("TooManyFunctions") // One function per gesture of the screen.
class LibraryViewModel(
    private val libraryId: LibraryId,
    private val sessions: SessionManager,
    home: HomeRepository,
    private val states: LibraryStateRepository,
) : ViewModel(),
    LibraryActions {
    private val active = sessions.active.value
    private val profile = active?.let { ProfileKey(it.profile.serverId, it.profile.user.id.value) }
    private val pager = ItemPager(viewModelScope)
    private val local = MutableStateFlow(LibraryUiState(remote = active?.connection?.mode == ConnectionMode.Remote))
    private var saving: Job? = null

    /** The server rejected the token: the profile is signed out. */
    val sessionEnded = Events<Unit>()

    /** The A–Z jump found its place. */
    val jumps = Events<Jump>()

    private val library =
        profile?.let { key ->
            home.libraries(key).map { all ->
                all.libraries.firstOrNull {
                    it.id ==
                        libraryId
                }
            }
        }

    val state: StateFlow<LibraryUiState> =
        combine(
            local,
            pager.items,
            home.railState(sessions),
            library ?: MutableStateFlow(null),
        ) { state, items, rail, library ->
            state.copy(items = items, rail = rail, library = library ?: state.library)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, local.value)

    init {
        viewModelScope.launch {
            // The kind of the library decides the query: read from the cache before anything.
            val known = library?.first()
            val remembered = profile?.let { states.load(it, libraryId) } ?: LibraryState()
            local.update { it.copy(library = known, browse = remembered, restored = true) }
            reload(around = remembered.position)
            loadOptions()
        }
    }

    private val browse get() = local.value.browse

    private fun kind(): LibraryKind? = local.value.library?.kind

    /** The query of the « Tout » or « Collections » tab. */
    private fun query(): ItemQuery {
        val kinds =
            when {
                browse.tab == LibraryTab.Collections -> setOf(ItemKind.Collection)
                kind() == LibraryKind.Movies -> setOf(ItemKind.Movie)
                kind() == LibraryKind.Shows -> setOf(ItemKind.Series)
                else -> emptySet()
            }
        val collections = browse.tab == LibraryTab.All && kind() == LibraryKind.Movies && browse.groupCollections
        return ItemQuery(
            library = libraryId,
            kinds = kinds,
            sort = if (browse.tab == LibraryTab.Collections) ItemSort() else browse.sort,
            filters = if (browse.tab == LibraryTab.Collections) ItemFilters.NONE else browse.filters,
            groupCollections = collections,
        )
    }

    private fun reload(around: Int = 0) {
        val source = active?.library ?: return
        when (browse.tab) {
            LibraryTab.All, LibraryTab.Collections -> {
                val query = query()
                pager.load(around) { page -> guarded { source.items(query, page) } }
            }

            LibraryTab.NextUp -> {
                local.update { it.copy(nextUp = null) }
                viewModelScope.launch {
                    val episodes = guardedOrNull { source.nextUp(libraryId, since = null, limit = NEXT_UP_LIMIT) }
                    local.update { it.copy(nextUp = episodes.orEmpty()) }
                }
            }

            // The genres come with the filter values.
            LibraryTab.Genres -> {}
        }
    }

    private fun loadOptions() {
        val source = active?.library ?: return
        viewModelScope.launch {
            val kinds = query().kinds
            val options = guardedOrNull { source.filterOptions(libraryId, kinds) }
            local.update { it.copy(options = options ?: FilterOptions()) }
        }
    }

    private suspend fun <T> guarded(block: suspend () -> T): T =
        try {
            block()
        } catch (rejected: SourceException.Unauthorized) {
            sessions.sessionRejected()
            sessionEnded.send(Unit)
            throw rejected
        }

    private suspend fun <T> guardedOrNull(block: suspend () -> T): T? =
        try {
            guarded(block)
        } catch (error: SourceException) {
            local.update { it.copy(error = error) }
            null
        }

    private fun change(
        reload: Boolean = true,
        transform: (LibraryState) -> LibraryState,
    ) {
        local.update { it.copy(browse = transform(it.browse).copy(position = 0), error = null) }
        save()
        if (reload) reload()
    }

    override fun selectTab(tab: LibraryTab) {
        if (tab == browse.tab) return
        change { it.copy(tab = tab) }
    }

    override fun setView(view: LibraryView) = change(reload = false) { it.copy(view = view) }

    /** The same field again reverses the direction; another field starts in its usual direction. */
    override fun sortBy(field: SortField) =
        change {
            it.copy(
                sort =
                    if (it.sort.field ==
                        field
                    ) {
                        it.sort.copy(ascending = !it.sort.ascending)
                    } else {
                        ItemSort(field)
                    },
            )
        }

    override fun setFilters(filters: ItemFilters) = change { it.copy(filters = filters, tab = LibraryTab.All) }

    override fun clearFilters() = setFilters(ItemFilters.NONE)

    override fun toggleGroupCollections() = change { it.copy(groupCollections = !it.groupCollections) }

    /** A genre of the « Genres » tab: « Tout », filtered on it. */
    override fun openGenre(genre: String) = setFilters(ItemFilters(genres = setOf(genre)))

    /** The item at [index] is on screen: its page is read if it was not. */
    override fun onVisible(index: Int) = pager.request(index)

    /** The item at [index] took the focus: remembered for the next visit, without writing at each step. */
    override fun onFocused(index: Int) {
        if (browse.position == index) return
        local.update { it.copy(browse = it.browse.copy(position = index)) }
        saving?.cancel()
        saving =
            viewModelScope.launch {
                delay(SAVE_DELAY_MILLIS)
                save()
            }
    }

    private fun save() {
        val profile = profile ?: return
        val state = browse
        viewModelScope.launch { states.save(profile, libraryId, state) }
    }

    /** Jumps to the first title at [letter] (« # » for the titles that start with a digit or a sign). */
    override fun jumpTo(letter: String) {
        val source = active?.library ?: return
        viewModelScope.launch {
            val index =
                if (letter ==
                    DIGITS
                ) {
                    0
                } else {
                    guardedOrNull { source.count(query().copy(titleBelow = letter)) } ?: return@launch
                }
            val total = pager.items.value.total ?: return@launch
            val target = index.coerceAtMost((total - 1).coerceAtLeast(0))
            pager.request(target)
            jumps.send(Jump(target))
        }
    }

    override fun retry() {
        local.update { it.copy(error = null) }
        if (pager.items.value.error != null) pager.retry() else reload(browse.position)
        if (local.value.options == null) loadOptions()
    }

    companion object {
        const val DIGITS = "#"
        val LETTERS = listOf(DIGITS) + ('A'..'Z').map { it.toString() }
        private const val NEXT_UP_LIMIT = 100
        private const val SAVE_DELAY_MILLIS = 500L
    }
}
