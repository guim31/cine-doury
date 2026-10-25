package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.FailureLog
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.SourceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import java.io.FilterInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The sign-in of the real-device trial of 25/10/2026, replayed on the JVM: answers recorded from
 * the CI server with a hidden user, a picture, a full policy and preferences
 * (`src/test/resources/fixtures`), sent in chunks and slowly as Kestrel does, to a connector called
 * from a « main » thread where, as on Android, reading the network throws.
 */
class RealServerAnswersTest {
    private val server = MockWebServer()
    private val mainExecutor =
        Executors.newSingleThreadExecutor { task -> Thread(task, MAIN).also { mainThread = it } }
    private val main = mainExecutor.asCoroutineDispatcher()
    private val logged = mutableListOf<String>()
    private val connector =
        JellyfinConnector(
            ClientIdentity(appVersion = "0.1.0", deviceId = "0123456789abcdef0123456789abcdef", deviceName = "Chambre"),
            requestTimeout = 10.seconds,
            http = OkHttpClient.Builder().socketFactory(MainThreadGuard).build(),
            io = Dispatchers.IO,
        )
    private val address by lazy { ServerAddress.of(server.url("/").toString()) }

    @BeforeTest
    fun start() {
        server.start()
        FailureLog.sink = FailureLog.Sink { _, message, _ -> synchronized(logged) { logged += message } }
    }

    @AfterTest
    fun stop() {
        server.close()
        mainExecutor.shutdownNow()
        FailureLog.sink = FailureLog.Sink { _, _, _ -> }
    }

    @Test
    fun `a hidden user signs in with a password from the main thread`() =
        onMain {
            server.enqueue(chunked("system-info-public.json"))
            server.enqueue(chunked("authenticate-hidden-user.json"))

            val session = connector.signIn(address, Credentials.Password("parent", "cinedoury-parent"))

            assertEquals("parent", session.user.name)
            assertEquals(false, session.user.isAdministrator)
            assertEquals("00000000000000000000000000000001", session.accessToken)
            assertEquals("c341d58b9b0563aad8e90a19d550eac6", session.user.image?.tag)
        }

    @Test
    fun `Quick Connect from the main thread, up to the session`() =
        onMain {
            server.enqueue(chunked("quick-connect-enabled.json"))
            server.enqueue(chunked("quick-connect-initiate-hidden-user.json"))
            server.enqueue(chunked("quick-connect-waiting.json"))
            server.enqueue(chunked("quick-connect-approved.json"))
            server.enqueue(chunked("system-info-public.json"))
            server.enqueue(chunked("quick-connect-authenticate.json"))

            assertTrue(connector.quickConnectEnabled(address))
            val request = connector.startQuickConnect(address)
            assertEquals(6, request.code.length)
            assertEquals(false, connector.isQuickConnectApproved(address, request))
            assertEquals(true, connector.isQuickConnectApproved(address, request))
            val session = connector.signIn(address, Credentials.QuickConnect(request))

            assertEquals("parent", session.user.name)
            assertTrue(Thread.currentThread() === mainThread, "the caller stays on its thread")
        }

    @Test
    fun `a failure names its request and status, and is logged`() =
        onMain {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(400)
                    .body("Bad request")
                    .build(),
            )

            val error = assertFailsWith<SourceException.Unexpected> { connector.startQuickConnect(address) }

            assertEquals("POST /QuickConnect/Initiate", error.request)
            assertEquals(400, error.httpStatus)
            assertEquals(listOf("Jellyfin: Unexpected on POST /QuickConnect/Initiate (HTTP 400)"), logged)
        }

    @Test
    fun `an answer that cannot be read names the request with status 200`() =
        onMain {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body("{\"Authenticated\":")
                    .build(),
            )

            val error = assertFailsWith<SourceException.NotAServer> { connector.startQuickConnect(address) }

            assertEquals("POST /QuickConnect/Initiate", error.request)
            assertEquals(200, error.httpStatus)
        }

    private fun onMain(block: suspend () -> Unit) = runBlocking { withContext(main) { block() } }

    private fun chunked(name: String) =
        MockResponse
            .Builder()
            .code(200)
            .addHeader("Content-Type", "application/json; charset=utf-8")
            // Kestrel answers in chunks; a slow body never comes with the headers.
            .chunkedBody(fixture(name), maxChunkSize = CHUNK)
            .throttleBody(CHUNK.toLong(), 20, TimeUnit.MILLISECONDS)
            .build()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/fixtures/$name")) { "missing fixture $name" }.readText()

    /** What `NetworkOnMainThreadException` is on Android. */
    class NetworkOnMainThread : RuntimeException("Network read on the main thread")

    /** Sockets whose reads fail on the [MAIN] thread, as Android's StrictMode makes them. */
    private object MainThreadGuard : SocketFactory() {
        override fun createSocket(): Socket = GuardedSocket()

        override fun createSocket(
            host: String,
            port: Int,
        ): Socket = unsupported()

        override fun createSocket(
            host: String,
            port: Int,
            localHost: InetAddress,
            localPort: Int,
        ): Socket = unsupported()

        override fun createSocket(
            host: InetAddress,
            port: Int,
        ): Socket = unsupported()

        override fun createSocket(
            address: InetAddress,
            port: Int,
            localAddress: InetAddress,
            localPort: Int,
        ): Socket = unsupported()

        private fun unsupported(): Nothing = throw UnsupportedOperationException("OkHttp connects itself")
    }

    private class GuardedSocket : Socket() {
        override fun getInputStream(): InputStream =
            object : FilterInputStream(super.getInputStream()) {
                override fun read(): Int {
                    guard()
                    return super.read()
                }

                override fun read(
                    buffer: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int {
                    guard()
                    return super.read(buffer, offset, length)
                }
            }

        private fun guard() {
            if (Thread.currentThread() === mainThread) throw NetworkOnMainThread()
        }
    }

    private companion object {
        const val MAIN = "main"

        /** The thread of the « main » dispatcher (thread names change in coroutine debug mode). */
        @Volatile
        var mainThread: Thread? = null
        const val CHUNK = 512
    }
}
