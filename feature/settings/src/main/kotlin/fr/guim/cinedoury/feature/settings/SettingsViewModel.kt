package fr.guim.cinedoury.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.guim.cinedoury.core.data.ActiveSession
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.StartupMode
import fr.guim.cinedoury.core.data.home.HomeRepository
import fr.guim.cinedoury.core.data.home.ProfileKey
import fr.guim.cinedoury.core.data.home.UserLibraries
import fr.guim.cinedoury.core.data.index.IndexScheduler
import fr.guim.cinedoury.core.data.index.IndexStatus
import fr.guim.cinedoury.core.data.index.LibraryIndex
import fr.guim.cinedoury.core.data.library.LibraryIconRepository
import fr.guim.cinedoury.core.data.prefs.AppPreferences
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryIcon
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.ui.Events
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * @param libraries the libraries of the profile, with the icon each one shows in the rail.
 * @param chosen the libraries whose icon the user picked (the others follow their name).
 * @param index where the local index of the server stands.
 */
@Immutable
data class SettingsState(
    val session: ActiveSession? = null,
    val startupMode: StartupMode = AppPreferences.DEFAULT_STARTUP,
    val libraries: List<Library> = emptyList(),
    val chosen: Map<LibraryId, LibraryIcon> = emptyMap(),
    val index: IndexStatus = IndexStatus(),
)

enum class SettingsEvent {
    /** The profile was signed out or left: the profile picker. */
    ProfileClosed,

    /** The server was forgotten: the welcome screen. */
    ServerForgotten,
}

/** The settings of J1: the profile, the startup, the server (`FONCTIONNALITES.md` § 1 and § 12). */
class SettingsViewModel(
    private val sessions: SessionManager,
    home: HomeRepository,
    private val icons: LibraryIconRepository,
    index: LibraryIndex,
    private val scheduler: IndexScheduler,
) : ViewModel() {
    private val profile = sessions.active.value?.let { ProfileKey(it.profile.serverId, it.profile.user.id.value) }

    val state: StateFlow<SettingsState> =
        combine(
            sessions.active,
            sessions.startupMode,
            profile?.let { home.libraries(it) } ?: flowOf(UserLibraries()),
            profile?.let { icons.chosen(it.serverId) } ?: flowOf(emptyMap()),
            profile?.let { index.status(it.serverId) } ?: flowOf(IndexStatus()),
        ) { session, mode, libraries, chosen, status ->
            SettingsState(session, mode, libraries.libraries, chosen, status)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsState(sessions.active.value))

    /** The icon of [library] in the rail; null goes back to the one its name suggests. */
    fun setIcon(
        library: LibraryId,
        icon: LibraryIcon?,
    ) {
        val serverId = profile?.serverId ?: return
        viewModelScope.launch { icons.set(serverId, library, icon) }
    }

    /** Reads every film and episode again into the local index, in the background. */
    fun rebuildIndex() = scheduler.rebuild()

    val events = Events<SettingsEvent>()

    fun toggleStartupMode() {
        val next =
            if (state.value.startupMode ==
                StartupMode.LastProfile
            ) {
                StartupMode.AlwaysAsk
            } else {
                StartupMode.LastProfile
            }
        viewModelScope.launch { sessions.setStartupMode(next) }
    }

    fun switchProfile() {
        sessions.leaveProfile()
        events.send(SettingsEvent.ProfileClosed)
    }

    fun signOut() {
        val profile = state.value.session?.profile ?: return
        viewModelScope.launch {
            sessions.signOut(profile)
            events.send(SettingsEvent.ProfileClosed)
        }
    }

    fun forgetServer() {
        viewModelScope.launch {
            sessions.forgetServer()
            events.send(SettingsEvent.ServerForgotten)
        }
    }
}
