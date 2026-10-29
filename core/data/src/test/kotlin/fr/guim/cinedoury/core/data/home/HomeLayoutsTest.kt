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

    @Test
    fun `under the mosaic, resume and next up are one row continue where the first stood`() {
        val layout = HomeLayouts.moveDown(HomeLayouts.moveDown(rows, "next-up"), "resume")
        // latest, resume, released, next-up: « Continuer » takes the place of « Reprendre ».
        val view = HomeLayouts.withContinue(layout)
        assertEquals(listOf("latest", "continue", "released"), ids(view))
        val row = view[1]
        assertEquals(HomeRowType.Continue, row.type)
        assertEquals(CardStyle.Thumbnail, row.style)
        assertEquals(14, row.windowDays)
        assertEquals(emptySet(), row.libraries)
        // Without either row, no « Continuer ».
        assertEquals(listOf("latest", "released"), ids(HomeLayouts.withContinue(rows.drop(2))))
    }

    @Test
    fun `continue carries what was chosen for its two rows`() {
        val films = LibraryId("films")
        val series = LibraryId("series")
        val layout =
            listOf(
                HomeRowConfig("next-up", HomeRowType.NextUp, setOf(series), CardStyle.Poster, windowDays = 30),
                HomeRowConfig("resume", HomeRowType.Resume, setOf(films)),
            )
        val row = HomeLayouts.withContinue(layout).single()
        assertEquals(setOf(films, series), row.libraries)
        assertEquals(CardStyle.Poster, row.style, "the cards of the first")
        assertEquals(30, row.windowDays)
        // One of them over all libraries: « Continuer » too.
        val all = HomeLayouts.withContinue(listOf(layout[0], layout[1].copy(libraries = emptySet()))).single()
        assertEquals(emptySet(), all.libraries)
    }

    @Test
    fun `an edit of continue goes back to its two rows, the rest as stored`() {
        val view = HomeLayouts.withContinue(rows)
        // Unchanged, the two rows come back exactly as stored.
        assertEquals(rows, HomeLayouts.withoutContinue(view, rows))
        // Moved down, with posters: both rows, where « Continuer » now stands.
        val moved = HomeLayouts.update(HomeLayouts.moveDown(view, "continue"), "continue", HomeLayouts::toggleStyle)
        val stored = HomeLayouts.withoutContinue(moved, rows)
        assertEquals(listOf("latest", "resume", "next-up", "released"), ids(stored))
        assertEquals(listOf(CardStyle.Poster, CardStyle.Poster), stored.filter { it.type.continues }.map { it.style })
        assertEquals(14, stored.single { it.type == HomeRowType.NextUp }.windowDays)
        // Removed: both rows go; added back: both come back.
        val removed = HomeLayouts.withoutContinue(HomeLayouts.remove(view, "continue"), rows)
        assertEquals(listOf("latest", "released"), ids(removed))
        val view2 = HomeLayouts.withContinue(removed)
        val added = HomeLayouts.withoutContinue(HomeLayouts.add(view2, HomeRowType.Continue), removed)
        assertEquals(listOf("latest", "released", "resume", "next-up"), ids(added))
    }
}
