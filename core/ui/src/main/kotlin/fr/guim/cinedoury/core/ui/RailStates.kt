package fr.guim.cinedoury.core.ui

import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.home.HomeRepository
import fr.guim.cinedoury.core.data.home.ProfileKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/** The rail of the open profile: its libraries as last cached, those it hid left out. */
fun HomeRepository.railState(sessions: SessionManager): Flow<RailState> {
    val active = sessions.active.value ?: return flowOf(RailState())
    val profile = ProfileKey(active.profile.serverId, active.profile.user.id.value)
    val avatar = sessions.avatarSource(active.profile.user)
    return combine(libraries(profile), railHidden(profile)) { libraries, hidden ->
        RailState(
            libraries = libraries.libraries.filterNot { it.id in hidden },
            profileName = active.profile.user.name,
            avatar = avatar,
        )
    }
}
