package fr.guim.cinedoury.core.data.server

import fr.guim.cinedoury.core.data.Connection
import fr.guim.cinedoury.core.data.ConnectionMode
import fr.guim.cinedoury.core.data.SavedServer
import fr.guim.cinedoury.core.source.FailureLog
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.SourceConnector
import fr.guim.cinedoury.core.source.SourceException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Chooses the address of a saved server at start (`SPEC.md` § 4.7): the local address first, with
 * a short timeout, then the remote one. An address counts only if the server that answers there is
 * the saved one (same id): on another network, the same local address may lead elsewhere.
 */
class AddressResolver(
    private val connector: SourceConnector,
    private val localTimeout: Duration = 3.seconds,
    private val remoteTimeout: Duration = 10.seconds,
) {
    /** The first address that answers, or the most telling failure if none does. */
    suspend fun resolve(server: SavedServer): Connection {
        val candidates =
            listOfNotNull(
                Triple(server.localAddress, ConnectionMode.Local, localTimeout),
                server.remoteAddress?.let { Triple(it, ConnectionMode.Remote, remoteTimeout) },
            )
        val failures = mutableListOf<SourceException>()
        for ((address, mode, timeout) in candidates) {
            when (val outcome = probe(server, address, timeout)) {
                null -> return Connection(server, address, mode)
                else -> failures += outcome
            }
        }
        throw failures.maxBy { it.weight() }
    }

    /** Null when [server] answers at [address], or why it does not. */
    private suspend fun probe(
        server: SavedServer,
        address: ServerAddress,
        timeout: Duration,
    ): SourceException? =
        try {
            val info = withTimeoutOrNull(timeout) { connector.serverInfo(address) }
            when {
                info == null -> {
                    SourceException.Unreachable().also { FailureLog.warn("No answer in $timeout at $address") }
                }

                info.id != server.id -> {
                    SourceException
                        .NotAServer("Another server (${info.name}) answers at $address")
                        .also { FailureLog.warn("Address resolver: ${it.message}") }
                }

                else -> {
                    null
                }
            }
        } catch (error: SourceException) {
            error
        }

    /** A server too old says more than a server that does not answer. */
    private fun SourceException.weight(): Int =
        when (this) {
            is SourceException.Incompatible -> 2
            is SourceException.NotAServer -> 1
            else -> 0
        }
}
