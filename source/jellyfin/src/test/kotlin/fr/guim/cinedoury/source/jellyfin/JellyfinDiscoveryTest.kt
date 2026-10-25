package fr.guim.cinedoury.source.jellyfin

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

class JellyfinDiscoveryTest {
    private val loopback = InetAddress.getLoopbackAddress()

    /** A local stand-in for Jellyfin's discovery port, answering [answers] to every question. */
    private fun responder(vararg answers: String): DatagramSocket {
        val socket = DatagramSocket(0, loopback)
        thread(isDaemon = true) {
            val buffer = ByteArray(256)
            while (!socket.isClosed) {
                val question = DatagramPacket(buffer, buffer.size)
                runCatching { socket.receive(question) }.onFailure { return@thread }
                if (String(question.data, 0, question.length) != JellyfinDiscovery.QUESTION) continue
                answers.forEach { answer ->
                    val bytes = answer.toByteArray()
                    socket.send(DatagramPacket(bytes, bytes.size, question.socketAddress))
                }
            }
        }
        return socket
    }

    @Test
    fun `each server once, as it answers`() {
        responder(
            """{"Address":"http://192.0.2.10:8096","Id":"a1","Name":"Salon","EndpointAddress":null}""",
            """{"Address":"http://192.0.2.11:8096","Id":"b2","Name":"Bureau"}""",
            "not json",
        ).use { socket ->
            val discovery =
                JellyfinDiscovery(timeout = 1_500.milliseconds) {
                    listOf(InetSocketAddress(loopback, socket.localPort))
                }
            val servers = runBlocking { discovery.discover().toList() }

            assertEquals(listOf("Salon", "Bureau"), servers.map { it.name })
            assertEquals("http://192.0.2.10:8096", servers.first().address.url)
        }
    }

    @Test
    fun `no server, no error`() {
        val discovery = JellyfinDiscovery(timeout = 300.milliseconds) { listOf(InetSocketAddress(loopback, 9)) }
        assertEquals(emptyList(), runBlocking { discovery.discover().toList() })
    }

    @Test
    fun `answer without an address uses the sender and the default port`() {
        val server =
            JellyfinDiscovery.parseAnswer(
                """{"Id":"a1","Name":"Salon"}""",
                InetAddress.getByName("192.0.2.12"),
            )
        assertEquals("http://192.0.2.12:8096", server?.address?.url)
        assertNull(JellyfinDiscovery.parseAnswer("""{"Name":"no id"}""", loopback))
    }
}
