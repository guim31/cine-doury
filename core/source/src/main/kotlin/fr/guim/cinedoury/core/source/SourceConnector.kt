package fr.guim.cinedoury.core.source

import fr.guim.cinedoury.core.model.ServerInfo
import fr.guim.cinedoury.core.model.User

/**
 * Entry point of a source: identifies a server, signs a user in, and opens a [LibrarySource] for a
 * session. Every failure is a [SourceException].
 */
interface SourceConnector {
    /** Public information of the server, readable without signing in. */
    suspend fun serverInfo(address: ServerAddress): ServerInfo

    suspend fun signIn(address: ServerAddress, credentials: Credentials): Session

    fun open(session: Session): LibrarySource
}

/** Base URL of a server, such as `http://192.0.2.10:8096`, without a trailing slash. */
@JvmInline
value class ServerAddress private constructor(val url: String) {
    override fun toString(): String = url

    companion object {
        fun of(url: String): ServerAddress {
            val trimmed = url.trim().trimEnd('/')
            require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                "A server address starts with http:// or https://"
            }
            return ServerAddress(trimmed)
        }
    }
}

sealed interface Credentials {
    data class Password(val username: String, val password: String) : Credentials {
        override fun toString(): String = "Password(username=$username, password=***)"
    }
}

/** A signed-in user on a server. The [accessToken] never leaves the source layer in logs. */
data class Session(
    val server: ServerAddress,
    val serverId: String,
    val user: User,
    val accessToken: String,
) {
    override fun toString(): String = "Session(server=$server, serverId=$serverId, user=${user.name}, accessToken=***)"
}
