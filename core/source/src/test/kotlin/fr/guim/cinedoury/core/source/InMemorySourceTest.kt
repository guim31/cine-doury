package fr.guim.cinedoury.core.source

import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.LibraryKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.source.fake.InMemorySource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InMemorySourceTest {
    private val films = LibraryId("films")
    private val source =
        InMemorySource(
            libraries = listOf(Library(films, "Films", LibraryKind.Movies)),
            contents =
                mapOf(
                    films to
                        listOf("Nuit polaire", "Le Phare", "Double jeu").map {
                            MediaItem(ItemId(it), ItemKind.Movie, it)
                        },
                ),
        )
    private val address = ServerAddress.of("http://localhost:8096/")

    @Test
    fun `address is normalised`() {
        assertEquals("http://localhost:8096", address.url)
        assertFailsWith<IllegalArgumentException> { ServerAddress.of("localhost:8096") }
    }

    @Test
    fun `wrong password is a typed error`() =
        runTest {
            assertFailsWith<SourceException.InvalidCredentials> {
                source.signIn(address, Credentials.Password("admin", "nope"))
            }
        }

    @Test
    fun `items are paged in sort order`() =
        runTest {
            val library = source.open(source.signIn(address, Credentials.Password("admin", "password")))
            val first = library.items(films, PageRequest(limit = 2))
            assertEquals(listOf("Double jeu", "Le Phare"), first.items.map { it.title })
            assertEquals(3, first.totalCount)
            val second = library.items(films, PageRequest(startIndex = first.nextStartIndex!!, limit = 2))
            assertEquals(listOf("Nuit polaire"), second.items.map { it.title })
            assertFailsWith<SourceException.NotFound> { library.item(ItemId("absent")) }
        }

    @Test
    fun `secrets stay out of toString`() {
        val text =
            Credentials.Password("admin", "hunter2").toString() +
                Session(address, "id", sampleUser(), "secret-token").toString()
        assert("hunter2" !in text && "secret-token" !in text)
    }

    private fun sampleUser() =
        fr.guim.cinedoury.core.model.User(
            id =
                fr.guim.cinedoury.core.model
                    .UserId("u"),
            name = "admin",
            isAdministrator = true,
            hasPassword = true,
        )
}
