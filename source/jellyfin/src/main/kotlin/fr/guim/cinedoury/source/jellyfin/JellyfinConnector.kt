package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.model.ServerInfo
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.LibrarySource
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.Session
import fr.guim.cinedoury.core.source.SourceConnector
import fr.guim.cinedoury.core.source.SourceException
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.authenticationApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.ServerVersion
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

    override suspend fun signIn(address: ServerAddress, credentials: Credentials): Session {
        val server = serverInfo(address)
        return when (credentials) {
            is Credentials.Password -> {
                jellyfinCall(onUnauthorized = { SourceException.InvalidCredentials() }) {
                    val api = jellyfin.createApi(baseUrl = address.url, httpClientOptions = httpOptions)
                    val result =
                        api.authenticationApi
                            .authenticateUserByName(credentials.username, credentials.password)
                            .content
                    val token = result.accessToken ?: throw SourceException.NotAServer("No access token in the answer")
                    val user = result.user ?: throw SourceException.NotAServer("No user in the answer")
                    Session(
                        server = address,
                        serverId = result.serverId ?: server.id,
                        user = user.toModel(),
                        accessToken = token,
                    )
                }
            }
        }
    }

    override fun open(session: Session): LibrarySource =
        JellyfinLibrarySource(
            api =
                jellyfin.createApi(
                    baseUrl = session.server.url,
                    accessToken = session.accessToken,
                    httpClientOptions = httpOptions,
                ),
            session = session,
        )

    companion object {
        const val CLIENT_NAME = "Cine Doury"

        /** `SPEC.md` § 2: Jellyfin 12.3 is the oldest supported server. */
        val MINIMUM_VERSION = ServerVersion(major = 12, minor = 3, patch = 0)
    }
}
