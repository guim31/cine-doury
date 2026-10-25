package fr.guim.cinedoury.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchTitleTest {
    private fun clean(
        name: String,
        known: Int? = null,
    ) = SearchTitle.of(name, known, currentYear = 2026)

    @Test
    fun `a folder name with its year in parentheses`() {
        assertEquals(SearchTitle("Le Phare", 2001), clean("Le Phare (2001)"))
    }

    @Test
    fun `a misspelt title stays as typed, for the user to correct`() {
        assertEquals(SearchTitle("Rox ete Rouky", null), clean("Rox ete Rouky"))
        assertEquals(SearchTitle("Rox ete Rouky", 1981), clean("Rox ete Rouky", known = 1981))
    }

    @Test
    fun `dots, release tags and the group go`() {
        assertEquals(SearchTitle("Le Phrae", 2001), clean("Le Phrae.2001.1080p"))
        assertEquals(SearchTitle("The Matrix", 1999), clean("The.Matrix.1999.1080p.BluRay.x264-GROUP"))
        assertEquals(SearchTitle("Nuit polaire", 2020), clean("Nuit_polaire_2020_MULTi_2160p_HDR"))
        assertEquals(SearchTitle("Givre", null), clean("Givre.1080p.WEB-DL"))
    }

    @Test
    fun `what is in brackets goes`() {
        assertEquals(SearchTitle("Le Phare", 2001), clean("Le Phare (2001) [tmdbid-12345]"))
    }

    @Test
    fun `a number in the title is not taken for the year`() {
        assertEquals(SearchTitle("1917", 2019), clean("1917 (2019)"))
        assertEquals(SearchTitle("2001 A Space Odyssey", 1968), clean("2001 A Space Odyssey (1968)"))
        assertEquals(SearchTitle("Blade Runner 2049", 2017), clean("Blade.Runner.2049.2017.1080p"))
        assertEquals(SearchTitle("Blade Runner 2049", null), clean("Blade Runner 2049"))
        assertEquals(SearchTitle("Les Étoiles filantes 2", 1984), clean("Les Étoiles filantes 2 (1984)"))
    }
}
