package fr.guim.cinedoury.core.source.fake

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
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.Session
import fr.guim.cinedoury.core.source.SourceConnector
import fr.guim.cinedoury.core.source.SourceException

/**
 * A source held in memory, for screen and logic tests: the screens are tested against it, never
 * against a real server (`SPEC.md` § 5).
 */
class InMemorySource(
    private val server: ServerInfo = ServerInfo(id = "in-memory", name = "En mémoire", version = "12.3.0"),
    private val accounts: Map<String, String> = mapOf("admin" to "password"),
    private val libraries: List<Library> = emptyList(),
    private val contents: Map<LibraryId, List<MediaItem>> = emptyMap(),
) : SourceConnector {
    private val items: Map<ItemId, MediaItem> = contents.values.flatten().associateBy { it.id }

    override suspend fun serverInfo(address: ServerAddress): ServerInfo = server

    override suspend fun signIn(address: ServerAddress, credentials: Credentials): Session =
        when (credentials) {
            is Credentials.Password -> {
                if (accounts[credentials.username] != credentials.password) throw SourceException.InvalidCredentials()
                Session(
                    server = address,
                    serverId = server.id,
                    user =
                        User(
                            id = UserId(credentials.username),
                            name = credentials.username,
                            isAdministrator = true,
                            hasPassword = credentials.password.isNotEmpty(),
                        ),
                    accessToken = "token-${credentials.username}",
                )
            }
        }

    override fun open(session: Session): LibrarySource = InMemoryLibrary(session)

    private inner class InMemoryLibrary(override val session: Session) : LibrarySource {
        override suspend fun libraries() = libraries

        override suspend fun items(library: LibraryId, page: PageRequest): Page<MediaItem> {
            val all = contents[library] ?: throw SourceException.NotFound("library ${library.value}")
            val sorted = all.sortedBy { (it.sortTitle ?: it.title).lowercase() }
            return Page(
                items = sorted.drop(page.startIndex).take(page.limit),
                startIndex = page.startIndex,
                totalCount = sorted.size,
            )
        }

        override suspend fun item(id: ItemId): MediaItem =
            items[id] ?: throw SourceException.NotFound("item ${id.value}")
    }
}
