package fr.guim.cinedoury.feature.library

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.guim.cinedoury.core.data.ConnectionMode
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.UserDataUpdates
import fr.guim.cinedoury.core.data.home.HomeRepository
import fr.guim.cinedoury.core.data.home.ProfileKey
import fr.guim.cinedoury.core.data.index.IndexStatus
import fr.guim.cinedoury.core.data.index.IndexedQuery
import fr.guim.cinedoury.core.data.index.LibraryIndex
import fr.guim.cinedoury.core.data.library.ItemPager
import fr.guim.cinedoury.core.data.library.LibraryIconRepository
import fr.guim.cinedoury.core.data.library.LibraryState
import fr.guim.cinedoury.core.data.library.LibraryStateRepository
import fr.guim.cinedoury.core.data.library.LibraryTab
import fr.guim.cinedoury.core.data.library.LibraryView
import fr.guim.cinedoury.core.data.library.PagedItems
import fr.guim.cinedoury.core.data.manage.ManageUpdates
import fr.guim.cinedoury.core.model.FilterOptions
import fr.guim.cinedoury.core.model.ItemFilters
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.ItemQuery
import fr.guim.cinedoury.core.model.ItemSort
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.LibraryKind
import fr.guim.cinedoury.core.model.LibraryScan
import fr.guim.cinedoury.core.model.ManageRights
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.MetadataRefresh
import fr.guim.cinedoury.core.model.ScanStatus
import fr.guim.cinedoury.core.model.SortField
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.ui.Events
import fr.guim.cinedoury.core.ui.RailState
import fr.guim.cinedoury.core.ui.railState
import fr.guim.cinedoury.feature.manage.ItemManager
import fr.guim.cinedoury.feature.manage.ManageNotice
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
 * @param index where the local index stands: its filters (HDR, audio, codec) are complete once it is
 * [IndexStatus.ready].
 * @param rights what the profile may manage: the « Gestion » button (J6).
 * @param scan where the scan started from « Gestion » stands; null when none was started.
 * @param selection the items ticked, while a selection is on (J6); null otherwise.
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
    val index: IndexStatus? = null,
    val rights: ManageRights = ManageRights.NONE,
    val scan: ScanStatus? = null,
    val selection: Set<ItemId>? = null,
    val notice: ManageNotice? = null,
    val manageError: SourceException? = null,
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
    icons: LibraryIconRepository,
    private val index: LibraryIndex,
    private val manager: ItemManager? = null,
    manageUpdates: ManageUpdates? = null,
    updates: UserDataUpdates? = null,
) : ViewModel(),
    LibraryActions {
    private val active = sessions.active.value
    private val profile = active?.let { ProfileKey(it.profile.serverId, it.profile.user.id.value) }
    private val pager = ItemPager(viewModelScope)
    private val local =
        MutableStateFlow(
            LibraryUiState(
                remote = active?.connection?.mode == ConnectionMode.Remote,
                rights = manager?.cachedRights ?: ManageRights.NONE,
            ),
        )
    private var noticeJob: Job? = null
    private var saving: Job? = null

    /** The item focused last; written at each step of the focus, never emitted ([LibraryActions.focusedIndex]). */
    override var focusedIndex: Int? = null
        private set

    /** Where the list stands: the item focused last, else the remembered one. */
    private val position get() = focusedIndex ?: browse.position

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
            home.railState(sessions, icons),
            library ?: MutableStateFlow(null),
            profile?.let { index.status(it.serverId) } ?: MutableStateFlow(null),
        ) { state, items, rail, library, index ->
            state.copy(items = items, rail = rail, library = library ?: state.library, index = index)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, local.value)

    /** The query of the « Tout » or « Collections » tab, as read now: through the index for its filters. */
    private var reading: IndexedQuery? = null

    init {
        viewModelScope.launch {
            // The kind of the library decides the query: read from the cache before anything.
            val known = library?.first()
            val remembered = profile?.let { states.load(it, libraryId) } ?: LibraryState()
            local.update { it.copy(library = known, browse = remembered, restored = true) }
            reload(around = remembered.position)
            loadOptions()
        }
        if (manager != null) {
            viewModelScope.launch {
                val rights = guardedOrNull { manager.rights() } ?: return@launch
                local.update { it.copy(rights = rights) }
            }
        }
        // An item deleted, identified again or given a new picture: its page is read again, at the
        // same place (J6).
        manageUpdates?.let { flow ->
            viewModelScope.launch {
                flow.updates.collect { reload(around = position) }
            }
        }
        // « Vu » or a favourite from the menu of a card, a position left by the player: the card
        // changes where it is, nothing is read again (essai réel 5).
        updates?.let { flow ->
            viewModelScope.launch {
                flow.updates.collect { (id, data) ->
                    pager.update(id) { it.copy(userData = data) }
                    local.update { state ->
                        state.copy(nextUp = state.nextUp?.map { if (it.id == id) it.copy(userData = data) else it })
                    }
                }
            }
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
                val serverId = profile?.serverId ?: return
                val query = IndexedQuery(source, index, serverId, query()).also { reading = it }
                pager.load(around) { page -> guarded { query.page(page) } }
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
        focusedIndex = 0
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
        if (position == index) return
        focusedIndex = index
        saving?.cancel()
        saving =
            viewModelScope.launch {
                delay(SAVE_DELAY_MILLIS)
                save()
            }
    }

    private fun save() {
        val profile = profile ?: return
        val state = browse.copy(position = position)
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
                    guardedOrNull { reading?.countBelow(letter) ?: source.count(query().copy(titleBelow = letter)) }
                        ?: return@launch
                }
            val total = pager.items.value.total ?: return@launch
            val target = index.coerceAtMost((total - 1).coerceAtLeast(0))
            pager.request(target)
            jumps.send(Jump(target))
        }
    }

    override fun retry() {
        local.update { it.copy(error = null) }
        if (pager.items.value.error != null) pager.retry() else reload(position)
        if (local.value.options == null) loadOptions()
    }

    private suspend fun managed(block: suspend (ItemManager) -> Unit): Boolean {
        val manager = manager ?: return false
        return try {
            guarded { block(manager) }
            true
        } catch (_: SourceException.Unauthorized) {
            false
        } catch (error: SourceException) {
            local.update { it.copy(manageError = error) }
            false
        }
    }

    private fun show(notice: ManageNotice) {
        local.update { it.copy(notice = notice) }
        noticeJob?.cancel()
        noticeJob =
            viewModelScope.launch {
                delay(NOTICE_MILLIS)
                local.update { it.copy(notice = null) }
            }
    }

    /**
     * A scan of new files shows its progress (the scheduled task of the server) until it ends, then
     * the library is read again; « Tout actualiser » is done by the server in the background.
     */
    override fun scan(mode: LibraryScan) {
        if (local.value.scan?.running == true) return
        viewModelScope.launch {
            if (!managed { it.scan(libraryId, mode) }) return@launch
            if (mode == LibraryScan.RefreshAll) {
                show(ManageNotice.ScanRequested)
                return@launch
            }
            local.update { it.copy(scan = ScanStatus(running = true)) }
            // The task takes a moment to start: never seen running means already done.
            delay(SCAN_POLL_MILLIS)
            var running = true
            while (running) {
                val status = guardedOrNull { manager?.scanStatus() }
                status?.let { found -> local.update { it.copy(scan = found) } }
                running = status?.running == true
                if (running) delay(SCAN_POLL_MILLIS)
            }
            local.update { it.copy(scan = ScanStatus.IDLE) }
            show(ManageNotice.ScanDone)
            reload(around = position)
        }
    }

    override fun startSelection(item: MediaItem?) =
        local.update { it.copy(selection = it.selection.orEmpty() + listOfNotNull(item?.id)) }

    override fun toggleSelected(item: MediaItem) =
        local.update { state ->
            val selection = state.selection ?: return@update state
            state.copy(selection = if (item.id in selection) selection - item.id else selection + item.id)
        }

    override fun endSelection() = local.update { it.copy(selection = null) }

    override fun markSelection(played: Boolean) {
        val chosen =
            local.value.selection
                ?.toList()
                .orEmpty()
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            if (managed { it.setPlayed(chosen, played) }) {
                local.update { it.copy(selection = null) }
                show(ManageNotice.SelectionDone)
            }
        }
    }

    override fun refreshSelection() {
        val chosen =
            local.value.selection
                ?.toList()
                .orEmpty()
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            if (managed { manager -> chosen.forEach { manager.refresh(it, MetadataRefresh.FillMissing) } }) {
                local.update { it.copy(selection = null) }
                show(ManageNotice.RefreshRequested)
            }
        }
    }

    override fun dismissManageError() = local.update { it.copy(manageError = null) }

    companion object {
        const val DIGITS = "#"
        val LETTERS = listOf(DIGITS) + ('A'..'Z').map { it.toString() }
        private const val NEXT_UP_LIMIT = 100
        private const val SAVE_DELAY_MILLIS = 500L
        private const val SCAN_POLL_MILLIS = 1_000L
        private const val NOTICE_MILLIS = 4_000L
    }
}
