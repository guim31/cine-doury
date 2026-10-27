package fr.guim.cinedoury.core.data

import fr.guim.cinedoury.core.data.prefs.NetworkMode
import fr.guim.cinedoury.core.data.server.AddressResolver
import fr.guim.cinedoury.core.model.ServerInfo
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.source.fake.InMemorySource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AddressResolverTest {
    private val local = ServerAddress.of("http://192.0.2.10:8096")
    private val remote = ServerAddress.of("https://example.com")
    private val home = ServerInfo(id = "home", name = "Maison", version = "12.3.0")
    private val saved = SavedServer("home", "Maison", "12.3.0", local, remote)

    @Test
    fun `local address first`() =
        runTest {
            val connection = AddressResolver(InMemorySource(server = home)).resolve(saved)
            assertEquals(local, connection.address)
            assertEquals(ConnectionMode.Local, connection.mode)
        }

    @Test
    fun `remote address when the local one does not answer`() =
        runTest {
            val source = InMemorySource(server = home).apply { unreachable += local }
            val connection = AddressResolver(source).resolve(saved)
            assertEquals(remote, connection.address)
            assertEquals(ConnectionMode.Remote, connection.mode)
        }

    @Test
    fun `another server on the local address does not count`() =
        runTest {
            val elsewhere = ServerInfo(id = "neighbour", name = "Voisin", version = "12.3.0")
            val source = InMemorySource(server = home, serverAt = { if (it == local) elsewhere else home })
            assertEquals(remote, AddressResolver(source).resolve(saved).address)
        }

    @Test
    fun `nothing answers`() =
        runTest {
            val source = InMemorySource(server = home).apply { unreachable += setOf(local, remote) }
            assertFailsWith<SourceException.Unreachable> { AddressResolver(source).resolve(saved) }
            assertFailsWith<SourceException.Unreachable> {
                AddressResolver(source).resolve(saved.copy(remoteAddress = null))
            }
        }

    @Test
    fun `a server too old says more than silence`() =
        runTest {
            val old =
                InMemorySource(server = home.copy(version = "10.11.2"), serverAt = {
                    throw SourceException.Incompatible("10.11.2", "12.3.0")
                })
            assertFailsWith<SourceException.Incompatible> { AddressResolver(old).resolve(saved) }
        }

    @Test
    fun `local only never goes out through the remote address`() =
        runTest {
            val source = InMemorySource(server = home).apply { unreachable += local }
            assertFailsWith<SourceException.Unreachable> {
                AddressResolver(source).resolve(saved, NetworkMode.LocalOnly)
            }
            val connection = AddressResolver(InMemorySource(server = home)).resolve(saved, NetworkMode.LocalOnly)
            assertEquals(local, connection.address)
        }

    @Test
    fun `remote only skips the local address`() =
        runTest {
            val connection = AddressResolver(InMemorySource(server = home)).resolve(saved, NetworkMode.RemoteOnly)
            assertEquals(remote, connection.address)
            assertEquals(ConnectionMode.Remote, connection.mode)
            val noRemote =
                AddressResolver(
                    InMemorySource(server = home),
                ).resolve(saved.copy(remoteAddress = null), NetworkMode.RemoteOnly)
            assertEquals(local, noRemote.address)
        }
}
