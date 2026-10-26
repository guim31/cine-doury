package fr.guim.cinedoury.core.data.library

import fr.guim.cinedoury.core.data.db.LibraryStateEntity
import fr.guim.cinedoury.core.data.db.PreferencesDao
import fr.guim.cinedoury.core.data.home.ProfileKey
import fr.guim.cinedoury.core.model.ItemFilters
import fr.guim.cinedoury.core.model.ItemSort
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.SortField

/** The tabs of a library (`FONCTIONNALITES.md` § 3). */
enum class LibraryTab {
    All,
    Collections,
    Genres,

    /** Series libraries only. */
    NextUp,
}

/** How a library lays its items out. */
enum class LibraryView {
    SmallPosters,
    Posters,
    LargePosters,
    List,
    Thumbnails,
}

/**
 * What a library remembers between two visits (`UI.md` § 3, `SPEC.md` § 4.9): its tab, view, sort,
 * filters, grouping of collections and the item that had the focus.
 */
data class LibraryState(
    val tab: LibraryTab = LibraryTab.All,
    val view: LibraryView = LibraryView.Posters,
    val sort: ItemSort = ItemSort(),
    val filters: ItemFilters = ItemFilters.NONE,
    val groupCollections: Boolean = false,
    val position: Int = 0,
)

/**
 * The [LibraryState] of each library of each profile, in Room. A library the profile never opened
 * starts in the view of « Vue par défaut » ([defaultView], `SPEC.md` § 4.16).
 */
class LibraryStateRepository(
    private val preferences: PreferencesDao,
    private val defaultView: suspend (ProfileKey) -> LibraryView = { LibraryView.Posters },
) {
    suspend fun load(
        profile: ProfileKey,
        library: LibraryId,
    ): LibraryState =
        preferences.libraryState(profile.serverId, profile.userId, library.value)?.toModel()
            ?: LibraryState(view = defaultView(profile))

    suspend fun save(
        profile: ProfileKey,
        library: LibraryId,
        state: LibraryState,
    ) {
        preferences.saveLibraryState(
            LibraryStateEntity(
                serverId = profile.serverId,
                userId = profile.userId,
                libraryId = library.value,
                tab = state.tab.name,
                view = state.view.name,
                sortField = state.sort.field.name,
                ascending = state.sort.ascending,
                filters = FiltersCodec.encode(state.filters),
                groupCollections = state.groupCollections,
                position = state.position,
            ),
        )
    }
}

private fun LibraryStateEntity.toModel(): LibraryState {
    val field = SortField.entries.firstOrNull { it.name == sortField } ?: SortField.Title
    return LibraryState(
        tab = LibraryTab.entries.firstOrNull { it.name == tab } ?: LibraryTab.All,
        view = LibraryView.entries.firstOrNull { it.name == view } ?: LibraryView.Posters,
        sort = ItemSort(field, ascending),
        filters = FiltersCodec.decode(filters),
        groupCollections = groupCollections,
        position = position,
    )
}
