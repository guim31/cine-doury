package fr.guim.cinedoury.core.source

import fr.guim.cinedoury.core.model.ImageRef
import fr.guim.cinedoury.core.model.ServerInfo
import fr.guim.cinedoury.core.model.User

/**
 * Entry point of a source: identifies a server, signs a user in, and opens a [LibrarySource] for a
 * session. Every failure is a [SourceException].
 */
interface SourceConnector {
    /** Public information of the server, readable without signing in. */
    suspend fun serverInfo(address: ServerAddress): ServerInfo

    /** The users the server shows on a sign-in screen, readable without signing in. */
    suspend fun publicUsers(address: ServerAddress): List<User>

    /** Whether the server accepts Quick Connect. */
    suspend fun quickConnectEnabled(address: ServerAddress): Boolean

    /**
     * Starts a Quick Connect request: the [QuickConnectRequest.code] is shown on the TV and typed
     * on a device where the user is signed in. Throws [SourceException.QuickConnectDisabled].
     */
    suspend fun startQuickConnect(address: ServerAddress): QuickConnectRequest

    /**
     * Whether [request] has been approved; then sign in with [Credentials.QuickConnect]. Throws
     * [SourceException.QuickConnectExpired] once the server has forgotten the request.
     */
    suspend fun isQuickConnectApproved(address: ServerAddress, request: QuickConnectRequest): Boolean

    suspend fun signIn(address: ServerAddress, credentials: Credentials): Session

    /** Ends [session] on the server: its token stops working. */
    suspend fun signOut(session: Session)

    fun open(session: Session): LibrarySource

    /**
     * URL of [image], resized by the server to fill [width] × [height] pixels (`SPEC.md` § 4.8).
     * Images are readable without a token.
     */
    fun imageUrl(server: ServerAddress, image: ImageRef, width: Int, height: Int): String

    /** URL of the picture of [user], or null when the user has none. */
    fun userImageUrl(server: ServerAddress, user: User): String?

    /**
     * The name this device shows on the server, from the next request on, for every session (« Nom de
     * l'appareil », `SPEC.md` § 4.17).
     */
    fun setDeviceName(name: String)
}

/**
 * A pending Quick Connect request. [code] is what the user types on the other device; [secret]
 * identifies the request and never leaves the source layer in logs.
 */
data class QuickConnectRequest(
    val code: String,
    val secret: String,
) {
    override fun toString(): String = "QuickConnectRequest(code=$code, secret=***)"
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

    /** An approved Quick Connect request (see [SourceConnector.isQuickConnectApproved]). */
    data class QuickConnect(val request: QuickConnectRequest) : Credentials
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
