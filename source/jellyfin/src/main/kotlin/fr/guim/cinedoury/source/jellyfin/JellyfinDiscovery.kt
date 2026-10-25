package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.source.DiscoveredServer
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.ServerDiscovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Jellyfin's local discovery (`SPEC.md` § 4.6): « Who is JellyfinServer? » broadcast on UDP 7359;
 * each server answers with a JSON object (`Address`, `Id`, `Name`) to the sender. The question is
 * sent a few times, since UDP may lose it, and each server is emitted once, as soon as it answers.
 *
 * @param targets where the question goes: the broadcast address of every network interface, and
 * the global broadcast address. Tests point it at a local responder.
 */
class JellyfinDiscovery(
    private val timeout: Duration = 3.seconds,
    private val targets: () -> List<InetSocketAddress> = ::broadcastTargets,
) : ServerDiscovery {
    override fun discover(): Flow<DiscoveredServer> =
        flow {
            // No network, no socket: no server, never an error.
            val opened =
                try {
                    DatagramSocket()
                } catch (_: IOException) {
                    return@flow
                }
            opened.use { socket ->
                socket.broadcast = true
                socket.soTimeout = RECEIVE_SLICE.inWholeMilliseconds.toInt()
                val question = QUESTION.toByteArray(Charsets.US_ASCII)
                val destinations = targets()
                val start = TimeSource.Monotonic.markNow()
                var sent = 0
                val seen = mutableSetOf<String>()
                val buffer = ByteArray(BUFFER_SIZE)
                while (start.elapsedNow() < timeout) {
                    currentCoroutineContext().ensureActive()
                    if (sent < SENDS && start.elapsedNow() >= RESEND_EVERY * sent) {
                        destinations.forEach { target ->
                            // An interface may refuse broadcasts: the others still get the question.
                            runCatching { socket.send(DatagramPacket(question, question.size, target)) }
                        }
                        sent++
                    }
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    } catch (_: IOException) {
                        continue
                    }
                    val answer = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                    val server = parseAnswer(answer, packet.address) ?: continue
                    if (seen.add(server.id)) emit(server)
                }
            }
        }.flowOn(Dispatchers.IO)

    companion object {
        const val PORT = 7359
        const val QUESTION = "Who is JellyfinServer?"
        private const val BUFFER_SIZE = 4096
        private const val SENDS = 3
        private const val DEFAULT_HTTP_PORT = 8096
        private val RESEND_EVERY = 1.seconds
        private val RECEIVE_SLICE = 200.milliseconds
        private val json = Json { ignoreUnknownKeys = true }

        /** A server's answer, or null if it is not one. Without `Address`, the sender's address. */
        internal fun parseAnswer(
            answer: String,
            sender: InetAddress,
        ): DiscoveredServer? {
            val fields =
                try {
                    json.parseToJsonElement(answer).jsonObject
                } catch (_: IllegalArgumentException) {
                    return null
                }
            val id = fields.text("Id") ?: return null
            val address =
                fields.text("Address")?.let { runCatching { ServerAddress.of(it) }.getOrNull() }
                    ?: ServerAddress.of("http://${sender.hostAddress}:$DEFAULT_HTTP_PORT")
            return DiscoveredServer(id = id, name = fields.text("Name") ?: id, address = address)
        }

        private fun JsonObject.text(key: String): String? =
            runCatching { get(key)?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() && it != "null" }

        /** The broadcast address of each active interface, plus 255.255.255.255. */
        fun broadcastTargets(): List<InetSocketAddress> {
            val interfaces =
                try {
                    NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                } catch (_: IOException) {
                    emptyList()
                }
            val broadcasts =
                interfaces
                    .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
                    .flatMap { it.interfaceAddresses.mapNotNull { address -> address.broadcast } }
            return (broadcasts + InetAddress.getByName("255.255.255.255"))
                .distinct()
                .map { InetSocketAddress(it, PORT) }
        }
    }
}
