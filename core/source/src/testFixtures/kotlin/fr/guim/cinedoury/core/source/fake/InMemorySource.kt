package fr.guim.cinedoury.core.source.fake

import fr.guim.cinedoury.core.model.FilterOptions
import fr.guim.cinedoury.core.model.HomeExclusions
import fr.guim.cinedoury.core.model.ImageRef
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.ItemQuery
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
import java.time.Instant

/**
 * A source held in memory, for screen and logic tests: the screens are tested against it, never
 * against a real server (`SPEC.md` § 5).
 *
 * @param accounts user names and passwords; every account is a public user, unless listed in
 * [hiddenUsers].
 * @param serverAt the server that answers at an address, to simulate another server on the
 * same address.
 * @param nextUp what « À suivre » gives: the fake does not follow series.
 */
class InMemorySource(
    private val server: ServerInfo = ServerInfo(id = "in-memory", name = "En mémoire", version = "12.3.0"),
    private val accounts: Map<String, String> = mapOf("admin" to "password"),
    private val libraries: List<Library> = emptyList(),
    private val contents: Map<LibraryId, List<MediaItem>> = emptyMap(),
    private val hiddenUsers: Set<String> = emptySet(),
    private val serverAt: (ServerAddress) -> ServerInfo = { server },
    private val exclusions: HomeExclusions = HomeExclusions(),
    private val nextUp: Map<LibraryId, List<MediaItem>> = emptyMap(),
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

        override suspend fun homeExclusions(): HomeExclusions {
            checkSession()
            return exclusions
        }

        override suspend fun items(
            query: ItemQuery,
            page: PageRequest,
        ): Page<MediaItem> {
            val all = run(query)
            return Page(
                items = all.drop(page.startIndex).take(page.limit),
                startIndex = page.startIndex,
                totalCount = all.size,
            )
        }

        override suspend fun count(query: ItemQuery): Int = run(query).size

        /** The items of [query]: filters on what a [MediaItem] carries, then the sort. */
        private fun run(query: ItemQuery): List<MediaItem> {
            val matching =
                pool(query.library)
                    .filter { query.kinds.isEmpty() || it.kind in query.kinds }
                    .filter { InMemoryQueries.matches(it, query.filters) }
                    .filter { item ->
                        query.releasedBefore?.let { (item.premiereDate ?: return@filter false) <= it }
                            ?: true
                    }
            val sorted = InMemoryQueries.sort(matching, query.sort)
            return query.titleBelow?.let { below ->
                sorted.filter { InMemoryQueries.sortKey(it) < below.lowercase() }
            } ?: sorted
        }

        override suspend fun resume(
            library: LibraryId?,
            limit: Int,
        ): List<MediaItem> =
            pool(library)
                .filter { item -> item.userData?.let { !it.played && it.playbackPosition.isPositive() } == true }
                .sortedByDescending { it.userData?.lastPlayed ?: Instant.EPOCH }
                .take(limit)

        override suspend fun nextUp(
            library: LibraryId?,
            since: Instant?,
            limit: Int,
        ): List<MediaItem> {
            checkSession()
            val candidates = library?.let { nextUp[it].orEmpty() } ?: nextUp.values.flatten()
            return candidates
                .filter { item -> since == null || (item.userData?.lastPlayed ?: Instant.EPOCH) >= since }
                .take(limit)
        }

        override suspend fun latest(
            library: LibraryId,
            limit: Int,
        ): List<MediaItem> = pool(library).sortedByDescending { it.dateAdded ?: Instant.EPOCH }.take(limit)

        override suspend fun filterOptions(
            library: LibraryId,
            kinds: Set<ItemKind>,
        ): FilterOptions {
            val items = pool(library).filter { kinds.isEmpty() || it.kind in kinds }
            return FilterOptions(
                genres = items.flatMap { it.genres }.distinct().sorted(),
                years = items.mapNotNull { it.year }.distinct().sorted(),
                officialRatings = items.mapNotNull { it.officialRating }.distinct().sorted(),
            )
        }

        private fun pool(library: LibraryId?): List<MediaItem> {
            checkSession()
            return library?.let { contents[it] ?: throw SourceException.NotFound("library ${it.value}") }
                ?: contents.values.flatten()
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
