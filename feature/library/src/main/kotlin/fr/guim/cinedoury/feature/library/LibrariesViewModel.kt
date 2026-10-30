package fr.guim.cinedoury.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.home.HomeRepository
import fr.guim.cinedoury.core.data.library.LibraryIconRepository
import fr.guim.cinedoury.core.ui.RailState
import fr.guim.cinedoury.core.ui.railState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * The libraries of the open profile, in its order, those it hid from the rail left out, with their
 * icons (M1): the tab « Bibliothèques » of the phone lists what the rail of the TV shows. Read from
 * the cache the home keeps up to date.
 */
class LibrariesViewModel(
    sessions: SessionManager,
    home: HomeRepository,
    icons: LibraryIconRepository,
) : ViewModel() {
    val state: StateFlow<RailState?> =
        home.railState(sessions, icons).stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
