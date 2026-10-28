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
        assertEquals(LibraryIcon.HomeVideos, icon("Caméscope 2009", LibraryKind.HomeVideos))
        // A collection named after its saga stays a collection.
        assertEquals(LibraryIcon.Collections, icon("Le Phare : la saga", LibraryKind.Collections))
    }

    @Test
    fun `films and series name the kind of a library of folders`() {
        assertEquals(LibraryIcon.Movies, icon("Films", LibraryKind.Mixed))
        assertEquals(LibraryIcon.Shows, icon("Séries", LibraryKind.Mixed))
        assertEquals(LibraryIcon.Shows, icon("TV shows", LibraryKind.Mixed))
    }

    @Test
    fun `cartoons with an accent, Japanese animation without`() {
        // « animés »: the adjective (cartoons); « Animes »: the Japanese word, as written in French.
        assertEquals(LibraryIcon.Animation, icon("Films animés"))
        assertEquals(LibraryIcon.Animation, icon("Séries animées", LibraryKind.Shows))
        assertEquals(LibraryIcon.Anime, icon("Animes", LibraryKind.Shows))
    }

    @Test
    fun `genres and uses before films and series`() {
        assertEquals(LibraryIcon.Uhd, icon("Films 4K"))
        assertEquals(LibraryIcon.Uhd, icon("UHD"))
        assertEquals(LibraryIcon.Classics, icon("Classiques"))
        assertEquals(LibraryIcon.Classics, icon("Films noir et blanc"))
        assertEquals(LibraryIcon.Comedy, icon("Humour"))
        assertEquals(LibraryIcon.Comedy, icon("Comédies"))
        assertEquals(LibraryIcon.Stage, icon("Concerts", LibraryKind.Mixed))
        assertEquals(LibraryIcon.MusicVideos, icon("Clips musicaux", LibraryKind.Mixed))
        assertEquals(LibraryIcon.HomeVideos, icon("Vidéos de famille", LibraryKind.Mixed))
        assertEquals(LibraryIcon.Kids, icon("Films en famille"))
        assertEquals(LibraryIcon.Horror, icon("Horreur"))
        assertEquals(LibraryIcon.Horror, icon("Films d'épouvante"))
        assertEquals(LibraryIcon.SciFi, icon("Science-fiction"))
        assertEquals(LibraryIcon.Action, icon("Action et aventure"))
        assertEquals(LibraryIcon.Romance, icon("Romance"))
        assertEquals(LibraryIcon.Holidays, icon("Films de Noël"))
        assertEquals(LibraryIcon.Learning, icon("Tutos", LibraryKind.Mixed))
        assertEquals(LibraryIcon.Learning, icon("Conférences TED", LibraryKind.Mixed))
        assertEquals(LibraryIcon.Cooking, icon("Cuisine", LibraryKind.Shows))
        assertEquals(LibraryIcon.Nature, icon("Animaux"))
        assertEquals(LibraryIcon.World, icon("Cinéma du monde"))
        assertEquals(LibraryIcon.World, icon("VOSTFR", LibraryKind.Shows))
        assertEquals(LibraryIcon.TvPrograms, icon("Émissions", LibraryKind.Shows))
        assertEquals(LibraryIcon.Favorites, icon("Coups de cœur"))
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
        assertEquals(LibraryIcon.Comedy, icon("Stand-up comedy"))
        assertEquals(LibraryIcon.Kids, icon("Kids"))
        assertEquals(LibraryIcon.HomeVideos, icon("Home videos", LibraryKind.Mixed))
        assertEquals(LibraryIcon.Classics, icon("Classic movies"))
        assertEquals(LibraryIcon.Horror, icon("Horror"))
        assertEquals(LibraryIcon.MusicVideos, icon("Music videos", LibraryKind.Mixed))
    }

    @Test
    fun `a short keyword matches a whole word only`() {
        // « mma » is not in « Commando », nor « web » in « Webster ».
        assertEquals(LibraryIcon.Movies, icon("Commando"))
        assertEquals(LibraryIcon.Movies, icon("Webster"))
    }
}
