package fr.guim.cinedoury.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManageTest {
    private val film = MediaItem(id = ItemId("film"), kind = ItemKind.Movie, title = "Le Phare", canDelete = true)
    private val films = LibraryId("films")
    private val series = LibraryId("series")

    @Test
    fun `a profile that is not an administrator manages nothing`() {
        val child = ManageRights(administrator = false, deleteEverywhere = true)
        assertFalse(child.canManage)
        assertFalse(child.canDeleteSomewhere)
        assertFalse(child.canDelete(film))
        assertFalse(ManageRights.NONE.canManage)
    }

    @Test
    fun `an administrator manages, and deletes only with the right to delete`() {
        val admin = ManageRights(administrator = true)
        assertTrue(admin.canManage)
        assertFalse(admin.canDeleteSomewhere)
        assertFalse(admin.canDelete(film))
        assertTrue(ManageRights(administrator = true, deleteEverywhere = true).canDelete(film))
    }

    @Test
    fun `the right to delete in some libraries`() {
        val rights = ManageRights(administrator = true, deleteFrom = setOf(films))
        assertTrue(rights.canDeleteSomewhere)
        assertTrue(rights.canDelete(film, films))
        assertFalse(rights.canDelete(film, series))
        // Library unknown (a details screen): the server's own answer decides.
        assertTrue(rights.canDelete(film))
    }

    @Test
    fun `an item the server would not delete stays`() {
        val rights = ManageRights(administrator = true, deleteEverywhere = true)
        assertFalse(rights.canDelete(film.copy(canDelete = false)))
    }

    @Test
    fun `a whole series asks twice`() {
        val show = DeletionPreview(film.copy(kind = ItemKind.Series), emptyList(), null, listOf(ItemId("e1")))
        assertTrue(show.needsSecondConfirmation)
        assertEquals(1, show.episodeCount)
        assertFalse(DeletionPreview(film, listOf("/media/films/Le Phare.mkv"), 1L).needsSecondConfirmation)
    }
}
