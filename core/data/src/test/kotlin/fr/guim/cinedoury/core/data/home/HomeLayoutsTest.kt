package fr.guim.cinedoury.core.data.home

import fr.guim.cinedoury.core.model.LibraryId
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeLayoutsTest {
    private val rows = HomeLayouts.DEFAULT

    private fun ids(rows: List<HomeRowConfig>) = rows.map { it.id }

    @Test
    fun `the default home has the four rows of the 1_0`() {
        assertEquals(
            listOf(HomeRowType.Resume, HomeRowType.NextUp, HomeRowType.Latest, HomeRowType.RecentlyReleased),
            rows.map { it.type },
        )
        assertEquals(14, rows.single { it.type == HomeRowType.NextUp }.windowDays)
        assertEquals(CardStyle.Thumbnail, rows.first().style)
    }

    @Test
    fun `moves a row up and down, not past the ends`() {
        assertEquals(listOf("next-up", "resume", "latest", "released"), ids(HomeLayouts.moveUp(rows, "next-up")))
        assertEquals(listOf("resume", "next-up", "released", "latest"), ids(HomeLayouts.moveDown(rows, "latest")))
        assertEquals(rows, HomeLayouts.moveUp(rows, "resume"))
        assertEquals(rows, HomeLayouts.moveDown(rows, "released"))
        assertEquals(rows, HomeLayouts.moveDown(rows, "unknown"))
    }

    @Test
    fun `removes and adds rows, each with its own id`() {
        val removed = HomeLayouts.remove(rows, "resume")
        assertEquals(listOf("next-up", "latest", "released"), ids(removed))
        val added = HomeLayouts.add(HomeLayouts.add(removed, HomeRowType.Resume), HomeRowType.Resume)
        assertEquals(listOf("next-up", "latest", "released", "resume", "resume-2"), ids(added))
    }

    @Test
    fun `chooses the libraries of a row, all of them meaning none chosen`() {
        val all = listOf(LibraryId("a"), LibraryId("b"))
        val latest = rows.single { it.type == HomeRowType.Latest }
        val onlyB = HomeLayouts.toggleLibrary(latest, LibraryId("a"), all)
        assertEquals(setOf(LibraryId("b")), onlyB.libraries)
        assertEquals(emptySet(), HomeLayouts.toggleLibrary(onlyB, LibraryId("a"), all).libraries)
        // Removing the last one goes back to all of them rather than an empty row.
        assertEquals(emptySet(), HomeLayouts.toggleLibrary(onlyB, LibraryId("b"), all).libraries)
    }

    @Test
    fun `toggles the card style and cycles the next up window`() {
        val nextUp = rows.single { it.type == HomeRowType.NextUp }
        assertEquals(CardStyle.Poster, HomeLayouts.toggleStyle(nextUp).style)
        val windows = generateSequence(nextUp) { HomeLayouts.nextWindow(it) }.take(6).map { it.windowDays }.toList()
        assertEquals(listOf(14, 30, 90, null, 7, 14), windows)
    }
}
