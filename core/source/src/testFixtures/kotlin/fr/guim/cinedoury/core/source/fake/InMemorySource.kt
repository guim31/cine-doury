package fr.guim.cinedoury.core.source.fake

import fr.guim.cinedoury.core.model.ImageRef
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.Page
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.model.ServerInfo
import fr.guim.cinedoury.core.model.User
import fr.guim.cinedoury.core.model.UserId
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.LibrarySource
import fr.guim.cinedoury.core.source.QuickConnectRequest
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.Session
import fr.guim.cinedoury.core.source.SourceConnector
import fr.guim.cinedoury.core.source.SourceException

/**
 * A source held in memory, for screen and logic tests: the screens are tested against it, never
 * against a real server (`SPEC.md` § 5).
 *
 * @param accounts user names and passwords; every account is a public user, unless listed in
 * [hiddenUsers].
 * @param serverAt the server that answers at an address, to simulate another server on the
 * same address.
 */
class InMemorySource(
    private val server: ServerInfo = ServerInfo(id = "in-memory", name = "En mémoire", version = "12.3.0"),
    private val accounts: Map<String, String> = mapOf("admin" to "password"),
    private val libraries: List<Library> = emptyList(),
    private val contents: Map<LibraryId, List<MediaItem>> = emptyMap(),
    private val hiddenUsers: Set<String> = emptySet(),
    private val serverAt: (ServerAddress) -> ServerInfo = { server },
) : SourceConnector {
    private val items: Map<ItemId, MediaItem> = contents.values.flatten().associateBy { it.id }

    /** Addresses that do not answer. */
    val unreachable: MutableSet<ServerAddress> = mutableSetOf()

    var quickConnectEnabled: Boolean = true

    /** Tokens ended by [signOut]. */
    val revokedTokens: MutableSet<String> = mutableSetOf()

    private var nextCode = FIRST_CODE
    private val pending = mutableMapOf<String, String?>()

    fun user(name: String): User =
        User(id = UserId("id-$name"), name = name, isAdministrator = name == "admin", hasPassword = true)

    /** Simulates the user who types [code] on a phone where [username] is signed in. */
    fun approveQuickConnect(code: String, username: String) {
        val secret = pending.keys.single { it.removePrefix("secret-") == code }
        pending[secret] = username
    }

    /** Simulates the server forgetting every pending Quick Connect request. */
    fun expireQuickConnect() {
        pending.clear()
    }

    private fun check(address: ServerAddress) {
        if (address in unreachable) throw SourceException.Unreachable()
    }

    override suspend fun serverInfo(address: ServerAddress): ServerInfo {
        check(address)
        return serverAt(address)
    }

    override suspend fun publicUsers(address: ServerAddress): List<User> {
        check(address)
        return accounts.keys.filterNot { it in hiddenUsers }.map(::user)
    }

    override suspend fun quickConnectEnabled(address: ServerAddress): Boolean {
        check(address)
        return quickConnectEnabled
    }

    override suspend fun startQuickConnect(address: ServerAddress): QuickConnectRequest {
        check(address)
        if (!quickConnectEnabled) throw SourceException.QuickConnectDisabled()
        val code = (nextCode++).toString()
        val secret = "secret-$code"
        pending[secret] = null
        return QuickConnectRequest(code = code, secret = secret)
    }

    override suspend fun isQuickConnectApproved(address: ServerAddress, request: QuickConnectRequest): Boolean {
        check(address)
        if (request.secret !in pending) throw SourceException.QuickConnectExpired()
        return pending[request.secret] != null
    }

    override suspend fun signIn(address: ServerAddress, credentials: Credentials): Session {
        check(address)
        val username =
            when (credentials) {
                is Credentials.Password -> {
                    if (accounts[credentials.username] != credentials.password) {
                        throw SourceException.InvalidCredentials()
                    }
                    credentials.username
                }

                is Credentials.QuickConnect -> {
                    pending.remove(credentials.request.secret) ?: throw SourceException.QuickConnectExpired()
                }
            }
        return Session(
            server = address,
            serverId = serverAt(address).id,
            user = user(username),
            accessToken = "token-$username-${nextToken++}",
        )
    }

    private var nextToken = 1

    override suspend fun signOut(session: Session) {
        revokedTokens += session.accessToken
    }

    override fun open(session: Session): LibrarySource = InMemoryLibrary(session)

    override fun imageUrl(server: ServerAddress, image: ImageRef, width: Int, height: Int): String =
        "$server/image/${image.itemId.value}/${image.type}?w=$width&h=$height&tag=${image.tag}"

    override fun userImageUrl(server: ServerAddress, user: User): String? =
        user.image?.let { "$server/user/${user.id.value}?tag=${it.tag}" }

    private inner class InMemoryLibrary(override val session: Session) : LibrarySource {
        private fun checkSession() {
            check(session.server)
            if (session.accessToken in revokedTokens) throw SourceException.Unauthorized()
        }

        override suspend fun libraries(): List<Library> {
            checkSession()
            return libraries
        }

        override suspend fun items(library: LibraryId, page: PageRequest): Page<MediaItem> {
            checkSession()
            val all = contents[library] ?: throw SourceException.NotFound("library ${library.value}")
            val sorted = all.sortedBy { (it.sortTitle ?: it.title).lowercase() }
            return Page(
                items = sorted.drop(page.startIndex).take(page.limit),
                startIndex = page.startIndex,
                totalCount = sorted.size,
            )
        }

        override suspend fun item(id: ItemId): MediaItem {
            checkSession()
            return items[id] ?: throw SourceException.NotFound("item ${id.value}")
        }
    }

    private companion object {
        const val FIRST_CODE = 482_913
    }
}
