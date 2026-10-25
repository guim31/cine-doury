package fr.guim.cinedoury.core.ui

import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.home.HomeRepository
import fr.guim.cinedoury.core.data.home.ProfileKey
import fr.guim.cinedoury.core.data.library.LibraryIconRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/** The rail of the open profile: its libraries as last cached, those it hid left out, their icons. */
fun HomeRepository.railState(
    sessions: SessionManager,
    icons: LibraryIconRepository,
): Flow<RailState> {
    val active = sessions.active.value ?: return flowOf(RailState())
    val profile = ProfileKey(active.profile.serverId, active.profile.user.id.value)
    val avatar = sessions.avatarSource(active.profile.user)
    return combine(
        libraries(profile),
        railHidden(profile),
        icons.chosen(profile.serverId),
    ) { libraries, hidden, chosen ->
        RailState(
            libraries = libraries.libraries.filterNot { it.id in hidden },
            profileName = active.profile.user.name,
            avatar = avatar,
            icons = chosen,
        )
    }
}
