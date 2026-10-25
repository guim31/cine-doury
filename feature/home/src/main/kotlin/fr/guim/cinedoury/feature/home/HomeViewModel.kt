package fr.guim.cinedoury.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.guim.cinedoury.core.data.ConnectionMode
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.ui.Events
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeState(
    val profileName: String = "",
    val remote: Boolean = false,
    /** Null while loading. */
    val libraries: List<Library>? = null,
    val error: SourceException? = null,
)

/** The provisional home of J1: the libraries of the server, until the home of J2. */
class HomeViewModel(
    private val sessions: SessionManager,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = mutableState.asStateFlow()

    /** The server rejected the token: the profile is signed out. */
    val sessionEnded = Events<Unit>()

    init {
        load()
    }

    fun load() {
        val active = sessions.active.value ?: return
        mutableState.value =
            HomeState(
                profileName = active.profile.user.name,
                remote = active.connection.mode == ConnectionMode.Remote,
            )
        viewModelScope.launch {
            try {
                val libraries = active.library.libraries()
                mutableState.update { it.copy(libraries = libraries) }
            } catch (_: SourceException.Unauthorized) {
                sessions.sessionRejected()
                sessionEnded.send(Unit)
            } catch (error: SourceException) {
                mutableState.update { it.copy(error = error) }
            }
        }
    }
}
