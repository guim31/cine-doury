package fr.guim.cinedoury.core.data

import fr.guim.cinedoury.core.data.prefs.AppPreferences
import fr.guim.cinedoury.core.data.server.AddressResolver
import fr.guim.cinedoury.core.model.ImageRef
import fr.guim.cinedoury.core.model.User
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.QuickConnectRequest
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.Session
import fr.guim.cinedoury.core.source.SourceConnector
import fr.guim.cinedoury.core.source.SourceException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlin.coroutines.cancellation.CancellationException

/** Where the app opens (`SPEC.md` § 4.7). */
sealed interface StartupTarget {
    /** First launch, or the server was forgotten. */
    data object NoServer : StartupTarget

    /** The saved server did not answer at any of its addresses. */
    data class Unreachable(
        val server: SavedServer,
        val error: SourceException,
    ) : StartupTarget

    data object Profiles : StartupTarget

    /** The last profile opens directly, behind its PIN. */
    data class Pin(
        val profile: Profile,
    ) : StartupTarget

    /** The last profile is open. */
    data object Home : StartupTarget
}

/**
 * The connection to the server and the open profile, for every screen. Every failure is a
 * [SourceException]. The single entry point of the screens into connection and profiles: one
 * function per gesture, hence many functions.
 */
@Suppress("TooManyFunctions")
class SessionManager(
    private val connector: SourceConnector,
    private val servers: ServerRepository,
    private val profiles: ProfileRepository,
    private val preferences: AppPreferences,
    private val resolver: AddressResolver,
    /** Forgets what is cached for a user signed out, or (null user) for a forgotten server. */
    private val forgetCache: suspend (serverId: String, userId: String?) -> Unit = { _, _ -> },
) {
    private val currentConnection = MutableStateFlow<Connection?>(null)
    private val currentSession = MutableStateFlow<ActiveSession?>(null)

    /** The server in use, at the address that answered. */
    val connection: StateFlow<Connection?> = currentConnection.asStateFlow()

    /** The open profile. */
    val active: StateFlow<ActiveSession?> = currentSession.asStateFlow()

    val startupMode: Flow<StartupMode> get() = preferences.startupMode

    suspend fun setStartupMode(mode: StartupMode) = preferences.setStartupMode(mode)

    /** Decides the first screen: reconnects to the saved server and opens the last profile if asked. */
    suspend fun start(): StartupTarget {
        val server = preferences.lastServerId()?.let { servers.get(it) } ?: return StartupTarget.NoServer
        val connection =
            try {
                resolver.resolve(server)
            } catch (error: SourceException) {
                return StartupTarget.Unreachable(server, error)
            }
        currentConnection.value = connection
        val last =
            if (preferences.startupMode.first() == StartupMode.LastProfile) {
                preferences.lastUserId()?.let { profiles.get(server.id, it) }
            } else {
                null
            }
        return when {
            last == null || !last.isSignedIn -> StartupTarget.Profiles
            last.hasPin -> StartupTarget.Pin(last)
            open(last) -> StartupTarget.Home
            else -> StartupTarget.Profiles
        }
    }

    /** Checks the server at [address] (Jellyfin 12.3 or later) and makes it the server in use. */
    suspend fun addServer(address: ServerAddress): Connection {
        val info = connector.serverInfo(address)
        val server = servers.save(info, address)
        preferences.setLastServer(server.id)
        currentSession.value = null
        return Connection(server, address, ConnectionMode.Local).also { currentConnection.value = it }
    }

    /** Changes the addresses of the server in use; the next start tries them. */
    suspend fun setAddresses(
        local: ServerAddress,
        remote: ServerAddress?,
    ) {
        val connection = requireConnection()
        servers.setAddresses(connection.server.id, local, remote)
        val updated = connection.server.copy(localAddress = local, remoteAddress = remote)
        currentConnection.value = connection.copy(server = updated)
        currentSession.value = currentSession.value?.let { it.copy(connection = it.connection.copy(server = updated)) }
    }

    /** The profiles of the server in use, as they change. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun profiles(): Flow<List<Profile>> =
        currentConnection.flatMapLatest { connection ->
            connection?.let { profiles.observe(it.server.id) } ?: emptyFlow()
        }

    /** Asks the server for its public users and brings the profiles up to date. */
    suspend fun refreshProfiles() {
        val connection = requireConnection()
        profiles.rememberPublicUsers(connection.server.id, connector.publicUsers(connection.address))
    }

    suspend fun quickConnectEnabled(): Boolean = connector.quickConnectEnabled(requireConnection().address)

    suspend fun startQuickConnect(): QuickConnectRequest = connector.startQuickConnect(requireConnection().address)

    suspend fun isQuickConnectApproved(request: QuickConnectRequest): Boolean =
        connector.isQuickConnectApproved(requireConnection().address, request)

    /** Signs in, keeps the token and opens the profile. A fresh sign-in skips the PIN. */
    suspend fun signIn(credentials: Credentials): Profile {
        val connection = requireConnection()
        val session = connector.signIn(connection.address, credentials)
        val profile = profiles.saveSession(session)
        activate(connection, profile, session)
        return profile
    }

    /** Opens a signed-in profile (its PIN already checked); false if it is not signed in any more. */
    suspend fun open(profile: Profile): Boolean {
        val connection = requireConnection()
        val session = profiles.session(profile, connection.address) ?: return false
        activate(connection, profile, session)
        return true
    }

    private suspend fun activate(
        connection: Connection,
        profile: Profile,
        session: Session,
    ) {
        preferences.setLastUser(profile.user.id.value)
        currentSession.value = ActiveSession(connection, profile, session, connector.open(session))
    }

    suspend fun checkPin(
        profile: Profile,
        pin: String,
    ): Boolean = profiles.checkPin(profile, pin)

    suspend fun setPin(
        profile: Profile,
        pin: String?,
    ) {
        profiles.setPin(profile, pin)
        refreshActive(profile)
    }

    /** Leaves the open profile, for the profile picker. */
    fun leaveProfile() {
        currentSession.value = null
    }

    /**
     * Signs [profile] out: the server ends its token (if it can be reached) and the device forgets
     * it. The other profiles stay signed in.
     */
    suspend fun signOut(profile: Profile) {
        val connection = currentConnection.value
        if (connection != null) {
            profiles.session(profile, connection.address)?.let { endQuietly(it) }
        }
        profiles.signOut(profile)
        forgetCache(profile.serverId, profile.user.id.value)
        if (currentSession.value
                ?.profile
                ?.user
                ?.id == profile.user.id
        ) {
            currentSession.value = null
        }
    }

    /** Called when the server rejects the token of the open profile: it is signed out. */
    suspend fun sessionRejected() {
        val active = currentSession.value ?: return
        profiles.signOut(active.profile)
        forgetCache(active.profile.serverId, active.profile.user.id.value)
        currentSession.value = null
    }

    /** Signs every profile out and forgets the server in use, its addresses and its profiles. */
    suspend fun forgetServer() {
        val connection = currentConnection.value ?: return
        profiles.observe(connection.server.id).first().filter { it.isSignedIn }.forEach { profile ->
            profiles.session(profile, connection.address)?.let { endQuietly(it) }
        }
        profiles.deleteAll(connection.server.id)
        forgetCache(connection.server.id, null)
        servers.delete(connection.server.id)
        preferences.setLastServer(null)
        currentSession.value = null
        currentConnection.value = null
    }

    private suspend fun endQuietly(session: Session) {
        try {
            connector.signOut(session)
        } catch (error: CancellationException) {
            throw error
        } catch (_: SourceException) {
            // Unreachable server: the token is forgotten here anyway.
        }
    }

    private suspend fun refreshActive(profile: Profile) {
        val active = currentSession.value ?: return
        if (active.profile.user.id != profile.user.id) return
        profiles.get(profile.serverId, profile.user.id.value)?.let { updated ->
            currentSession.value = active.copy(profile = updated)
        }
    }

    /** URL of [image] at [width] × [height] pixels on the server in use, or null without one. */
    fun imageUrl(
        image: ImageRef,
        width: Int,
        height: Int,
    ): String? = currentConnection.value?.let { connector.imageUrl(it.address, image, width, height) }

    /** URL of the picture of [user] on the server in use, or null. */
    fun userImageUrl(user: User): String? = currentConnection.value?.let { connector.userImageUrl(it.address, user) }

    /**
     * Checks that the saved server answers at [address] (to change its addresses): fails if nothing
     * answers there, or another server does.
     */
    suspend fun checkAddress(address: ServerAddress) {
        val server = requireConnection().server
        val info = connector.serverInfo(address)
        if (info.id != server.id) throw SourceException.NotAServer("Another server (${info.name}) answers at $address")
    }

    private fun requireConnection(): Connection = checkNotNull(currentConnection.value) { "No server in use" }
}
