package fr.guim.cinedoury.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryIconTest {
    private fun icon(
        name: String,
        kind: LibraryKind = LibraryKind.Movies,
    ) = LibraryIcon.defaultFor(kind, name)

    @Test
    fun `the kind decides without a keyword`() {
        assertEquals(LibraryIcon.Movies, icon("Films"))
        assertEquals(LibraryIcon.Shows, icon("Séries TV", LibraryKind.Shows))
        assertEquals(LibraryIcon.Collections, icon("Collections", LibraryKind.Collections))
        assertEquals(LibraryIcon.Folder, icon("Divers", LibraryKind.Mixed))
    }

    @Test
    fun `french keywords, whatever the accents and the case`() {
        assertEquals(LibraryIcon.Anime, icon("Animes JPN", LibraryKind.Shows))
        assertEquals(LibraryIcon.Animation, icon("Dessins animés"))
        assertEquals(LibraryIcon.Animation, icon("Dessins animés japonais"))
        assertEquals(LibraryIcon.Documentaries, icon("Documentaires"))
        assertEquals(LibraryIcon.Documentaries, icon("Reportages", LibraryKind.Shows))
        assertEquals(LibraryIcon.Stage, icon("Spectacles"))
        assertEquals(LibraryIcon.Sport, icon("MMA", LibraryKind.Shows))
        assertEquals(LibraryIcon.Sport, icon("Sport"))
        assertEquals(LibraryIcon.OnlineVideo, icon("YouTube", LibraryKind.HomeVideos))
        assertEquals(LibraryIcon.Kids, icon("Films pour enfants"))
    }

    @Test
    fun `english keywords`() {
        assertEquals(LibraryIcon.Anime, icon("Anime", LibraryKind.Shows))
        assertEquals(LibraryIcon.Animation, icon("Animation"))
        assertEquals(LibraryIcon.Documentaries, icon("Documentaries"))
        assertEquals(LibraryIcon.Stage, icon("Stand-up comedy"))
        assertEquals(LibraryIcon.Kids, icon("Kids"))
    }

    @Test
    fun `a short keyword matches a whole word only`() {
        // « mma » is not in « Commando », nor « web » in « Webster ».
        assertEquals(LibraryIcon.Movies, icon("Commando"))
        assertEquals(LibraryIcon.Movies, icon("Webster"))
    }
}
