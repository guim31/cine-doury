package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.model.ImageRef
import fr.guim.cinedoury.core.model.ServerInfo
import fr.guim.cinedoury.core.model.User
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.LibrarySource
import fr.guim.cinedoury.core.source.QuickConnectRequest
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.Session
import fr.guim.cinedoury.core.source.SourceConnector
import fr.guim.cinedoury.core.source.SourceException
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.authenticateWithQuickConnect
import org.jellyfin.sdk.api.client.extensions.authenticationApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.ServerVersion
import org.jellyfin.sdk.model.api.AuthenticationResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How the app introduces itself to the server. [deviceId] must stay the same for the whole life of
 * an installation: Jellyfin ties sessions and tokens to it.
 */
data class ClientIdentity(
    val appVersion: String,
    val deviceId: String,
    val deviceName: String,
)

/** [SourceConnector] for a Jellyfin 12.3 server, built on the official Kotlin SDK. */
class JellyfinConnector(
    identity: ClientIdentity,
    requestTimeout: Duration = 30.seconds,
) : SourceConnector {
    private val httpOptions = HttpClientOptions(requestTimeout = requestTimeout)

    private val jellyfin: Jellyfin =
        createJellyfin {
            // ASCII on purpose: the SDK copies the client name into User-Agent, where OkHttp rejects
            // any non-ASCII character (CLAUDE.md, « Pièges »).
            clientInfo = ClientInfo(name = CLIENT_NAME, version = identity.appVersion)
            deviceInfo = DeviceInfo(id = identity.deviceId, name = identity.deviceName)
        }

    override suspend fun serverInfo(address: ServerAddress): ServerInfo =
        jellyfinCall {
            val api = jellyfin.createApi(baseUrl = address.url, httpClientOptions = httpOptions)
            val info = api.systemApi.getPublicSystemInfo().content
            val id = info.id ?: throw SourceException.NotAServer("No server id in /System/Info/Public")
            val version = info.version ?: throw SourceException.NotAServer("No server version in /System/Info/Public")
            val parsed = ServerVersion.fromString(version)
            if (parsed == null || parsed < MINIMUM_VERSION) {
                throw SourceException.Incompatible(serverVersion = version, minimumVersion = MINIMUM_VERSION.toString())
            }
            ServerInfo(id = id, name = info.serverName.orEmpty(), version = version)
        }

    private fun api(address: ServerAddress, token: String? = null): ApiClient =
        jellyfin.createApi(baseUrl = address.url, accessToken = token, httpClientOptions = httpOptions)

    override suspend fun publicUsers(address: ServerAddress): List<User> =
        jellyfinCall(what = "public users") {
            api(address).userApi.getPublicUsers().content.map { it.toModel() }
        }

    override suspend fun quickConnectEnabled(address: ServerAddress): Boolean =
        jellyfinCall(what = "Quick Connect") {
            api(address).authenticationApi.getQuickConnectEnabled().content
        }

    override suspend fun startQuickConnect(address: ServerAddress): QuickConnectRequest =
        // Jellyfin answers 401 when Quick Connect is disabled.
        jellyfinCall(onUnauthorized = { SourceException.QuickConnectDisabled() }, what = "Quick Connect") {
            val result = api(address).authenticationApi.initiateQuickConnect().content
            QuickConnectRequest(code = result.code, secret = result.secret)
        }

    override suspend fun isQuickConnectApproved(address: ServerAddress, request: QuickConnectRequest): Boolean =
        try {
            jellyfinCall(onUnauthorized = { SourceException.QuickConnectDisabled() }, what = "Quick Connect") {
                api(address).authenticationApi.getQuickConnectState(request.secret).content.authenticated
            }
        } catch (_: SourceException.NotFound) {
            // « Unknown secret »: the server forgot the request (10 minutes) or restarted.
            throw SourceException.QuickConnectExpired()
        }

    override suspend fun signIn(address: ServerAddress, credentials: Credentials): Session {
        val server = serverInfo(address)
        val result =
            when (credentials) {
                is Credentials.Password -> {
                    jellyfinCall(onUnauthorized = { SourceException.InvalidCredentials() }) {
                        api(address)
                            .authenticationApi
                            .authenticateUserByName(credentials.username, credentials.password)
                            .content
                    }
                }

                is Credentials.QuickConnect -> {
                    try {
                        jellyfinCall(onUnauthorized = { SourceException.QuickConnectExpired() }) {
                            api(address)
                                .authenticationApi
                                .authenticateWithQuickConnect(credentials.request.secret)
                                .content
                        }
                    } catch (_: SourceException.NotFound) {
                        throw SourceException.QuickConnectExpired()
                    }
                }
            }
        return result.toSession(address, server)
    }

    private fun AuthenticationResult.toSession(address: ServerAddress, server: ServerInfo): Session {
        val token = accessToken ?: throw SourceException.NotAServer("No access token in the answer")
        val user = user ?: throw SourceException.NotAServer("No user in the answer")
        return Session(
            server = address,
            serverId = serverId ?: server.id,
            user = user.toModel(),
            accessToken = token,
        )
    }

    override suspend fun signOut(session: Session) {
        jellyfinCall(what = "session") {
            api(session.server, session.accessToken).sessionApi.reportSessionEnded()
        }
    }

    override fun open(session: Session): LibrarySource =
        JellyfinLibrarySource(api = api(session.server, session.accessToken), session = session)

    override fun imageUrl(server: ServerAddress, image: ImageRef, width: Int, height: Int): String =
        imageUrlOf(server, image, width, height)

    override fun userImageUrl(server: ServerAddress, user: User): String? =
        user.image?.let { "${server.url}/UserImage?userId=${user.id.value}&tag=${it.tag}" }

    companion object {
        const val CLIENT_NAME = "Cine Doury"

        /** `SPEC.md` § 2: Jellyfin 12.3 is the oldest supported server. */
        val MINIMUM_VERSION = ServerVersion(major = 12, minor = 3, patch = 0)
    }
}
