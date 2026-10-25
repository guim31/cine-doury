package fr.guim.cinedoury.core.data

import fr.guim.cinedoury.core.model.User
import fr.guim.cinedoury.core.source.LibrarySource
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.Session

/**
 * A server remembered on this device, with its two addresses (`SPEC.md` § 4.7): the local one,
 * tried first, and an optional remote one (`https://…`), tried when the local one does not answer.
 */
data class SavedServer(
    val id: String,
    val name: String,
    val version: String,
    val localAddress: ServerAddress,
    val remoteAddress: ServerAddress? = null,
)

/**
 * A profile of a server as this device knows it: a public user of the server, or a user who signed
 * in here. [isSignedIn] means that a token is kept for it; [hasPin] that it is locked by a PIN of
 * this device.
 */
data class Profile(
    val serverId: String,
    val user: User,
    val isSignedIn: Boolean,
    val hasPin: Boolean,
)

/** What the app does at start once a server is known (`FONCTIONNALITES.md` § 1). */
enum class StartupMode {
    /** Opens the last profile directly (behind its PIN, if any). */
    LastProfile,

    /** Always shows the profile picker. */
    AlwaysAsk,
}

enum class ConnectionMode {
    Local,

    /** Through the remote address: shown by a discreet indicator. */
    Remote,
}

/** The saved server, reached at [address]. */
data class Connection(
    val server: SavedServer,
    val address: ServerAddress,
    val mode: ConnectionMode,
)

/** The open profile: what every screen after the profile picker works with. */
data class ActiveSession(
    val connection: Connection,
    val profile: Profile,
    val session: Session,
    val library: LibrarySource,
)
