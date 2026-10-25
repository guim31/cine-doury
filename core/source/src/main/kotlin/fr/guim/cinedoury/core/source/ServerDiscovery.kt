package fr.guim.cinedoury.core.source

import kotlinx.coroutines.flow.Flow

/** Finds the servers of the local network. */
fun interface ServerDiscovery {
    /**
     * The servers that answer, each once, as they answer; the flow completes after a few seconds.
     * Never fails: a network without servers gives an empty flow.
     */
    fun discover(): Flow<DiscoveredServer>
}

/** A server found on the local network, before any check. */
data class DiscoveredServer(
    val id: String,
    val name: String,
    val address: ServerAddress,
)
