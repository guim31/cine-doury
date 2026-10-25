package fr.guim.cinedoury.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.home.HomeLayouts
import fr.guim.cinedoury.core.data.home.HomeRepository
import fr.guim.cinedoury.core.data.home.HomeRowConfig
import fr.guim.cinedoury.core.data.home.HomeRowType
import fr.guim.cinedoury.core.data.home.ProfileKey
import fr.guim.cinedoury.core.data.home.UserLibraries
import fr.guim.cinedoury.core.model.LibraryId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class HomeSettingsState(
    val layout: List<HomeRowConfig> = HomeLayouts.DEFAULT,
    val libraries: UserLibraries = UserLibraries(),
    val railHidden: Set<LibraryId> = emptySet(),
)

/**
 * The customisation of the home (`FONCTIONNALITES.md` § 2): rows added, removed, moved, their
 * libraries and their cards; and the libraries of the rail. Kept per profile, on this device.
 */
class HomeSettingsViewModel(
    sessions: SessionManager,
    private val home: HomeRepository,
) : ViewModel() {
    private val profile =
        sessions.active.value?.let { ProfileKey(it.profile.serverId, it.profile.user.id.value) }

    val state: StateFlow<HomeSettingsState> =
        if (profile == null) {
            MutableStateFlow(HomeSettingsState())
        } else {
            combine(
                home.layout(profile),
                home.libraries(profile),
                home.railHidden(profile),
            ) { layout, libraries, hidden ->
                HomeSettingsState(layout, libraries, hidden)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeSettingsState())
        }

    private fun edit(change: (List<HomeRowConfig>) -> List<HomeRowConfig>) {
        val profile = profile ?: return
        val next = change(state.value.layout)
        viewModelScope.launch { home.saveLayout(profile, next) }
    }

    fun moveUp(id: String) = edit { HomeLayouts.moveUp(it, id) }

    fun moveDown(id: String) = edit { HomeLayouts.moveDown(it, id) }

    fun remove(id: String) = edit { HomeLayouts.remove(it, id) }

    fun add(type: HomeRowType) = edit { HomeLayouts.add(it, type) }

    fun toggleStyle(id: String) = edit { rows -> HomeLayouts.update(rows, id, HomeLayouts::toggleStyle) }

    fun nextWindow(id: String) = edit { rows -> HomeLayouts.update(rows, id, HomeLayouts::nextWindow) }

    fun toggleLibrary(
        id: String,
        library: LibraryId,
        all: List<LibraryId>,
    ) = edit { rows -> HomeLayouts.update(rows, id) { HomeLayouts.toggleLibrary(it, library, all) } }

    fun reset() {
        val profile = profile ?: return
        viewModelScope.launch { home.resetLayout(profile) }
    }

    fun setShownInRail(
        library: LibraryId,
        shown: Boolean,
    ) {
        val profile = profile ?: return
        viewModelScope.launch { home.setShownInRail(profile, library, shown) }
    }
}
