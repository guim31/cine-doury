package fr.guim.cinedoury.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.guim.cinedoury.core.data.ActiveSession
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.StartupMode
import fr.guim.cinedoury.core.data.prefs.AppPreferences
import fr.guim.cinedoury.core.ui.Events
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsState(
    val session: ActiveSession? = null,
    val startupMode: StartupMode = AppPreferences.DEFAULT_STARTUP,
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
) : ViewModel() {
    val state: StateFlow<SettingsState> =
        combine(sessions.active, sessions.startupMode) { session, mode -> SettingsState(session, mode) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsState(sessions.active.value))
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
